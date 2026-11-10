# Authentication and authorization

Authentication and authorization are separate:

| Concern            | Who                       | How                                                                                                                                                               |
|:-------------------|:--------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Authentication** | Cloudflare Access         | The JWT in the HTTP header `Cf-Access-Jwt-Assertion` is verified by the app with the Ktor JWT provider (java-jwt, jwks-rsa): signature, issuer, audience, expiry. |
| **Identity**       | SQLite (`guest`)          | The `email` claim is matched (case-insensitive) with `guest.email`. No new person records.                                                                        |
| **Authorization**  | SQLite (`guest.role`) | `ADMIN`, `FAMILY_USER` or `GUEST_USER`. A guest without a role has no access (external guests).                                                                   |

There are two kinds of callers, with different principals:

| Principal          | Who                                   | Authenticated with                           | May call                              |
|:-------------------|:--------------------------------------|:---------------------------------------------|:--------------------------------------|
| `UserPrincipal`    | A person, through Cloudflare Access   | JWT in HTTP header `Cf-Access-Jwt-Assertion` | What the role allows (matrix below)   |
| `ServicePrincipal` | A machine on the network, MagicMirror | API key in HTTP header `X-Api-Key`           | Only the routes marked `allowService` |

A service has no role. It is denied (`403`) on every route that does not explicitly allow services.

The app never trusts that a request passed through Cloudflare. A request straight to port 8079 on the local network
has no valid token, so it is rejected on every protected endpoint. The keys come from
`https://<team-domain>/cdn-cgi/access/certs` (cached, refresh rate-limited by jwks-rsa: 10 fetches per minute, shared by
all tokens with an unknown key id). The app
is meant to be reachable only through Cloudflare, otherwise tokens with random key ids can use up the refresh budget.

## Configuration

| Variable                        | Description                                                       |
|:--------------------------------|:------------------------------------------------------------------|
| `CLOUDFLARE_ACCESS_TEAM_DOMAIN` | e.g. `myteam.cloudflareaccess.com` (issuer is `https://<domain>`) |
| `CLOUDFLARE_ACCESS_AUD`         | `AUD` tag of the Access application                               |

| Variable   | Description                                                                                             |
|:-----------|:--------------------------------------------------------------------------------------------------------|
| `API_KEYS` | Comma separated `name:sha256` entries, the hex SHA-256 of each service's key. Blank means no API access |

The Cloudflare variables are optional. If one is missing, protected endpoints respond `503` (never open), and all other
endpoints work as
before.

## Status codes on protected endpoints

| Status | When                                                                                                                             |
|:-------|:---------------------------------------------------------------------------------------------------------------------------------|
| `401`  | Token missing, malformed, expired, not yet valid, wrong signature/issuer/audience/algorithm, or no `email` claim (service token) |
| `401`  | (also) The signing keys cannot be fetched from Cloudflare: fails closed, logged as error                                         |
| `401`  | API key missing or wrong                                                                                                         |
| `403`  | Valid API key, but the route does not allow services                                                                             |
| `403`  | Valid token, but the email has no guest with a role (or several do: ambiguous), or the role is not allowed                       |
| `503`  | Cloudflare Access is not configured, and the request has no valid API key                                                        |

## Authorization matrix

| Endpoint                                                                           | Auth                            | ADMIN                   | FAMILY_USER              | GUEST_USER               |
|:-----------------------------------------------------------------------------------|:--------------------------------|:------------------------|:-------------------------|:-------------------------|
| `GET /api/me`                                                                      | yes                             | yes                     | yes                      | yes                      |
| `PUT /api/me` (own guest)                                                          | yes                             | name, birth year, gender | name, birth year, gender | name, birth year, gender |
| `GET /api/guests` (list)                                                           | yes                             | yes                     | yes                      | no                       |
| `POST /api/guests` (create)                                                        | yes                             | yes                     | no                       | no                       |
| `PUT /api/guests/{guestId}` (any guest)                                            | yes                             | all properties          | no                       | no                       |
| `PUT /api/guests/{guestId}/avatar` (any guest)                                     | yes                             | yes                     | no                       | no                       |
| `PUT /api/me/avatar` (own avatar)                                                  | yes                             | yes                     | yes                      | yes                      |
| `GET /api/stats`, `GET /api/stats/guests/{guestId}`, `GET /api/guests/{id}/avatar` | yes, **also API key (service)** | yes                     | yes                      | yes                      |
| `GET /api/reservations`, `GET /api/reservations/{id}`                              | yes, **also API key (service)** | all                     | all                      | only those they are a guest of |
| `POST /api/reservations`, `PUT /api/reservations/{id}`, `DELETE /api/reservations/{id}` | yes                        | yes                     | yes                      | no                       |
| Other `GET /api/stats/**`                                                          | yes                             | yes                     | yes                      | yes                      |
| `/`, `/metrics`, `/openapi.json`                                                   | no (unchanged)                  |                         |                          |                          |

