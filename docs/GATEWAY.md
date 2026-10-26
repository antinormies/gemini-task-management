# Production gateway connection

GTM uses the MemeChat Gateway's first-party JSON completion endpoint; the
Android app and web UI only talk to the local GTM API.

| Setting | Value |
| --- | --- |
| `GTM_AI_BASE_URL` | `https://gateway.dream-team.works/internal/ai/complete` |
| `GTM_AI_API_KEY` | the gateway's `SELF_AI_API_KEY` (server-to-server Bearer) |
| `GTM_AI_MODEL` | `deepseek-v4.1-flash` |

`backend/.env` is gitignored. Never commit either key.

## Two credentials, two surfaces

- **`SELF_AI_API_KEY`** — `Authorization: Bearer ...` for
  `POST /internal/ai/complete`. This is the server-to-server endpoint GTM uses.
  It is not a Firebase token and not an app-client key.
- **App-client key** — `X-App-Client-Key: ...` for `/v1/*`
  (for example `/v1/chat/stream`). In production `/v1/*` is also wrapped in
  Firebase bearer auth, so an app-client key is for the Firebase-authenticated
  mobile app, not for GTM.

## Validation

```bash
# backend running locally
curl -sS http://127.0.0.1:8765/api/health | jq '.ai'
# provider "self", configured true, the production base_url
```

For the Android emulator, expose the loopback backend first:

```bash
adb reverse tcp:8765 tcp:8765
```

Then send a command from the web app or the Android Speak screen. The run's
`model` should be `deepseek-v4.1-flash`; the proposal is produced by the
production gateway.

If the gateway key rotates, update `GTM_AI_API_KEY` in `backend/.env` and
restart the backend. No code change is needed.
