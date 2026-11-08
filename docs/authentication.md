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
| `GET /api/stats`, `GET /api/stats/guests/{guestId}`, `GET /api/guests/{id}/avatar` | yes, **also API key (service)** | yes                     | yes                      | yes                      |
| Other `GET /api/stats/**`                                                          | yes                             | yes                     | yes                      | yes                      |
| `/`, `/metrics`, `/openapi.json`                                                   | no (unchanged)                  |                         |                          |                          |

### Planned permissions (reservation and guest API)

Not implemented yet, see `.claude/plans/reservations-rest-api.md`. The plan wins until a row moves into the matrix above
(the guest rows for list, create and edit have moved; avatar upload and the reservation rows are still planned).
Past, current and future reservations are treated the same. `ADMIN` and `FAMILY_USER` may change any reservation (there is
no owner concept). A guest without a role has no access.

**Reservations**

| Operation                                              | ADMIN | FAMILY_USER | GUEST_USER | Service (API key) |
|:-------------------------------------------------------|:------|:------------|:-----------|:------------------|
| Read (`GET /api/reservations`, `GET /api/reservations/{id}`) | yes   | yes         | yes        | yes               |
| Create                                                 | yes   | yes         | no         | no                |
| Update (incl. guests and their stays)                  | yes   | yes         | no         | no                |
| Delete                                                 | yes   | yes         | no         | no                |

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
- `POST /api/guests` answers `201` with the created guest. `POST /api/reservations` (planned) answers `201` with a `Location` header; update `200`; delete `204`; unknown id `404`.
- A stale `version` returns `412` (reservations, and guests on `PUT /api/guests/{guestId}` and `PUT /api/me`); a missing or non-numeric `version`
  returns `400`. An overlap with another reservation and a shared guest email return `409`.
- `DELETE /api/reservations/{id}?version=<n>` carries the version in the query.
- Avatar upload: `Content-Type: image/jpeg` required (`415` otherwise), at most the size limit (`413`), malformed JPEG `400`; metadata (EXIF) is stripped.
- Errors on the new routes (`GET`/`POST /api/guests`) are JSON `{code, message}` with stable codes (the status codes above are unchanged); the existing guest update routes (`PUT /api/guests/{guestId}`, `PUT /api/me`) keep plain-text bodies.
- A JSON write body above 64 KB returns `413`; a non-JSON content type on a write route returns `415` (also on `PUT /api/guests/{guestId}` and `PUT /api/me`, which answered `400` before); a `version` outside the `Short` range, an invalid `from`/`to`, or `to <= from` returns `400`.
- When the database stays locked after the retries (`SQLITE_BUSY`) or the Calendar outbox is full, writes return `503`.
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
Until the bootstrap admin exists (planned), the first admin on an empty database is added with SQL on `data/app.db`
(take a backup first).

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
