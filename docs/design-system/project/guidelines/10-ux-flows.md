# UX principles and flows

Every flow below is written as the steps a thumb takes. Each one names the components it uses, the API call behind each step, and what the user sees when that call is slow or fails. Build flows in this order. A flow that isn't here needs a spec before it gets UI.

## Principles for flows

- **One decision per screen.** Pairing asks for a computer, the new-chat sheet asks for a project, and the composer asks for words. Nothing asks two things at once.
- **Optimistic, then honest.** Show the result of an action immediately (the user bubble, the new chat row, the renamed title). Reconcile it when the server answers. If the server refuses, roll back in place and say why in a snackbar with a Retry action.
- **Resume, don't restart.** Cold start opens the last chat you were in, scrolled to where you left it, with the draft restored. Only first run lands on Home.
- **Work continues without you.** Leaving a chat never stops the agent. Approvals and finishes reach you as notifications and in Inbox, wherever you are.
- **Undo beats confirm.** Archive, delete draft and dismiss get an Undo snackbar. Only irreversible server-side actions get a dialog: deleting a chat, discarding worktree changes, and force-stopping a shell.

## 1. First run and pairing

1. **Welcome.** The `PairingScreen` hero shows the loading shape morphing slowly (`spring-slow-spatial`), with one line: "Drive OpenCode from your phone." The primary action is `Button` M filled "Scan QR code". Secondary actions are text buttons: "Enter address" and "How to set up".
2. **Scan.** A CameraX full-bleed viewfinder with a `radius-xl` cutout. The QR comes from `opencode pair --url` on the computer. A valid code shows a `LoadingIndicator` inside the cutout while the app calls `GET /auth/connect/:code` with `Accept: application/json`.
3. **Manual entry.** The fields are Address (`http://100.x.y.z:49374`) and Password, each an `OutlinedTextField` with helper text. On "Connect" the app probes `GET /api/info` with Basic auth. Errors appear inline under the field that caused them ("Wrong password", or "Nothing answered at that address").
4. **Success.** The check in a Cookie12Sided morphs to a Circle once, with the copy "Connected to MacBook Pro · OpenCode 2.0.18". The app stores the token in EncryptedDataStore and goes to Home after 900ms, or when the user taps.
5. **Version guard.** If `/api/info` reports a major version other than 2, show a blocking card: "This computer runs OpenCode 1.x. FlowPilot needs OpenCode 2. Update with `npm i -g @opencode/cli`." Include a copy button.

## 2. Home: find a chat in under two seconds

- The list is **every chat on the computer**, newest activity first, grouped by time: Today, Yesterday, This week, Earlier. It comes from the session list endpoint and is cached in Room, so it paints instantly from cache and then refreshes.
- **Row anatomy** (`ThreadRow`): project shape, then title (one line), then the last line of the agent's reply (one line), then a trailing relative time and status.
- **Status priority:** needs you, then failed, then working, then unread, then idle. A chat that needs you floats into a pinned "Needs you" group at the top, in `tertiary`.
- **Pull to refresh** (`PullToRefreshBox`) forces a refetch. It is rarely needed because the event stream keeps rows live.
- **Swipe** right to pin and left to archive (`SwipeToDismissBox`), each with an Undo snackbar. A long-press opens the row's menu: Rename, Move to project, Fork, Copy link, Archive, Delete.
- **Search** (`AppBarWithSearch`, then `ExpandedFullScreenSearchBar`) matches titles first, then message text from the local cache. Results group by project.
- **The FAB** is `MediumExtendedFloatingActionButton` "New chat" with `edit_square`. It collapses to an icon-only FAB when the list scrolls down and extends again when it scrolls up.

## 3. Start a chat

The new-chat sheet (`ModalBottomSheet`, half height) is one question: **where should the agent work?**

1. **Recent projects** come from `GET /api/project`. There are up to five `SegmentedListItem` rows with the project shape, name and path in `code-small`. One tap starts a chat there immediately, focuses the composer and raises the keyboard. The session isn't created until first send, so abandoned chats never litter the server.
2. **All projects…** opens the Projects tab in picker mode.
3. **New folder** asks for a name and a parent from a folder browser (`fs.list`, directories only). On create, the app writes `<path>/.gitkeep` with `fs.write`, which creates parent directories. A "Start with git" switch (on by default) runs `git init` via `/api/shell` with `cwd`.
4. **Clone repository** takes a URL field with paste detection (a GitHub URL on the clipboard pre-fills it), a destination and the default branch. Progress shows as a `LinearWavyProgressIndicator`, determinate once git reports percentages. If clone fails, the shell output stays visible in a `CodeBlock`, with Retry.
5. **No project** starts in the scratch folder `~/FlowPilot/Scratch/<date>`, created on demand and labelled "No project" in the UI.

