# Production gateway connection

GTM uses the MemeChat Gateway's **app-client chat stream**:

| Setting | Value |
| --- | --- |
| `GTM_AI_BASE_URL` | `https://gateway.dream-team.works/v1/chat/stream` |
| `GTM_AI_API_KEY` | the app client's plaintext key (sent as `X-App-Client-Key`) |
| `GTM_AI_CAPABILITY` | `chat.premium` (4096-token output budget) |
| `GTM_AI_MODEL` | `deepseek-v4.1-flash` |
| `GTM_AI_MAX_OUTPUT_TOKENS` | `4096` |

`chat.standard` is capped at 1024 output tokens; reasoning often consumes
that entire budget and returns no text, so GTM uses `chat.premium`.

No Firebase token is required. The app-client key is the app identity; the
gateway only needs a Firebase UID if the client is configured to require one.

## The key shown in the console is a digest

The backoffice app-client page displays the **SHA-1 digest** under "Client
identity"; the plaintext key is shown only once when a client is created or
when **Rotate key** is used. Use the plaintext value from that one-time dialog,
never the displayed digest.

The client must also have the capability enabled and bound to a routed model
(Capability map → Add capability → Save capability map).

## Wire format

`POST /v1/chat/stream` returns SSE:

```
event: meta
data: {"capability":"chat.standard","requestId":"...","reserved":0}

event: delta
data: {"text":"..."}

event: usage
data: {"inputTokens":37,"outputTokens":12,"creditsCharged":0}

event: done
data: {"finishReason":"stop","balanceAfterCommit":0}
```

GTM accumulates `delta.text`, parses the JSON interpretation, and records the
token usage from the `usage` event.

## Sample request

```bash
curl -N https://gateway.dream-team.works/v1/chat/stream \
  -H "X-App-Client-Key: $GTM_AI_API_KEY" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"requestId":"'$(uuidgen)'","capability":"chat.standard",
       "messages":[{"role":"user","text":"Reply with: ok"}],
       "maxOutputTokens":32}'
```

## Validation

```bash
curl -sS http://127.0.0.1:8765/api/health | jq '.ai'
# provider "gateway", configured true, the chat-stream base_url
```

If the key is rotated, update `GTM_AI_API_KEY` in `backend/.env` and restart
the backend. No code change is needed.
