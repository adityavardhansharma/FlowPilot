# FlowPilot: Android client for the OpenCode v2 API

Design notes for discussion. Revised on 2026-09-26 against **OpenCode v2**: the npm package `@opencode/cli` 2.0.18 (git tag `v2.0.18`, the same age as the `beta` dist-tag). I checked the source and ran a live `opencode serve`. UX reference: `pingdotgg/t3code` `apps/mobile`.

> The first draft of this doc targeted the old 1.x server (`opencode-ai` 1.18.x). That is superseded. v2 is a different CLI and server, and it has native project, pairing and session management routes.

Default stack where things fork: **Kotlin, Jetpack Compose (Material 3), coroutines + Flow**.

---

## 1. TL;DR

- The OpenCode v2 CLI (`@opencode/cli`, binary `opencode`) runs a password-protected HTTP server. That is either the background **service** (default port 49374) or a foreground `opencode serve`. Every route we need is under `/api/*`.
- **Pairing is built in.** `opencode pair` (or `POST /api/pair`) mints a one-time, 5-minute code. The phone redeems it at `GET /auth/connect/:code` with `Accept: application/json` and gets a **30-day token**, which it uses as the Basic-auth password. That is our QR login.
- Native v2 routes now cover projects (`GET/PATCH /api/project`) and session create, list, get, rename, delete, fork and move. **No v1 fallbacks are needed.**
- Streaming is **one global SSE stream**, `GET /api/event`. It is volatile by contract: no replay, and events are lost while disconnected. So the app resyncs messages over REST after every reconnect.
- The event names match OpenCode's own app: `session.text.delta`, `session.tool.called`, `session.step.ended`, and so on.
- The spec still calls itself "Experimental HttpApi surface". Pin a server version (`GET /api/info` returns `version`) and decode leniently.

---

## 2. Server facts (verified live on 2.0.18)

| Thing | Detail |
|---|---|
| Install | `npm i -g @opencode/cli` (the binary is `opencode`, plus an `opencode2` alias). |
| Run | **Background service:** `opencode service start`. It uses port `49374` by default, configured with `opencode service set port/hostname …`, and stores its password in the service config. **Foreground:** `opencode serve --hostname 0.0.0.0 --port 4096`. Without `OPENCODE_PASSWORD`, it generates a random password and prints `server password …`. The hostname defaults to `127.0.0.1`, so LAN access needs `0.0.0.0`. The `--mdns` flag is **gone** in v2. |
| Auth | Always on. `Authorization: Basic base64("opencode:" + password_or_token)`. Unauthenticated requests get `401 {"_tag":"UnauthorizedError"}`. |
| Pairing | `POST /api/pair` → `{code, expires_in:300}`, single-use. `GET /auth/connect/:code` with `Accept: application/json` → `{"token":"<expiry>.<sig>"}`, valid for 30 days. Tokens are signed with the password, so rotating the password revokes every paired phone. `opencode pair --url https://my-box.ts.net` prints links with an external URL, which works for Tailscale. |
| Info | `GET /api/info` → `{version, pid, urls[], paths}`. Use it as the health check and the version gate. |
| Spec | `GET /openapi.json` (115 paths). `/doc` now serves the web UI. |
| Scoping | `?location[directory]=/abs/path` on location-scoped routes such as models, agents and fs. Session routes resolve the location from the session. |
| Migration | v2 reads existing 1.x sessions. Old chats show up in `GET /api/session`. |

### Core concepts

- **Project**: `{ id, canonical (dir), vcs?, name?, icon?, time{created,updated,active} }`. Created implicitly when a session is started in a folder. In v2, **every folder gets its own project**, git or not (verified: a non-git scratch folder got its own id). The special `global` project only holds migrated 1.x chats.
- **Session**: `{ id, projectID, title?, model, agent, location, cost, tokens, time, parentID? }`.
- **Message** (tagged `type`): `user`, `assistant` (with `content[]` of `text`, `reasoning` and `tool` parts, plus `finish`, `error`, `cost` and `tokens`), `idle` (end-of-run marker with `outcome`), `synthetic`, `shell`, `compaction`, `agent-switched` and `model-switched`.
- **Inbox**: prompts are queued items. `delivery: "steer"` injects into the running turn, and `"queue"` waits for the current turn to finish.
- **Forms** replace v1 "questions". The agent asks you something through a form you fill in.

---

## 3. Feature → API mapping (v2 only)

