import json, re
d = json.load(open('openapi/cabin-visits.json'))
S = d['components']['schemas']

def ex(s):
    if '$ref' in s: return ex(S[s['$ref'].split('/')[-1]])
    if 'example' in s:
        e = s['example']
        if isinstance(e, str) and s.get('type') != 'string':
            try: return json.loads(e)
            except ValueError: return e
        return e
    t = s.get('type')
    if t == 'object' or 'properties' in s:
        return {k: ex(v) for k, v in s.get('properties', {}).items()}
    if t == 'array': return [ex(s['items'])]
    return {'string': '', 'integer': 0, 'number': 0, 'boolean': False}.get(t)

def var(n): return '{{%s}}' % n
items = {}
for path, ops in d['paths'].items():
    for m, o in ops.items():
        tag = o['tags'][0] if o.get('tags') else 'Other'
        params = o.get('parameters', [])
        urlpath = re.sub(r'\{(\w+)\}', r':\1', path)
        q = [p for p in params if p['in'] == 'query']
        pv = [p for p in params if p['in'] == 'path']
        url = {
            'raw': var('baseUrl') + urlpath + ('?' + '&'.join(f"{p['name']}={p.get('example','')}" for p in q) if q else ''),
            'host': [var('baseUrl')],
            'path': urlpath.strip('/').split('/'),
            'query': [{'key': p['name'], 'value': str(p.get('example', '')), 'description': p.get('description', ''), 'disabled': not p.get('required', False)} for p in q],
            'variable': [{'key': p['name'], 'value': str(p.get('example', '')), 'description': p.get('description', '')} for p in pv],
        }
        req = {'method': m.upper(), 'header': [], 'url': url, 'description': o.get('description', '')}
        rb = o.get('requestBody')
        if rb:
            ct, c = next(iter(rb['content'].items()))
            req['header'].append({'key': 'Content-Type', 'value': ct})
            if ct == 'application/json':
                req['body'] = {'mode': 'raw', 'raw': json.dumps(ex(c['schema']), indent=2, ensure_ascii=False), 'options': {'raw': {'language': 'json'}}}
            else:
                req['body'] = {'mode': 'file', 'file': {}}
        items.setdefault(tag, []).append({'name': f"{m.upper()} {path} – {o.get('summary','')}", 'request': req})

DOCS = """# Cabin Visits API

REST API for cabin visit statistics. Generated from `openapi/cabin-visits.json` by `postman/cabin-visits/generate.py`.

## Setup

1. Import this collection and the environments `local.postman_environment.json` and `rpi.postman_environment.json`.
2. Select the environment (top right): **Local** or **RPi**. Edit `baseUrl` if needed (RPi: the host or Cloudflare hostname).
3. Paste a token into the **`authToken`** variable (Environments -> your environment -> `authToken`, **Current value**). It is a secret and stays local to your Postman.

Every request sends the header named by `authHeader` with the value of `authToken` (collection-level auth).

## Getting a valid JWT (Cloudflare Access)

The app only accepts a JWT signed by Cloudflare Access (checked: signature, issuer, audience, expiry), and the `email` claim must match a guest with a role. Use `authHeader` = `Cf-Access-Jwt-Assertion` (default).

**Option A - cloudflared (recommended)**

    cloudflared access login https://<your-app-hostname>
    cloudflared access token -app=https://<your-app-hostname>

Paste the printed token into `authToken`.

**Option B - browser**

1. Open `https://<your-app-hostname>` and log in through Cloudflare Access.
2. DevTools -> Application -> Cookies -> copy the value of the cookie `CF_Authorization` (it is the same JWT).
3. Paste it into `authToken`.

The token expires (Cloudflare session duration, often 24 h). On `401`, get a new one.

**Local app:** it needs `CLOUDFLARE_ACCESS_TEAM_DOMAIN` and `CLOUDFLARE_ACCESS_AUD` set to the same values as production. A token from the production Access application is then valid locally too. Without them, protected endpoints answer `503`.

## Service API key (MagicMirror style)

Set `authHeader` = `X-Api-Key` and `authToken` = the plain key (the app holds its SHA-256 in `API_KEYS`). Only routes that allow services accept it; others answer `403`. See `docs/authentication.md`.

## Notes

- `PUT` and `DELETE` need the current `version` from a previous `GET`.
- The avatar `PUT` routes take a raw `image/jpeg` (Body -> binary).
"""

coll = {
    'info': {'name': 'Cabin Visits API', 'description': DOCS,
             'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
    'auth': {'type': 'apikey', 'apikey': [
        {'key': 'key', 'value': var('authHeader'), 'type': 'string'},
        {'key': 'value', 'value': var('authToken'), 'type': 'string'},
        {'key': 'in', 'value': 'header', 'type': 'string'}]},
    'variable': [{'key': 'baseUrl', 'value': 'http://localhost:8079'}, {'key': 'authHeader', 'value': 'Cf-Access-Jwt-Assertion'}, {'key': 'authToken', 'value': '', 'description': 'JWT (Cf-Access-Jwt-Assertion) or API key (X-Api-Key). See the collection description.'}],
    'item': [{'name': t, 'item': i} for t, i in items.items()],
}
json.dump(coll, open('postman/cabin-visits/cabin-visits.postman_collection.json', 'w'), indent=2, ensure_ascii=False)

def env(name, base):
    return {'name': name, '_postman_variable_scope': 'environment', 'values': [
        {'key': 'baseUrl', 'value': base, 'enabled': True, 'type': 'default'},
        {'key': 'authHeader', 'value': 'Cf-Access-Jwt-Assertion', 'enabled': True, 'type': 'default'},
        {'key': 'authToken', 'value': '', 'enabled': True, 'type': 'secret', 'description': 'Cloudflare Access JWT, or API key if authHeader is X-Api-Key'}]}
json.dump(env('Cabin Visits – Local', 'http://localhost:8079'), open('postman/cabin-visits/local.postman_environment.json', 'w'), indent=2)
json.dump(env('Cabin Visits – RPi', 'http://CHANGE-ME-RPI-HOST:8079'), open('postman/cabin-visits/rpi.postman_environment.json', 'w'), indent=2)