### Planned permissions (reservation and guest API)

Overview; the implemented routes are in the matrix above, see `.claude/plans/reservations-rest-api.md` for what is still planned. The plan wins until a row moves into the matrix above
(the guest rows, including the avatar upload, and the reservation read and write rows have moved; the table below stays as the overview with the guest-stay plans).
Past, current and future reservations are treated the same. `ADMIN` and `FAMILY_USER` may change any reservation (there is
no owner concept). A guest without a role has no access.

**Reservations**

| Operation                                              | ADMIN | FAMILY_USER | GUEST_USER | Service (API key) |
|:-------------------------------------------------------|:------|:------------|:-----------|:------------------|
| Read (`GET /api/reservations`, `GET /api/reservations/{id}`) | all   | all         | only those where the user is a guest | all               |
| Create                                                 | yes   | yes         | no         | no                |
| Update (incl. guests and their stays)                  | yes   | yes         | no         | no                |
| Delete                                                 | yes   | yes         | no         | no                |

**Who can read which reservation.** `ADMIN`, `FAMILY_USER` and the service (API key, the MagicMirror) read every reservation. A `GUEST_USER` reads only the reservations that have them as a guest, **for the whole stay or only part of it** (partial stays, section 9 of the plan: any `reservation_guest` link counts, whatever its `fromDate`/`toDate`). The guest of a `GUEST_USER` is the one found by the email of their Cloudflare Access login (`UserPrincipal.id`).
- `GET /api/reservations` leaves the other reservations out for a `GUEST_USER`; the optional `from`/`to` window applies on top of that.
- `GET /api/reservations/{id}` answers `404` for a `GUEST_USER` when the reservation does not exist **or** they are not a guest of it (the same answer, so ids cannot be probed). A `GUEST_USER` gets the whole reservation, including the other guests (`guestId`, name, `avatarUrl`), `version` and the audit fields: the response is the same for every role.
- The filter is applied in the repository query (by guest id), not after loading every reservation. (Implemented in PR 4a; `GET /api/reservations/{id}` loads the one reservation and checks its guests.)

**Guests**

| Operation                                                              | ADMIN | FAMILY_USER | GUEST_USER | Service (API key) |
|:-----------------------------------------------------------------------|:------|:------------|:-----------|:------------------|
| List guests (`GET /api/guests`)                                        | yes   | yes         | no         | no                |
| Create a guest                                                         | yes   | no          | no         | no                |
| Edit any guest (`PUT /api/guests/{guestId}`)                           | yes   | no          | no         | no                |
| Edit own profile (`PUT /api/me`: name, birth year, gender)             | yes   | yes         | yes        | no                |
| Upload or replace avatar of any guest                                  | yes   | no          | no         | no                |
| Upload or replace own avatar                                           | yes   | yes         | yes        | no                |
| Read avatar (`GET /api/guests/{id}/avatar`)                            | yes   | yes         | yes        | yes               |
| Delete a guest                                                         | not supported | not supported | not supported | not supported |

Only `ADMIN` may change `email`, `isFamily` and `role`.

**Status codes and versioning** (guest routes are implemented, the rest is planned, details in the plan):
- `POST /api/guests` answers `201` with the created guest. `POST /api/reservations` answers `201` with the created reservation and a `Location` header; `PUT` answers `200` with the reservation (unchanged data keeps the version); `DELETE` answers `204`; an unknown id is `404`.
- A stale `version` returns `412` (reservations, and guests on `PUT /api/guests/{guestId}` and `PUT /api/me`); a missing or non-numeric `version`
  returns `400`. An overlap with another reservation and a shared guest email return `409`.
