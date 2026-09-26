# FlowPilot build plan (v2, deep dive)

A native Android client (Kotlin, Jetpack Compose + Material 3, coroutines/Flow) for the **OpenCode v2 API** (https://opencode.ai/v2/docs/api). Everything here was checked against the `@opencode/cli` 2.0.18 source and a live server, and every t3code mobile feature folder (`apps/mobile/src/features/*`) was read for reference. Endpoint shapes are in `opencode-android-design.md` in this folder.

**Contents**
1. Projects, folders and chat history
2. Model picker and "which models to show" settings
3. How streaming text works in the UI
4. How tool calls look in the UI (including web and browser)
5. Emulator and device mirroring on the phone
6. Every t3code mobile feature and how FlowPilot does it with OpenCode
7. Extra features beyond t3code
8. Architecture
9. Milestones
10. Testing, risks and decisions

---

## 1. Projects, folders and chat history

**How OpenCode models this:** a project is simply a folder that has had a session in it. `GET /api/project` lists every project the server knows about, including the ones you opened from the desktop app or the TUI. It returns `{id, canonical (folder), vcs, name, icon, time.active}`. So "choose an existing project that's already open in OpenCode" comes for free.

### 1.1 Add project (the same three sources as t3code's `AddProjectScreen`)

| t3code option | FlowPilot flow | OpenCode v2 calls |
|---|---|---|
| **Local folder** ("Browse a folder on disk") | A folder browser that starts at the server's home, with breadcrumbs, search and "Use this folder". | `GET /api/fs/list?location[directory]=<dir>` (verified: lists `path` and `type`), `GET /api/fs/find` for search |
| **New folder** | "New folder" in the browser: pick a name, and optionally tick "Initialise git". | `POST /api/experimental/fs/write?path=<dir>/.gitkeep` (the doc says it *creates parent directories*) creates the folder. For git: `POST /api/shell { command: "git init", cwd: "<dir>" }`, then poll `GET /api/shell/:id` until it exits. |
| **Clone from URL** ("Git URL", "Clone destination") | Paste a GitHub or git URL, pick the destination folder, then watch a progress sheet. | `POST /api/shell { command: "git clone --progress <url> <dest>" }` and stream progress through `GET /api/shell/:id/output?cursor=` |
| (after any of these) | The project appears on Home, and you land in a new chat inside it. | `POST /api/session { location:{directory} }` creates the project |

"Project already exists" (a t3code string) maps to checking `GET /api/project` for the same `canonical` folder before creating anything.

Project settings (rename, icon/colour, startup command) go through `PATCH /api/project/:id`.

### 1.2 Start a new chat in an existing project

On Home, tap the project header's **+**, or use the FAB's project picker (recent projects sorted by `time.active`). You can optionally choose a **branch or worktree**: `GET /api/vcs/branch` lists branches, and `POST /api/worktree` creates an isolated worktree for the chat. That matches t3code's "New task → branch picker" and "worktree setup" sheet. The session is created on first send: `POST /api/session { location:{directory: project or worktree}, model, agent }`.

### 1.3 Full chat history for existing chats

- **Chat list per project:** `GET /api/session?project=<id>&parentID=null&order=desc&limit=50`, then follow `cursor.next` until it's empty (infinite scroll).
- **All chats (Home):** the same call without `project`, so it covers every project. 1.x chats show up too, because v2 migrates them (verified).
- **Full transcript:** `GET /api/session/:id/message?order=desc&limit=50` and keep following `cursor` backwards as the user scrolls up. Each page is cached in Room, so reopening is instant and older pages are fetched only once. The transcript includes every message type: user, assistant (text, reasoning, tool calls), shell, compaction summaries, model or agent switches, and `idle` end markers.
- **Sub-agent chats:** a `subagent` tool call creates a child session (`parentID`). It renders as a card ("Explore agent · 12 tool calls") that opens the child transcript through `GET /api/session?parentID=<id>`.
- **Search:** `GET /api/session?search=` covers titles. A later "search inside chat" would run over the cached messages in Room.

---

## 2. Model picker and model visibility

**Source of truth:** `GET /api/model` returns every model from every connected provider (194 on my test server). Each one has `{id, providerID, name, family, capabilities{input:[text,image,pdf], tools}, variants[{id, settings}], limit.context, cost, time.released, status}`. `GET /api/model/default` returns the server default, and `GET /api/provider` returns provider names and logos.

**How OpenCode Desktop does "which models to show":** I read `packages/app/src/providers/models/manage.tsx` and `models.tsx`. Visibility is a **client-side** setting. Each model has a visible toggle, there's a per-provider "toggle all", and by default models without a recent release date are hidden. There's also a *recent models* list. The server has no API for this, so FlowPilot does the same thing locally, per server.

**FlowPilot design:**
- **Settings → Models**: a list grouped by provider. Each group has a master switch and each model has its own switch. There's search and a "Show only recent (last 6 months)" default, like Desktop. Choices are stored in DataStore, keyed by server.
- **Picker (bottom sheet from the composer pill):**
  1. A **Recent** row (your last 5 picks) at the top.
  2. Then visible models grouped by provider.
  3. Each row shows the name, a context size chip (such as "200k"), capability icons (an eye icon for images, a tool icon for tools), and a price hint.
  4. Tapping a model that has **variants** opens a segmented control for reasoning effort (none, low, medium, high, …), from `variants[].id`.
  5. "Manage models…" at the bottom jumps to Settings.
- Changing the model mid-chat calls `POST /api/session/:id/model {model:{id, providerID, variant}}`. The feed shows the resulting `model-switched` marker.
- **Agent pill** next to it: Build and Plan (from `GET /api/agent`, filtered to `mode: primary` and not `hidden`). It calls `POST /api/session/:id/agent`.

---

## 3. How streaming text works in the UI

**What the server sends** (verified event names, from `schema/src/session-event.ts` on v2): for each assistant message, `session.step.started`, then `session.text.started {ordinal}` → `session.text.delta {ordinal, delta}` × N → `session.text.ended {ordinal, text}`. Reasoning follows the same pattern with `session.reasoning.*`. `ordinal` is the index of the part inside the message.

**Pipeline on the phone:**
1. **EventStream** (OkHttp SSE, IO thread) parses each line into a sealed class and pushes it into a buffered channel. It never blocks, because the server kills slow consumers.
2. **ChatReducer** keeps an in-memory `StreamingPart(ordinal, StringBuilder)`, and `delta` appends to it. The reducer emits UI state at most **once per frame** (a `Choreographer`-paced conflation of about 16 ms). That way 50 tokens a second causes about 60 recompositions a second, not one per token.
3. **Markdown rendering while streaming.**
   - Settled blocks (finished paragraphs and closed code fences) are parsed once and cached.
   - Only the *last, open* block is re-parsed on each frame.
   - An unclosed code fence is shown as a code block that is still growing.
   - A blinking caret sits at the end of the text.
4. `text.ended` replaces the buffer with the authoritative full `text`, which corrects any delta that was missed, and the message is written to Room.
5. **Scroll behaviour** (like t3code's `thread-feed-live-follow`): auto-follow only while the user is within about 48 dp of the bottom. Otherwise show a "↓ New output" pill.
6. **Working pill** at the top of the composer: "Working · 0:42" (timer from `session.execution.started`), plus a Stop button (`POST /api/session/:id/interrupt`). It also reports reconnecting, retry ("Retrying in 5s, attempt 2", from `session.retry.scheduled`) and compacting.
7. **Reconnect:** replay the open chat through `GET /api/experimental/session/:id/log?after=<lastSeq>&follow=true`, which returns durable events only and never duplicates. If a part was mid-stream, its `text.ended` arrives in the replay and fills in the full text.

**Reasoning** appears as a collapsed "Thinking…" row with a shimmer (t3code's `ThreadThinkingRow`). Tapping it expands the live reasoning text. When it finishes, the row reads "Thought for 12s".

---

## 4. How tool calls look in the UI

**What the server sends:**
1. `session.tool.input.started {id, name}` → `tool.input.delta` (live, partial arguments) → `tool.input.ended`
2. `session.tool.called {id, input}` → `session.tool.progress {metadata}` (live, for example a shell `shellID`)
3. `session.tool.success {content[], metadata}` or `session.tool.failed {error, content?}`

`content[]` is text or **file** (`{uri, mime}`), so tools can return images such as screenshots.

**Built-in v2 tools** (from `core/src/tool/plugin/*`): `read`, `write`, `edit`, `patch`, `glob`, `grep`, `shell`, `webfetch`, `websearch`, `subagent`, `skill`, `question`, plus MCP tools, plugin tools (such as `browser.*`) and code-mode `execute`.

**Layout (borrowed from t3code's `thread-work-log.tsx`):**
- Consecutive tool calls fold into one **work group**: "Explored 6 files, ran 2 commands, searched the web". While running, it shows a shimmer line with the *current* action ("Reading src/App.kt…"). It collapses automatically when the assistant starts writing text again. Tapping it expands a row per tool.
- **Each row** shows an icon by kind, a one-line summary, a status (spinner, check, cross) and a duration. Tapping it opens a detail sheet.

| Tool | Row summary | Detail sheet |
|---|---|---|
| `read` / `glob` / `grep` | "Read `App.kt` (L1–120)", "Found 14 files for `*.kt`", "Searched `TODO` → 9 hits" | File preview with syntax highlighting; a tappable hit list that opens the file viewer |
| `edit` / `patch` / `write` | "Edited `App.kt` +12 −3" (from `metadata.files[]` diffs) | An inline unified diff with word-level highlights. "Open in Review" |
| `shell` | `$ npm test` with the exit code badge | A terminal-style output box that streams live through `shell.output` while it runs. Copy button |
| `websearch` | "Searched the web: 'compose lazy list perf'" | A results list: favicon, title, domain and snippet (from `results[{url, title, content}]`). Tapping opens a Chrome Custom Tab |
| `webfetch` | A link card: favicon, domain, page title | The fetched markdown rendered, and "Open in browser" |
| `browser.*` (plugin-browser) | "Opened `localhost:3000`", "Clicked 'Sign in'", "Screenshot" | Screenshot thumbnails (tool `file` content, image mime) in a swipeable strip, plus the current URL. This gives a live "what the agent sees" view |
| `subagent` | An agent card: "Explore · 8 tools · 34s" | Opens the child session transcript |
| `question` / forms | An inline form card with the answer UI | none |
| MCP / unknown | "server · tool_name" | Pretty-printed JSON of input and output |

- **Permissions.** When a tool needs approval, `permission.asked` pins a card *in the feed* and *above the composer*. The card shows the action, e.g. "Run `rm -rf build`?" or "Edit 3 files", and offers **Allow once / Always / Deny**, plus a "Deny with message" option. It sends `POST …/permission/:id/reply {decision, message}`. Home shows a "Needs approval" pill, and a notification is sent if the app is in the background.
- **Images** returned by tools are fetched through `fs.read` or the `uri`, and open full-screen with pinch-zoom.

---

## 5. Emulator and device mirroring on the phone

**How t3code does it** (from `docs/internals/devices.md`): their *server* runs `expo-device-hub` (it streams iOS Simulator and Android Emulator video over MJPEG or H.264 WebSocket) and `agent-device` (the CLI agents use to drive the device). A proxy authenticates the stream. The mobile app shows it in a WebView (`DeviceStreamWebView`), and agents get `device_list/open/screenshot/close` tools.

**OpenCode has no device API.** But v2 has the exact extension point we need: **plugins with RPC**. `POST /api/rpc/:rpcID/:method` and `rpc.*` events on the stream let a plugin talk to apps. OpenCode's own `@opencode/plugin-browser` uses this to let the desktop app host a browser that the agent drives, and it even includes a `tunnel.open/read/write` byte relay over RPC. So there are two tiers:

**Tier 1: no plugin, works with plain OpenCode (M6).**
- The host needs Android SDK platform-tools (`adb`) and a running emulator.
- Get a frame: `POST /api/shell {command: "adb exec-out screencap -p > /tmp/fp/screen.png"}` → `GET /api/fs/read/tmp/fp/screen.png`. That's about 1–3 fps: fine for "look at what the agent built", not for gameplay.
- Input: taps and swipes on the image become `adb shell input tap x y`, `input swipe …`, `input text …`, and `input keyevent BACK/HOME/APP_SWITCH`.
- Device list and boot: `adb devices -l`, `emulator -list-avds`, `emulator -avd X`.
- The agent can already do the same with its own `shell` tool, so "agent tests the app, you watch" works on day one.

**Tier 2: a FlowPilot companion OpenCode plugin (`@flowpilot/opencode-devices`), installed on the computer with `opencode plugin add` (post-M7).**
- **Agent tools:** `device.list`, `device.open`, `device.screenshot`, `device.tap`, `device.type`, `device.ui_tree` (the accessibility tree from `uiautomator dump`), `device.close`. Screenshots come back as image `file` content, so they show up in our tool rows as well.
- **App RPC:** `devices.list/boot/shutdown`, `input.touch/key/text`, `settings.*` (dark mode, font size, locale, fake location, permissions: the t3code "Tools drawer").
- **Video:** the plugin runs a loopback stream server. The best options are the emulator's built-in gRPC `streamScreenshot` (`emulator -grpc`), or `scrcpy-server` for real devices, which gives H.264. The phone reaches it through the **RPC tunnel pattern** that plugin-browser uses, so nothing extra is exposed on the network. The phone decodes H.264 with `MediaCodec` onto a `SurfaceView`, which gives 30+ fps with low latency.
- **iOS Simulator** (Mac hosts): the same plugin wraps `xcrun simctl io booted screenshot` (Tier 1 style) or `serve-sim`.
- **UI:** a "Device" tab in the chat (like t3code's `DevicePreviewRouteScreen`). It holds the live screen with touch passthrough and a toolbar (Back, Home, Recents, Rotate, Power, Screenshot to chat). A **floating mini-player** (picture-in-picture) keeps the device visible while you read the chat.

**Bonus, the same trick for web apps:** a "Preview" tab. The phone opens the project's dev server (`localhost:3000` on the host) in a WebView. It can do that directly over Tailscale, or through the plugin-browser `tunnel` relay when the dev server binds only to loopback.

---

## 6. t3code mobile feature map → FlowPilot + OpenCode

| t3code feature (folder) | What it does | FlowPilot via OpenCode v2 | Phase |
|---|---|---|---|
| connection / pairing | QR and URL pairing, environment list, status dots | `opencode pair` → `/auth/connect/:code` token; multi-server list; `/api/info` | M1 |
| cloud (T3 Connect relay) | Reach the computer from anywhere | Tailscale + `opencode pair --url https://<host>.ts.net`; no relay in v1 | M1 (docs) |
| home | Thread list grouped by project, filters, sort, swipe actions, FAB | `session.list` + `project.list` + `session.active`; swipe to archive, snooze or delete | M1 |
| archive | Archived threads screen | Store `archived: true` in **session metadata** (`PATCH /api/session/:id {metadata}`), so it syncs to every client | M4 |
| snooze (CustomSnoozeSheet) | Hide a thread until a time | `metadata.snoozedUntil` + a local alarm to un-snooze | M7 |
| projects | Add a project from a local folder or clone; project grouping | §1: `fs.list`, `fs.write`, `shell` git clone/init, `project.update` | M3 |
| threads / feed / work log | Streaming feed, grouped tool rows, thinking row, agent spawn cards | §3 and §4 | M2 |
| composer | Attachments, `/` commands, `@` skills and files, dictation, usage limits | `command.list` + `session.command`, `skill.list`, `fs.find`, prompt `files[]`; Android SpeechRecognizer for dictation | M5 |
| PendingApprovalCard / PendingUserInputCard | Approvals and agent questions | `permission.*` and `form.*` | M4 |
| queued-message icon | Queue while busy | `delivery: queue/steer`, `session.inbox.*` (edit or cancel queued items) | M3 |
| thread settings sheet | Model, runtime mode (Supervised / Auto-accept edits / Full access) | Model/agent (§2). The runtime mode maps to the session **permissions ruleset** (`PATCH /api/session/:id {permissions}`) | M4 |
| review (diff + comments) | Per-file diffs, comment on lines, send to agent | `session.diff`, `vcs.diff/status`; line comments are sent as a prompt with a file:line context block | M5 |
| git controls | Commit, branch, pull, PR sheets | v2 `vcs` is read-only, so commit, pull and push run through `POST /api/shell` (git) with a confirm sheet. "Ask agent to open PR" sends a prompt | M5 |
| files | Workspace file tree, source viewer, image/video/markdown preview | `fs.list`, `fs.read`, `fs.find` with highlighting | M5 |
| terminal | A native terminal per thread | `pty.create` + `pty.connectToken` + WebSocket `pty.connect`; Termux terminal-view | M6 |
| devices | Live emulator and simulator | §5 | M6 / M8 |
| diffs (native highlighter) | Fast syntax-highlighted diffs | A Compose diff view with a highlight cache (tree-sitter-kotlin via JNI, or a regex highlighter) | M5 |
| usage (UsageRouteScreen, widgets) | Token and cost usage charts, subscription limits | `GET /api/experimental/session/stats` (activity, usage, tool reliability) plus session cost and tokens | M7 |
| agent-awareness | Live Activities, push notifications, notification deep links | A foreground service while turns run: "Finished", "Needs approval" (Allow/Deny actions) and inline reply (RemoteInput → queued prompt). Deep links open the chat | M6 |
| widgets (AgentActivity) | A home-screen widget with running agents | A Jetpack **Glance** widget: running chats and pending approvals | M7 |
| sharing (incoming share) | Share text or images into the app | An Android share target: "New chat with this" or "Send to current chat" | M7 |
| shortcuts | App icon shortcuts | Android dynamic shortcuts for "New chat in <recent project>" | M7 |
| keyboard / command palette | Hardware keyboard shortcuts, palette | A Ctrl+K palette on tablets and Chromebooks | M7 |
| layout (adaptive) | Sidebar + detail + inspector on tablets | Material 3 adaptive list-detail-supporting pane | M7 |
| settings | Appearance, environments, notifications, threads, licences, diagnostics | Plus Models (§2), Providers (`integration.*`), MCP (`mcp.*`), Plugins (`plugin.*`) and saved permissions | M7 |
| diagnostics / observability | Crash log, tracing | Local crash log + an "export debug bundle" (last N events, server version) | M7 |
| voice-input | Dictation in the composer | SpeechRecognizer (on-device when available) | M5 |
| showcase | Screenshot automation | Roborazzi screenshot tests | M0+ |

---

## 7. Extra features beyond t3code

1. **Inline reply from notifications.** Answer "what next?" or approve a command without opening the app.
2. **Fork from any message and undo to here.** `session.fork` and `session.revert.stage/commit/clear`, with a clear "files will be restored" preview from `session.diff`.
3. **Session timeline scrubber.** Jump between user turns in long chats.
4. **Pinned chats and tags.** Stored in session `metadata`, so they're shared with other clients.
5. **Quick Settings tile.** Shows how many agents are running and pending approvals, and opens the next approval.
6. **"Continue on phone".** Paste or scan a session link from the desktop and open the same chat. The pairing link format can carry `#session=<id>`.
7. **Live usage and cost** per chat in the top bar (`cost`, `tokens`, and `session.usage.updated` events), plus a context-window meter from `limit.context` that suggests **Compact** near the limit.
8. **MCP and skills manager.** Connect or disconnect MCP servers and browse skills from the phone.
9. **Provider login from the phone.** `integration.connect.key/oauth` opens a Custom Tab and completes the attempt.
10. **Offline read and drafts.** The Room cache lets you read everything offline, and drafts persist per chat.

---

## 8. Architecture

```
:app                    nav host, DI, theme, adaptive shell, notifications service, widget
:core:model             domain models (Session, Message, Part, Tool, Model, Project…)
:core:api               Retrofit service, DTOs, SSE EventStream, session-log stream, PTY WebSocket, RPC client
:core:data              repositories, Room, DataStore (servers, model visibility, prefs), Keystore vault, SyncEngine
:core:domain            ChatReducer, WorkGroupBuilder, ToolPresenter (tool → row), MarkdownStreamer
:core:designsystem      M3 theme, markdown + code, diff view, pills, cards, shimmer
:feature:connect  :feature:home  :feature:projects  :feature:chat  :feature:composer
:feature:review   :feature:files  :feature:terminal  :feature:devices  :feature:settings
```

- **Room is the one source of truth.** REST pages and stream events write into it, and screens observe it.
- **SyncEngine per server:**
  - The global `/api/event` stream while the app is in the foreground, or held by the service during runs.
  - A per-chat `session.log?after&follow` stream for the open chat.
  - A resync on reconnect.
  - Deltas stay in memory until `*.ended`.
- **Decoding** uses kotlinx.serialization sealed classes with `Unknown` fallbacks everywhere, so the app never crashes on new event, tool or message types.
- **ToolPresenter** is a registry from tool name to row/detail renderer, with a generic JSON fallback. Adding a tool UI is a single file.
- **Libraries:** OkHttp/okhttp-sse, Retrofit, kotlinx.serialization, Hilt, Room, DataStore, CameraX + ML Kit (QR), multiplatform-markdown-renderer-m3, Coil, Termux terminal-view, MediaCodec (device video), Glance (widget), Material 3 adaptive.

---

## 9. Milestones

| # | Milestone | Scope | Done when |
|---|---|---|---|
| M0 | Foundations | Repo, modules, CI (build, lint, tests), DTOs, SSE fixture recorder and replayer, OpenAPI drift check | CI green; the drift check catches a renamed field |
| M1 | Connect + Home | QR/manual pairing, servers, Home grouped by project, active dots, full session paging, Room cache | Pair by QR in under 10 s; all existing desktop chats and projects appear; offline Home works |
| M2 | Chat read + streaming | Full history paging, the §3 streaming pipeline, the §4 tool work log for built-in tools, thinking row, sub-agent cards, reconnect replay | A desktop-started turn streams smoothly on the phone; airplane mode mid-turn catches up exactly |
| M3 | Send + projects | Composer, stop, queue/steer and inbox, new chat in existing project, **add project** (browse, new folder, git init, clone), model picker + agent pill, Settings → Models visibility | Create a folder, clone a repo and start chats in both from the phone; hidden models don't appear in the picker |
| M4 | Human in the loop | Permission cards, forms, runtime-mode presets (permissions ruleset), archive/pin via metadata, fork, undo/revert, compact | Approve a command from the feed; undo restores files |
| M5 | Power tools | `/` commands, `@` files, skills, attachments, dictation, Review (diffs + line comments), files browser, git sheet (via shell) | Review the agent's diff, comment on a line, and the agent fixes it |
| M6 | Background, terminal, devices tier 1 | Foreground service + notifications with actions and inline reply; PTY terminal; emulator view through adb screenshots + input | Approve from a locked phone; watch and tap the emulator while the agent tests |
| M7 | Polish + platform | Tablet layout, Glance widget, QS tile, share target, shortcuts, usage screen, providers/MCP/plugins settings, snooze, diagnostics, a11y, Play internal release | Signed AAB on internal testing; TalkBack pass; 60 fps on 1k-message chats |
| M8 | Devices tier 2 | The `@flowpilot/opencode-devices` plugin (agent tools + RPC + H.264 stream), live device tab, PiP mini-player, web preview tab | A 30 fps interactive emulator on the phone; the agent's device screenshots show in tool rows |

### The three phases

| Phase | Goal | Milestones | Done when |
|---|---|---|---|
| **Phase 1: everything working** | A daily driver: pair, see every chat, read full history, stream, send, stop, steer/queue, approve, answer forms, start chats in existing, new, cloned or no-project folders, pick visible models | M0, M1, M2, M3, plus the permission cards and forms slice of M4 | You can run a whole day of agent work from the phone without opening the desktop |
| **Phase 2: almost full parity** | Everything t3code mobile does that OpenCode supports, plus Android platform features | The rest of M4 (runtime presets, archive and pin, fork, undo, compact), M5, M6 (notifications, PTY terminal, device tier 1), M7 | Every row of the §6 feature map is shipped or explicitly dropped |
| **Phase 3: really hard features** | Things no mobile coding client does well | M8 (devices tier 2: custom plugin, H.264 live stream, PiP), web preview tunnel, live browser tool view, parallel agents on worktrees with compare and merge, relay access without VPN, voice mode | Each feature ships behind a flag once it holds 30 fps or equivalent quality bar |

The design system's phases section (`docs/design-system/project/guidelines/50-phases.md` in the repo) lists the components each phase needs.

---

## 10. Testing, risks and decisions

**Testing:**
- Reducer tests on recorded SSE captures from a real server for every tool type.
- MockWebServer contract tests.
- An OpenAPI drift check in CI.
- Compose UI and screenshot tests (Roborazzi) for every feed item in light and dark themes.
- A manual end-to-end checklist per milestone over LAN and Tailscale.

**Risks:**
- **The v2 API is labelled experimental.** Pin 2.0.x, gate on `/api/info`, decode leniently and drift-check.
- **Several routes we rely on live under `/experimental/`**: `session.log`, `fs.write` and stats. Each has a fallback (message refetch, `shell mkdir -p`, local stats).
- **The shell and PTY routes give the phone full command execution on the host.** Keep pairing tokens in Keystore, recommend Tailscale HTTPS, and require confirmation for destructive git and shell actions.
- **Device video depends on host tooling** (the Android SDK). Tier 1 works with just `adb`, and Tier 2 is optional.
- **Battery.** The service runs only while a turn is running.

**Decisions needed:**
1. **Repo:** create a private `adityavardhansharma/flowpilot`? Recommended: yes.
2. **"No project" chats:** one scratch folder shown as "No project"? Recommended: yes.
3. **Device mirroring:** Tier 1 in M6, and build the Tier 2 plugin later? Recommended: yes.
4. **Archive, snooze and pin stored in session metadata**, so they sync with other OpenCode clients (the desktop app will just ignore unknown keys)? Recommended: yes.
5. **App id**, for example `dev.flowpilot.app`.
