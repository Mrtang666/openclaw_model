# OpenClaw Spider_XHS Sidecar

This service adapts an external Spider_XHS checkout to the internal OpenClaw collection contract. It is intentionally separate from the Spring Boot process and exposes only read-only search collection operations.

## Safety boundaries

- The default bind address is `127.0.0.1`.
- `X-Collector-Api-Key` authentication is enabled when `XHS_COLLECTOR_API_KEY` is configured.
- Spider_XHS runs in a child process with an environment allowlist. OpenClaw database and model credentials are not forwarded.
- Cookies are read only from `XHS_COOKIES` or `COOKIES` in the child process and are never written to job files.
- Profile URLs, nicknames and avatars are removed. Author identifiers are HMAC pseudonymized before persistence.
- A note may include a validated `access_url` containing only `xsec_token` and `xsec_source`. This content-scoped navigation URL is not an account Cookie, but it is retained in Sidecar job state long enough for Java import and must not be logged or shared.
- The adapter provides no publish, delete, like, follow or private-message endpoint.
- Use only with accounts and data collection activities you are authorized to operate. Follow platform terms, applicable law and reasonable rate limits.

## HTTP contract

```text
POST /internal/v1/jobs/search
GET  /internal/v1/jobs/{jobId}
GET  /health
```

Submission body:

```json
{"query":"brand name","limit":20,"cursor":""}
```

Terminal job response:

```json
{
  "jobId": "...",
  "status": "SUCCEEDED",
  "complete": true,
  "nextCursor": "",
  "records": [],
  "errorCode": "",
  "errorMessage": "",
  "collectedAt": "2026-07-28T08:00:00Z"
}
```

Jobs are stored as JSON under `XHS_SIDECAR_DATA_DIR`. Jobs interrupted by a sidecar restart become `FAILED` with `SIDECAR_RESTARTED`, allowing the Java coordinator to stop polling deterministically.
Terminal job files are removed after `XHS_SIDECAR_JOB_RETENTION_HOURS` (seven days by default).

The stable `note_url` has no query parameters and is used as the post identity. The separate `access_url` is restricted to HTTPS Xiaohongshu hosts, a path matching the note ID, and the two navigation parameters above. Java stores it separately and exposes it to the browser only through a validated redirect endpoint.

## Windows setup

From the project root:

```powershell
cd .\xhs-sidecar
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e .

$archive = "C:\path\to\Spider_XHS-master.zip"
$spiderRoot = .\.venv\Scripts\python.exe -m xhs_sidecar.bootstrap `
  --archive $archive `
  --target .\runtime\vendor

.\.venv\Scripts\python.exe -m pip install -r "$spiderRoot\requirements.txt"
Push-Location $spiderRoot
npm install
Pop-Location
```

Configure the current shell. Do not place a live Cookie in a committed file:

```powershell
$env:SPIDER_XHS_ROOT = $spiderRoot
$env:SPIDER_XHS_PYTHON = (Resolve-Path .\.venv\Scripts\python.exe)
$env:XHS_COOKIES = "your complete authorized Cookie"
$env:XHS_COLLECTOR_API_KEY = "a long random shared secret"
$env:XHS_AUTHOR_HASH_KEY = "a separate long stable secret"

.\.venv\Scripts\python.exe -m xhs_sidecar.server --check
.\.venv\Scripts\python.exe -m xhs_sidecar.server
```

The bootstrap command validates archive paths and extracts Spider_XHS only into the ignored `runtime/vendor` directory. Spider source is not copied into the application source tree.

## Spring Boot configuration

Use the same collector API key and author hash key in the Java process:

```powershell
$env:XHS_COLLECTOR_ENABLED = "true"
$env:XHS_COLLECTOR_BASE_URL = "http://127.0.0.1:18081"
$env:XHS_COLLECTOR_API_KEY = "a long random shared secret"
$env:XHS_AUTHOR_HASH_KEY = "a separate long stable secret"
$env:XHS_ANALYSIS_ENABLED = "true"
```

Alerts remain independently controlled by `XHS_ALERT_ENABLED`.

## Verification

Run dependency-free sidecar tests:

```powershell
py -3.13 -m unittest discover -s tests -v
```

Check the running service:

```powershell
Invoke-RestMethod http://127.0.0.1:18081/health
```

A real search requires a valid authorized Cookie, Spider_XHS Python dependencies and Node.js dependencies. Contract tests do not contact Xiaohongshu.

### Refresh an expired Cookie

When a job returns `AUTH_EXPIRED`, the configured Xiaohongshu browser session is no longer valid. Sign in to Xiaohongshu again in a browser, copy the complete Cookie from an authenticated `www.xiaohongshu.com` request, and replace only the `XHS_COOKIES` value in the ignored `xhs-sidecar/.env` file. Do not paste the Cookie into logs, source files, screenshots, or chat messages.

Recreate the Sidecar so Docker loads the updated environment value:

```powershell
docker compose --project-directory .\xhs-sidecar -f .\xhs-sidecar\compose.yaml up -d --force-recreate sidecar
Invoke-RestMethod http://127.0.0.1:18081/health
```

Submit a new collection job after the health check succeeds. Existing failed jobs retain their original `AUTH_EXPIRED` result for troubleshooting.

## Docker deployment

Docker Compose reads secrets from the ignored `xhs-sidecar/.env` file. Never copy this file into the image or commit it.

```powershell
cd .\xhs-sidecar
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
Invoke-RestMethod http://127.0.0.1:18081/health
```

The container exposes port 18081 only on host loopback, runs as a non-root user, and stores job state in the `openclaw-xhs_xhs-jobs` Docker volume. The Java configuration remains `XHS_COLLECTOR_BASE_URL=http://127.0.0.1:18081`.

Operational commands:

```powershell
docker compose logs -f --tail 100 sidecar
docker compose restart sidecar
docker compose down
```

`docker compose down` preserves the job volume. Only use `docker compose down -v` when the retained Sidecar job state should also be deleted.
