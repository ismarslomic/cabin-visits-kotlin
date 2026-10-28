# Authentication and authorization

Authentication and authorization are separate:

| Concern            | Who                       | How                                                                                                                                                               |
|:-------------------|:--------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Authentication** | Cloudflare Access         | The JWT in the HTTP header `Cf-Access-Jwt-Assertion` is verified by the app with the Ktor JWT provider (java-jwt, jwks-rsa): signature, issuer, audience, expiry. |
| **Identity**       | SQLite (`guest`)          | The `email` claim is matched (case-insensitive) with `guest.email`. No new person records.                                                                        |
| **Authorization**  | SQLite (`guest.app_role`) | `ADMIN`, `FAMILY_USER` or `GUEST_USER`. A guest without a role has no access (external guests).                                                                   |

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
| Future write endpoints                                                             | yes                             | all reservations, users | own reservations         | no                       |

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

## Granting access

Roles are managed in SQLite (`guest.app_role`), but the source is `guests.json`: the optional `appRole` property of
each guest is written to the database at startup together with the other guest properties (see `GuestService`).
A guest without `appRole` has no access, so removing the property from the file revokes access at the next startup,
**unless the role of that guest has been changed through the API**: the API is then the source (see
[Changing guest data](#changing-guest-data)) and the file no longer decides the role.

```json
{
  "id": "ismar",
  "firstName": "Ismar",
  "lastName": "Slomic",
  "birthYear": 1984,
  "email": "ismar@slomic.no",
  "gender": "MALE",
  "isFamily": true,
  "appRole": "ADMIN"
}
```

The guest needs the email that Cloudflare Access verifies. Values: `ADMIN`, `FAMILY_USER`, `GUEST_USER`.

## Changing guest data

`POST /api/me` changes the authenticated user's own guest, and `POST /api/guests/{guestId}` (admin only) any guest.
The body is JSON with the properties to change; properties that are left out are unchanged.

| Property                                       | Who                                     |
|:-----------------------------------------------|:----------------------------------------|
| `firstName`, `lastName`, `birthYear`, `gender` | every role, own guest                   |
| `email`, `isFamily`, `appRole`                 | `ADMIN`, own guest and all other guests |

An admin cannot remove their own `ADMIN` role or change their own email (the email identifies the user). An email can
only be used by one guest with a role (`409` otherwise): sharing it makes the identity ambiguous and denies both. `id`
and `notionId` cannot be changed. A property cannot be set back to
null, so an email can be replaced but not removed. Rules are enforced in `GuestProfileService`.

Properties changed through the API are remembered per guest (`guest.api_edited_fields`) and are **not overwritten** by
the `guests.json` sync at startup. The sync still updates the properties that were never changed through the API, and
adds new guests. To make the file win again for a property, clear the property name from `api_edited_fields`.
