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
| `POST /api/me` (own guest)                                                         | yes                             | all properties          | name, birth year, gender | name, birth year, gender |
| `POST /api/guests/{guestId}` (any guest)                                           | yes                             | all properties          | no                       | no                       |
| `GET /api/stats`, `GET /api/stats/guests/{guestId}`, `GET /api/guests/{id}/avatar` | yes, **also API key (service)** | yes                     | yes                      | yes                      |
| Other `GET /api/stats/**`                                                          | yes                             | yes                     | yes                      | yes                      |
| `/`, `/metrics`, `/openapi.json`                                                   | no (unchanged)                  |                         |                          |                          |

### Planned permissions (reservation and guest API)

Not implemented yet, see `.claude/plans/reservations-rest-api.md`. The plan wins until a row moves into the matrix above.
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
| Edit any guest (`POST /api/guests/{guestId}`)                          | yes   | no          | no         | no                |
| Edit own profile (`POST /api/me`: name, birth year, gender)            | yes   | yes         | yes        | no                |
| Upload or replace avatar of any guest                                  | yes   | no          | no         | no                |
| Upload or replace own avatar                                           | yes   | yes         | yes        | no                |
| Read avatar (`GET /api/guests/{id}/avatar`)                            | yes   | yes         | yes        | yes               |
| Delete a guest                                                         | not supported | not supported | not supported | not supported |

Only `ADMIN` may change `email`, `isFamily` and `role`.

**Planned status codes and versioning** (details in the plan):
- `POST /api/reservations` answers `201` with a `Location` header; update `200`; delete `204`; unknown id `404`.
- A stale `version` returns `412` (reservations, and guests on `POST /api/guests/{guestId}` and `POST /api/me`); a missing or non-numeric `version`
  returns `400`. An overlap with another reservation and a shared guest email return `409`.
- `DELETE /api/reservations/{id}?version=<n>` carries the version in the query.
- Avatar upload: `Content-Type: image/jpeg` required (`415` otherwise), at most the size limit (`413`), malformed JPEG `400`; metadata (EXIF) is stripped.
- Errors on the new routes are JSON `{code, message}` with stable codes (the status codes above are unchanged); the existing guest update routes keep plain-text bodies.
- A JSON write body above the limit returns `413`; a non-JSON content type on a write route returns `415`; a `version` outside the `Short` range, an invalid `from`/`to`, or `to <= from` returns `400`.
- When the database stays locked after the retries (`SQLITE_BUSY`) or the Calendar outbox is full, writes return `503`.
- **State-changing routes** require `application/json` or `image/jpeg`, never change state on `GET`, and have no body-less `POST` (this is what keeps cross-site requests out, because Cloudflare Access authenticates by cookie).

`GET /api/guests` returns the same fields (incl. email and role) to `ADMIN` and `FAMILY_USER`. Avatars are JPEG only,
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

Changes to guests (`POST /api/guests/{guestId}`, `POST /api/me`) record the authenticated user's `guest.id` in
`guest.updated_by`, together with `updated_time` and a new `version`. Tasks without a user (the vehicle trip import)
record `system`. See [database_schema.md](database_schema.md#audit-columns).

## Granting access

Roles are stored in SQLite (`guest.role`) and changed by an admin with `POST /api/guests/{guestId}`. A guest without a
`role` has no access. A role cannot be set back to null through the API, so an admin can only lower it (for example to
`GUEST_USER`); removing access completely takes SQL on `data/app.db`. The guest needs the email that Cloudflare Access
verifies. Values: `ADMIN`, `FAMILY_USER`, `GUEST_USER`.

There is no import file any more: guests are no longer loaded at startup. Until the guest API can create guests, a new
guest is added with SQL on `data/app.db` (take a backup first).

`POST /api/guests/{guestId}` takes the properties to change; properties that are left out are unchanged:

| Property                                                  | Who   |
|:----------------------------------------------------------|:------|
| `firstName`, `lastName`, `birthYear`, `gender`, `email`, `isFamily`, `role` | `ADMIN` |

An admin cannot remove their own `ADMIN` role or change their own email there. An email can only belong to one guest
(`409` otherwise), also guests without a role: sharing it makes the identity ambiguous. This is also enforced by the
unique index `uq_guest_email` in the database.
`id` and `notionId` cannot be changed. A property cannot be set back to null, so an email can be replaced but not
removed. Rules are enforced in `GuestProfileService`.
