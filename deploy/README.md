# Backend CI/CD

Workflow: .github/workflows/backend.yml

## Triggers

| Event | Job | What it does |
| --- | --- | --- |
| Pull request -> development | lint-test | ruff format --check, ruff check, pytest |
| Push -> development | build | lint, tests, wheel build, then removes build artifacts and caches |
| Push -> master | build -> deploy | lint/tests/build, then rsync + remote install/restart |

The development job deliberately does **not** use actions/cache; it installs with
--no-cache-dir and removes dist, build, __pycache__, .pytest_cache,
.ruff_cache, and ~/.cache/pip at the end. GitHub-hosted runners are ephemeral,
so nothing persists after the job.

## Required GitHub secrets

| Secret | Value |
| --- | --- |
| VPS_HOST | 139.99.88.23 |
| VPS_USER | deploy |
| VPS_PORT | 22 |
| VPS_SSH_KEY | private key matching github-actions-deploy |
| VPS_KNOWN_HOSTS | the two host-key lines below |
| GTM_AI_API_KEY | app client plaintext key (required only when .env is missing) |
| GTM_SECRET_KEY | long random secret (optional; generated if absent) |
| GTM_AUTO_PAIR_KEY | optional Android production bootstrap key |
| GTM_ENV_FILE | optional full .env override, multiline |

If the server already has .env, the deploy leaves it untouched and does not
require GTM_AI_API_KEY.

    139.99.88.23 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPNBetaOdE0+KO67iSgP3db8Ctc0gC8yRU5fiEdlWOkR
    139.99.88.23 ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDEZj1/gP4ujyrypiOVZU9/loisTH23JHeR5upHG/v3l6HB3TMYQosD/NYgfaCK6lT0Wu9lA6ldrAQZoaWSN4asxDGNa1LlzFdyWRwsnSeoOAHRf1FJp+RE1m8iUAqoEjqMojQLl71N1tuhVFUsdReej2R7xTenpkLtz0jV2Q2vM6/WzYlPgKcJnd6pypWZgbt0ZqdmP3AKmAtUAfByQ7rAII/5bIwf3D7tT1ZcFhszGBAKeo/eUYdZ1Z7wshbmlF4ipucGtBwAHkMGD++oWJ9JcPy2ndX0yHAkVlzLQpaojBAQfMKENSMq3vF3aM4cdN7gFd1WLhGSk4dE6UMW9w161QcVjspHWROYjniRnn1mP6VaCasGEuZAfqOY8bNAlW7/xhmvrDox8ZxatYdcbxKStJ6cA7TFK3sR6gNjZoMabpY3pddJLiw9ZgzXtq9a0P7NXdnKZ4yi+zEYLSrO4F7nV807pYyptuzOH4zxRPqbNzGONP4VXI3ktSCmx1s9f+c=

## One-time server setup

The workflow assumes:

- App directory: /home/deploy/antaratask/backend
- Systemd service: antaratask-backend
- Backend on 127.0.0.1:8765
- Public hostname antaratask.dream-team.works reverse-proxied to that port

1. The master deploy bootstraps .env if it is missing, using the
   GTM_AI_API_KEY (and optional GTM_SECRET_KEY / GTM_ENV_FILE) secrets.
   It is excluded from rsync and preserved on later deploys.

   To create it manually instead:

        mkdir -p /home/deploy/antaratask/backend
        cd /home/deploy/antaratask/backend
        cp deploy/env.example .env
        # fill GTM_SECRET_KEY and GTM_AI_API_KEY (the app client's plaintext key)
        chmod 600 .env

2. Install the systemd unit (as root):

        sudo install -m 644 deploy/antaratask-backend.service /etc/systemd/system/
        sudo systemctl daemon-reload
        sudo systemctl enable --now antaratask-backend

   The unit runs as deploy and reads /home/deploy/antaratask/backend/.env.
   If you prefer a user unit, install it under ~/.config/systemd/user/ and
   let the remote script's systemctl --user fallback restart it.

3. The deploy user needs permission to restart the service. Either allow
   passwordless systemctl restart antaratask-backend via sudoers, or use the
   user-unit fallback.

4. Reverse proxy the public hostname to the loopback backend. Caddy example:

        antaratask.dream-team.works {
          reverse_proxy 127.0.0.1:8765
        }

   Nginx: proxy / to http://127.0.0.1:8765 with the usual Host,
   X-Forwarded-For, and X-Forwarded-Proto headers.

## Deploy

Merge to master. The workflow runs tests, then rsyncs backend/ to
/home/deploy/antaratask/backend, installs into .venv, runs
alembic upgrade head, restarts antaratask-backend, and checks
http://127.0.0.1:8765/api/health.

The remote script refuses to deploy unless .env contains
GTM_AI_CAPABILITY=chat.standard -- this deployment is standard-only.