## 4. Send, watch, steer

1. **Type.** The composer grows to 6 lines, then scrolls inside itself. A draft saves on every keystroke, debounced 300ms and stored per chat.
2. **Send** (tap, or Enter on a hardware keyboard) triggers the choreography described under Motion. The call is `POST prompt {text, files, agents, delivery}`.
3. **Streaming.** Text arrives on `session.text.delta`, keyed by ordinal. The renderer appends to the current block. Markdown is parsed incrementally: close fences and lists provisionally, re-parse the last block only.
4. **Auto-scroll** follows only while the user is within 48dp of the bottom. Scrolling up pins the view, and a `JumpToLatest` pill appears showing the unread count ("3 new").
5. **Tools** appear as rows inside the current `WorkGroup`, in the order `session.tool.called` arrives. The group header reads the latest running tool in the present tense. When the turn finishes, the group collapses to a past-tense summary.
6. **Steer vs queue.** While the agent works, the send button becomes a `SplitButton`. The main part sends now, as a steer. The trailing arrow opens "Send when finished", which queues. Queued messages show as `QueuedChip`s above the composer. They can be edited or removed until they send.
7. **Stop** is a squared send button with the `stop` icon. One tap aborts. There is no confirmation, because the partial answer stays.

## 5. The agent needs you

- **Approvals** (a permission asked) insert a `PermissionCard` at the end of the chat. The same card shows in Inbox and as a heads-up notification with actions (Allow once / Deny).
  - The card shows exactly what will happen: the command in a `CodeBlock`, the path, or the diff preview.
  - Its buttons are a `ButtonGroup`: **Allow once** (filled), **Always allow** (tonal), **Deny** (outlined).
  - The answer goes as `{decision: once|always|reject}`.
  - After the answer, the card collapses to one line: "Allowed `npm test` once".
- **Questions** arrive as forms and render as a `FormCard`. Single-choice fields are `SegmentedListItem` radios. Multi-choice fields are checkboxes. Free text is a `TextField`. Submit stays disabled until required fields are filled.
- While anything waits on the user, the chat's working pill turns amber: "Needs you · 1 approval". The Inbox badge counts it.

## 6. Change the model or agent

- The model chip in the composer shows the provider logo (from its icon asset), the model name and a chevron. It opens `ModelPickerSheet`.
- **The sheet:**
  - Search at the top.
  - "Recent" (last three) first, then providers as segmented groups.
  - Only models marked visible in Settings appear, plus a "Show all models" row.
  - Each row shows the name, context window ("200k") and capability icons (reasoning, vision).
  - The current model is selected, with `check` and FILL 1.
  - Tapping a row selects it and closes the sheet.
- **Variants** (reasoning effort) appear as a `ButtonGroup` under the selected row: Low, Medium, High.
- **Agent toggle** (Build/Plan) is a connected `ButtonGroup` in the composer toolbar. `SEGMENT_TICK` haptic.

## 7. Reconnect and history

- The global event stream drops on network changes, as expected. Show nothing for the first 2 seconds, then a `ServerChip` in "Reconnecting" state (a spinning `sync` icon), never a blocking screen.
- On reconnect, each open chat replays from its last sequence number through the session log (`after=seq&follow=true`). The list refetches pages changed since the last sync. Rows animate into their new positions. They never flash.
- **Full history:** opening an old chat paints the cached messages first, then pages older messages in when the user scrolls to within 20 items of the top. Show a `LoadingIndicator` row while fetching and keep the scroll anchored.

## 8. Notifications and away mode

- There are three channels:
  - **Needs you:** high importance, heads-up, with actions.
  - **Finished:** default importance.
  - **Background activity:** low, silent, ongoing while a turn runs, with a live duration.
- On Android 16, the running turn uses `Notification.ProgressStyle` with segments per tool. The Live Update chip shows "Working · 0:42".
- Tapping a notification deep-links to the chat, scrolled to the card that needs the user.