| Feature | Call(s) | Notes |
|---|---|---|
| Pair (QR) | Desktop: `opencode pair --url <reachable url>` shows a link or QR. Phone: `GET /auth/connect/:code` (Accept JSON) → store the token | Manual fallback: URL + password. |
| Health / version | `GET /api/info` | Warn when outside the tested range. |
| List projects | `GET /api/project` | Sort by `time.active`. |
| Rename project / icon | `PATCH /api/project/:id` | |
| Create project | `POST /api/session { location:{directory} }` in a new folder | Pick the folder via `GET /api/fs/list?location[directory]=…`. |
| Chat without a project | `POST /api/session { location:{directory:<scratch folder>} }` | That folder becomes one project. The app labels it "No project" (see open question 1). |
| Home: all chats | `GET /api/session?order=desc&limit=50&parentID=null`, then `cursor=` | `parentID=null` hides sub-agent child sessions. Filter with `project=` and search with `search=`. |
| Running indicator | `GET /api/session/active` + `session.status` / `session.execution.*` events | |
| Open chat | `GET /api/session/:id` + `GET /api/session/:id/message?order=desc&limit=30`, then `cursor` | |
| Send | `POST /api/session/:id/prompt { text, files?, agents?, skills?, delivery? }` | Verified. Returns the inbox item. The body is flat now, with no `prompt` wrapper. |
| Stop | `POST /api/session/:id/interrupt` | |
| Rename / delete chat | `PATCH /api/session/:id { title }` (verified 204), `DELETE /api/session/:id` | |
| Fork | `POST /api/session/:id/fork` | Later phase. |
| Model picker | `GET /api/model` (194 models in my test), `GET /api/model/default`, `GET /api/provider` | Model shape: `{id, providerID, name, family, capabilities, variants[{id, settings}], …}`. Variants are reasoning-effort levels. |
| Change model / agent | `POST /api/session/:id/model`, `POST /api/session/:id/agent`; list agents with `GET /api/agent` | Agents: Build, Plan, and others. Show the ones with `mode: primary` and not `hidden`. |
| Permission prompts | Event `permission.asked` → `POST /api/session/:id/permission/:reqId/reply { decision: "once"\|"always"\|"reject", message? }` | Also `GET /api/session/:id/permission`. |
| Agent questions | Events `form.created`/`form.replied` → `POST /api/session/:id/form/:formId/reply { answer }` | |
| Queued messages | `GET /api/session/:id/inbox`, `PATCH`/`DELETE …/inbox/:id` | Show or edit queued messages, like t3code's queued-message chip. |
| Diff of changes | `GET /api/session/:id/diff`, `GET /api/vcs/status` | Later phase: a review screen. |
| Slash commands, @files | `GET /api/command`, `GET /api/fs/find` | Later phase. |

---

## 4. Streaming design

`GET /api/event` is the single stream. It sends `server.connected` first, then everything: catalog updates (`model.updated`, `project.updated`, …), `session.created/updated/renamed/deleted`, and the per-turn events. Events carry `durable.seq` per session. The global stream has no replay, but v2 has an experimental per-session replay stream, `GET /api/experimental/session/:id/log?after=<seq>&follow=true`, which returns durable events (no deltas) after `seq` and then stays live. Use it for the open chat on reconnect (see build-plan.md).

The turn sequence I observed live: `session.inbox.enqueued` → `session.execution.started` → `session.inbox.delivered` → `session.step.started` → (`text.started` / `text.delta`* / `text.ended`, `reasoning.*`, `tool.input.*`, `tool.called` / `progress` / `success` / `failed`) → `session.step.ended` or `step.failed` → `session.execution.succeeded` or `failed`. The model call itself was blocked by my sandbox's network, so I have seen the failure path end-to-end, but not text actually streaming back.

**Plan:**
- One OkHttp `EventSource` while the app is in the foreground, exposed as a `SharedFlow<OpenCodeEvent>`. Reconnect with backoff.
- On **every** (re)connect, after `server.connected` arrives: replay the open chat through `session.log?after=<last seq>` (fall back to refetching its latest message page) and `/api/session/active`, and refresh Home. Events that arrive during the refetch get applied on top, keyed by message id and part id, the same approach as OpenCode's own app.
- The chat reducer is pure Kotlin. `text.delta` appends to the part with that `textID`, `text.ended` replaces it with the full text (so it self-heals), tool events update tool rows, and `step.failed` or `session.error` show an error card.
- Use `kotlinx.serialization` sealed classes (`classDiscriminator="type"`) with an `Unknown` fallback.
- The stream fails for slow consumers, so parse it on `Dispatchers.IO` into a buffered channel and never block on the UI.