- `DELETE /api/reservations/{id}?version=<n>` carries the version in the query.
- Reservation writes (`ADMIN` and `FAMILY_USER`; `ReservationService`): `400` with a code for a broken rule (`END_BEFORE_START`, `TOO_LONG` over 60 nights by UTC date, `TOO_FAR_AHEAD` more than 2 years ahead, `NO_GUESTS`, `TOO_MANY_GUESTS` over 50, `DUPLICATE_GUEST`, `UNKNOWN_GUEST`; in the past is allowed; on `PUT` the range rules apply only when the times change), `409` `OVERLAP` for a reservation that overlaps another (a handover on the same instant is allowed; a retried create is `409`). On `PUT` the checks run in the order: reservation exists (`404`), version (`412`), rules (`400`), overlap (`409`). A change of the times clears check-in, check-out and trips of that reservation and derives them again after the commit.
- Avatar upload (`PUT /api/guests/{guestId}/avatar`, `PUT /api/me/avatar`, implemented): the body is the raw JPEG. `Content-Type: image/jpeg` is required (`415` otherwise, parameters such as `charset` are ignored), at most 4 MB (`413`), not a valid JPEG or larger than 4000 x 4000 pixels `400` (`INVALID_IMAGE`), unknown guest `404`; the answer is `204`. The server removes all metadata without re-encoding (Exif including GPS and orientation, XMP, comments, thumbnails, appended images; `JpegSanitizer.kt`), so clients must upload upright pixels. It is last write wins: no `version`, and the same image changes nothing. Errors are JSON `{code, message}`.
- Errors on every guest route (`GET`/`POST /api/guests`, `PUT /api/guests/{guestId}`, `PUT /api/me`, the avatar uploads) are JSON `{code, message}` with stable codes (`INVALID_REQUEST`, `INVALID_GUEST`, `INVALID_IMAGE`, `NOT_FOUND`, `FORBIDDEN`, `EMAIL_IN_USE`, `LAST_ADMIN`, `VERSION_MISMATCH`, `BODY_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `TEMPORARILY_UNAVAILABLE`); the status codes are unchanged. The reservation routes use the same format with the codes above and `OVERLAP`. `GET /api/me` and the response of `PUT /api/me` carry `avatarUrl` (null without an avatar, versioned like `GuestResponse`), so the web app refetches `/api/me` after `PUT /api/me/avatar` to get the new url.
- A JSON write body above 64 KB returns `413`; a non-JSON content type on a write route returns `415` (also on `PUT /api/guests/{guestId}` and `PUT /api/me`, which answered `400` before); a `version` outside the `Short` range, an invalid `from`/`to`, or `to <= from` returns `400`.
- When the database stays locked after the 5 s busy timeout (`SQLITE_BUSY`) or 200 or more calendar operations are waiting in the outbox, reservation writes return `503` `TEMPORARILY_UNAVAILABLE` (nothing is written).
- **State-changing routes** require `application/json` or `image/jpeg`, never change state on `GET`, and have no body-less `POST` (this is what keeps cross-site requests out, because Cloudflare Access authenticates by cookie). No CORS plugin is installed, so a cross-site request needs a preflight that is never allowed. `UpdateGuestRoutesTest` checks `415` for `text/plain`, `multipart/form-data` and no body, and that a preflight gets no `Access-Control-Allow-*` header.

`GET /api/guests` returns the same fields (incl. email, role, `version` and the audit fields) to `ADMIN` and `FAMILY_USER`. Avatars are JPEG only,
sent as raw bytes with a size limit.

New protected routes use `authorized(AppRole.X, ...) { ... }` (`auth/RoleAuthorization.kt`), which always combines
authentication and the role check. Services are denied unless the route passes `allowService = true`.

## API key for a service (MagicMirror)

The key is a random secret that only the service and you know. The app only stores its hash.

```bash
# 1. Generate the key (keep it secret, this is what MagicMirror sends)
KEY=$(openssl rand -hex 32)
echo "$KEY"

# 2. Hash it (this goes in the app configuration, not the key itself)
printf %s "$KEY" | shasum -a 256 | cut -d' ' -f1
```

Set the app's environment variable `API_KEYS=mirror:<the hash>` and restart. Several services: `a:<hash>,b:<hash>`.
The name (`mirror`) is only used to identify the service in the code. Configure MagicMirror to send the key in the
HTTP header `X-Api-Key` on every request, also the avatar requests (an `<img>` tag in a browser cannot, so MagicMirror
must
fetch avatars itself, for example in its node helper).

- **Revoke**: remove the entry and restart. **Rotate**: add the new entry, switch the client, remove the old one.
- The key is sent in clear text on a plain HTTP network. Keep it on a trusted network or put TLS in front.
- A wrong or empty key gives `401`. A valid key on a route that does not allow services gives `403`.

## Audit trail

A guest created with `POST /api/guests` records the authenticated user's `guest.id` in `guest.created_by`. Changes to guests
(`PUT /api/guests/{guestId}`, `PUT /api/me`) record it in `guest.updated_by`, together with `updated_time` and a new `version`. Tasks without a user (the vehicle trip import)
record `system`. See [database_schema.md](database_schema.md#audit-columns).

## Granting access

Roles are stored in SQLite (`guest.role`) and changed by an admin with `PUT /api/guests/{guestId}`. A guest without a
`role` has no access, so an admin removes access by sending `role: null`. The guest needs the email that Cloudflare Access
verifies. Values: `ADMIN`, `FAMILY_USER`, `GUEST_USER`.

There is no import file any more: guests are no longer loaded at startup. An admin creates guests with `POST /api/guests`.
The id is generated from the name (`ola.nordmann`, then `ola.nordmann-2` on a collision) and never changes. Migration `V1.8` gave the guests that existed before the same kind of id (`ismar` became `ismar.slomic`). A guest with a
role but without an email cannot log in, and a guest that has to log in also needs an entry in the Cloudflare Access policy.
**First admin (bootstrap).** On an empty database nobody can log in to create guests. At startup, if **no** guest has the `ADMIN` role (with or without an
email) and the `BOOTSTRAP_ADMIN_*` variables are set (`EMAIL`, `FIRST_NAME`, `LAST_NAME`, `BIRTH_YEAR`, `GENDER`), the app creates that guest as `ADMIN`
(same validation and id rule as `POST /api/guests`, `isFamily` false, audit actor `system`). A guest that already has the email gets the `ADMIN` role instead, and a
warning with the guest id is logged. No variables, or an existing admin, do nothing. Partly set or invalid variables fail startup with a message that names
them (also when an admin exists). It is meant for development, CI and disaster recovery; leave the variables unset in production. The admin also needs an
entry in the Cloudflare Access policy.

**Updates are full replaces (`PUT`).** `PUT /api/guests/{guestId}` takes the `version` the client read and every writable
property; `PUT /api/me` takes the `version` and the four profile properties. A missing property is `400`, `null` for `email`
or `role` clears it, and properties that are not writable (`id`, `avatarUrl`, the audit fields, ...) are ignored. See the
API design principles in the plan.

| Property                                                  | Who   |
|:----------------------------------------------------------|:------|
| `firstName`, `lastName`, `birthYear`, `gender`, `email`, `isFamily`, `role` | `ADMIN` (`PUT /api/guests/{guestId}`) |
| `firstName`, `lastName`, `birthYear`, `gender`            | the user (`PUT /api/me`; the other properties stay as they are) |

- **Version:** the response of every guest read (`GET /api/me`, `GET /api/guests`, the response of every guest write)
  carries `version`. A stale `version` returns `412`, a missing one `400`. An update that changes nothing answers `200`
  with the unchanged `version`. The check order is: role (`401`/`403`), malformed body incl. missing `version` (`400`),
  forbidden (`403`), invalid values (`400`), unknown guest (`404`), stale version (`412`), last admin (`409`), email in
  use (`409`). The version is compared inside the database transaction of the write. Avatar uploads do not use it.
- An admin cannot remove their own `ADMIN` role or change or clear their own email there. The last guest with the `ADMIN` role
  and an email cannot lose the role or the email (`409`). If it still happens (SQL, or two admins at the same moment), the way back is SQL on
  `data/app.db`: `update guest set role = 'ADMIN' where id = '<guest id>' and email is not null`.
- The email is trimmed and stored in lowercase, at most 254 characters, in the form `a@b`. A blank email means no email, on create and on
  update (it clears the email). `ola+x@example.com` is a different identity than `ola@example.com`.
- An email can only belong to one guest (`409` otherwise), also guests without a role: sharing it makes the identity
  ambiguous. This is also enforced by the unique index `uq_guest_email` in the database.
- `id` and `notionId` cannot be changed. Rules are enforced in `GuestProfileService` (edit) and `GuestAdminService` (create).
- Authorization is read from the database on every request, so a changed role takes effect on the next request. A
  `UserPrincipal` is never cached.
