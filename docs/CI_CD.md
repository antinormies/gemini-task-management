# CI/CD and deployment plan

Status: proposed. The workflows and the server setup described here are not in
place yet. This document records the intended pipeline so it can be reviewed
before anything is deployed.

## Goals

- **Pull request to `development`:** lint the backend.
- **Push to `development`:** prove the app still builds, then throw the build
  output away. This is the early warning that `master` will build.
- **Push to `master`:** build the same artifact and deploy it to the VPS.
- **Public URL:** `https://task.dream-team.works` for the web UI.
- **Android:** keep using Tailscale for now. Public API access for the phone is
  a separate decision (see Open decisions).

## What "build" means here

The backend is FastAPI/Python 3.14 with a server-rendered Jinja UI and SQLite.
There is no compiled binary and no Node build. A release is:

1. `python -m build --wheel`
2. install the wheel into a clean virtualenv
3. run Alembic migrations
4. boot the app and request `GET /api/health`

The development build job runs exactly those steps against a temporary
database. GitHub-hosted runners are ephemeral and the workflow does **not**
upload an artifact, so the wheel disappears when the job ends.

## Workflows

| Workflow | Trigger | Jobs |
| --- | --- | --- |
| `.github/workflows/ci.yml` | `pull_request` → `development` | `ruff format --check`, `ruff check`, `pytest -q` |
| `.github/workflows/build.yml` | `push` → `development` | build wheel, clean-venv install, Alembic upgrade on a temp DB, boot + `/api/health`, `pytest -q` |
| `.github/workflows/deploy.yml` | `push` → `master`, `workflow_dispatch` | `pytest -q`, build wheel, stage release, SSH deploy, migrate, restart, health check |

Common settings:

- `actions/checkout@v4`, `actions/setup-python@v6` with Python `3.14`
- pip cache keyed on `backend/pyproject.toml`
- `defaults.run.working-directory: backend`
- `permissions: contents: read`
- concurrency groups so superseded builds are cancelled, except deploys
  (`cancel-in-progress: false`)

## Server layout

The VPS already runs nginx, a remote-managed Cloudflare Tunnel, and a `deploy`
user whose sudo rights are limited to per-project helper scripts. The task
backend follows the same pattern.

| Path | Purpose |
| --- | --- |
| `/opt/gtm/releases/<sha>` | one immutable release per deploy |
| `/opt/gtm/current` | symlink to the active release |
| `/var/lib/gtm` | SQLite database, backups, logs, workspace |
| `/etc/gtm.env` | runtime configuration (mode `0640`, `root:gtm`) |
| `/usr/local/sbin/gtm-deploy` | root helper that installs and restarts a release |
| `/etc/sudoers.d/deploy-gtm` | allows `deploy` to run only `gtm-deploy` |
| `gtm-backend.service` | systemd unit, loopback `127.0.0.1:8765` |

nginx gets a `task.dream-team.works` server block on `127.0.0.1:8082` that
proxies to `127.0.0.1:8765`. The proxy read timeout is 130 seconds so AI
requests are not cut off. The Cloudflare Tunnel publishes
`task.dream-team.works` → `http://127.0.0.1:8082`; TLS ends at Cloudflare.

## Deploy helper

`gtm-deploy` is invoked as `sudo -n /usr/local/sbin/gtm-deploy` and takes no
arguments. It picks up the newest `/tmp/gtm-release-*.tar.gz`, validates the
release id, and then:

1. backs up the SQLite database into `/var/lib/gtm/backups`
2. extracts the release into `/opt/gtm/releases/<sha>`
3. creates a virtualenv and installs the wheel
4. loads `/etc/gtm.env` and runs `alembic upgrade head` as `gtm`
5. flips `/opt/gtm/current`
6. restarts `gtm-backend.service`
7. polls `/api/health`
8. **on failure:** restores the previous `current` symlink, restarts, and
   exits non-zero
9. keeps the three newest releases and deletes the uploaded tarball

## Required GitHub secrets

| Secret | Value |
| --- | --- |
| `DEPLOY_SSH_KEY` | private key of a dedicated `github-actions-gtm` key |
| `DEPLOY_HOST` | VPS address (kept in secrets, not in the repo) |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_PORT` | `22` |

No secret value belongs in `.env`, the repository, workflow logs, or the
deployment document itself.

## One-time server setup

- [ ] create the `gtm` system user and `/opt/gtm`, `/var/lib/gtm`
- [ ] write `/etc/gtm.env` with a generated `GTM_SECRET_KEY`,
      `GTM_DATA_DIR=/var/lib/gtm`, `GTM_HOST=127.0.0.1`, `GTM_PORT=8765`,
      `GTM_PUBLIC_ORIGIN=https://task.dream-team.works`,
      `GTM_TRUST_PROXY_HEADERS=true`, and the AI settings
- [ ] install and enable `gtm-backend.service`
- [ ] install `gtm-deploy` and `/etc/sudoers.d/deploy-gtm`
- [ ] add the `github-actions-gtm` public key to
      `/home/deploy/.ssh/authorized_keys`
- [ ] add the nginx server block and reload nginx
- [ ] add the Cloudflare public hostname and Access application
- [ ] create the four GitHub secrets above

## Rollout order

1. Merge this plan and the existing branch work.
2. Add the workflows in a follow-up pull request to `development`.
3. Run the one-time server setup and the first manual deploy.
4. Merge `development` → `master`; the first automatic deploy should be a
   no-op apart from installing the same commit.
5. Verify `https://task.dream-team.works`, `/api/health`, pairing, and a task
   create/complete round trip.
6. Add Android CI and public API access as follow-ups.

## Security notes

- The web UI has no login. It assumes the Tailscale perimeter today. Publishing
  it publicly without Cloudflare Access would expose every task to anyone.
  Recommended: protect the whole hostname with Access and keep the phone on
  Tailscale.
- The Android app can use Cloudflare Access only if it sends a service-token
  header, which is a code change. A separate `api-task.dream-team.works`
  hostname with a service-token policy is the cleanest later option.
- The database file is the only state. Back it up before every migration; the
  deploy helper does this automatically.
- `backend/.env` stays local and ignored. Production configuration lives only
  in `/etc/gtm.env` on the server.
- The AI base URL is not deployed on this server. Either point
  `GTM_AI_BASE_URL` at the reachable gateway and supply `GTM_AI_API_KEY`, or
  start with `GTM_AI_ENABLED=false`.

## Open decisions

- Should Android lint and unit tests run on pull requests too, not just backend
  lint?
- Cloudflare Access only for the web UI, or also a service token for Android?
- Is a reachable MemeChat Gateway URL available for `GTM_AI_BASE_URL` now, or
  should AI start disabled in production?
- Who adds the Cloudflare public hostname and Access application — dashboard
  or a scoped API token?