---

## 5. Android architecture

```
app/
  ui/       Compose screens + ViewModels (StateFlow<UiState>)
            servers/ home/ newchat/ chat/ modelpicker/ settings/
  domain/   models, ChatReducer, use cases (pure Kotlin, unit-tested)
  data/
    api/    OpenCodeApi (Retrofit), DTOs, EventStream (okhttp-sse), AuthInterceptor
    repo/   ServerRepository, ProjectRepository, SessionRepository, ModelRepository
    local/  Room cache (sessions, messages), DataStore (servers, prefs), Keystore for tokens
  di/       Hilt
```

| Concern | Pick |
|---|---|
| UI | Compose + Material 3 + Navigation Compose. Adaptive list-detail layout for tablets later. |
| Async | Coroutines + Flow |
| HTTP / SSE | OkHttp + Retrofit + `okhttp-sse` |
| JSON | kotlinx.serialization |
| DI | Hilt |
| Cache | Room (instant cold start, then reconcile) |
| Secrets | Tokens encrypted with Android Keystore |
| QR | CameraX + ML Kit barcode scanning |
| Markdown | `mikepenz/multiplatform-markdown-renderer` (m3) + code highlighting |
| Wire types | Hand-written DTOs for our ~25 endpoints, plus a CI script that diffs them against `/openapi.json` from the pinned server version. |

Networking: plain `http://` to a LAN IP needs a `network_security_config` cleartext allowance. The recommended remote setup is Tailscale (with HTTPS through `tailscale serve`) together with `opencode pair --url`.

---

## 6. UI, borrowing from t3code mobile

1. **Connect**: "Scan pairing QR" as the primary action, with URL + password as the fallback. A status dot per server. This is the same idea as t3code's `pairing.ts`, which uses a URL-hash token.
2. **Home**: chats grouped by project, with a "No project" group and a recent-activity sort. Each row shows the title, project, relative time, a working dot, and a pill for a pending permission or error. Search in the top bar, swipe to archive or delete, and a FAB for a new chat.
3. **New chat**: the composer is visible immediately, with **Project** and **Model** chips above it. The session is created on the first send, so there are no empty chats.
4. **Chat**:
   - User bubbles. Assistant markdown with copyable code.
   - A collapsible "Thinking" row for reasoning.
   - Compact tool rows (spinner, check or cross, tap to expand input and output).
   - Inline permission and form cards.
   - A floating "Working · 0:42" pill that doubles as the reconnect notice.
   - An error card with Retry.
   - Follow-scroll only while the user is at the bottom.
5. **Composer**: a growing text field, and a Send button that turns into Stop while running. The toolbar holds a model pill (a bottom sheet grouped by provider, with search, variant or effort, and context size) and an agent pill (Build/Plan). While a turn runs, a sent message is queued, and a chip lets you steer or cancel it.
6. **Settings**: servers, theme, default model, and the "No project" folder.

---

## 7. Suggested phases

1. **Pair + Home**: QR and manual connect, `/api/info`, session list grouped by project, active dots.
2. **Chat read + stream**: messages, the SSE reducer, reconnect resync.
3. **Send + control**: composer, new chat with project and no-project pickers, model and agent pickers, stop.
4. **Interactivity**: permission and form cards, rename, delete, queued messages.
5. **Polish**: Room cache, markdown, tablet layout, diff viewer, notifications.

---

## 8. Open questions

1. **"No project" chats.** v2 makes every folder a project, so "No project" has to be a convention. Recommendation: one scratch folder (`~/flowpilot-scratch`), shown in the app as "No project". A folder per chat would also work, but clutters the project list.
2. **Connection.** Recommendation: LAN + Tailscale with QR pairing for v1, and no cloud relay.
3. **Background service vs `opencode serve`.** Recommendation: document `opencode service start` (it's always on and survives terminal close), plus `opencode service set hostname 0.0.0.0`. I haven't verified that `set` key yet.
4. **Notifications when a turn finishes while the app is closed.** Recommendation: later. It needs a foreground service or a push relay.
5. **Supported version.** Recommendation: pin 2.0.x and re-verify on each release, because v2 still labels itself experimental.
6. **Attachments and voice**: later. `files[]` takes URIs, so image upload needs a design choice.
