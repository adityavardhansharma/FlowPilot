# States and feedback

Every screen and component has the same set of states. Designs that skip a state are incomplete. Use this matrix when reviewing a screen.

## The state matrix

| State | What the user sees | Component |
| --- | --- | --- |
| First load, no cache | Skeleton rows at the real row size, on `surface-container-low`, pulsing opacity 1 to 0.6 on `spring-slow-effects` | Skeleton `ThreadRow` |
| Load with cache | Cached content immediately. Refresh is silent, and changed rows animate | none |
| Slow (>1s) | `LoadingIndicator` centered. After 10s: "Still loading. Your computer may be busy." | `LoadingIndicator` |
| Empty | An `EmptyState`: one line of what this place is for, one action. No illustration | `EmptyState` |
| Offline | A `ServerChip` in the offline state and a one-line banner at the top of lists. Cached content stays usable. Actions that need the server are disabled with an explanation on tap | `ServerChip` |
| Reconnecting | `ServerChip` reconnecting, spinning `sync`. Nothing else changes | `ServerChip` |
| Error, recoverable | An inline `ErrorCard` at the place of failure, with a cause and a Retry action | `ErrorCard` |
| Error, fatal to screen | A full-screen `EmptyState` variant with the `error` icon, a cause, Retry, and "Change computer" | `EmptyState` |
| Needs you | Amber everywhere: pill, badge, notification, Inbox | `StatusPill`, `PermissionCard` |
| Done | A quiet `check_circle` in `primary` on the row, a light haptic if the app is foreground | `StatusPill` |

## Streaming states of a turn

1. **Waiting for first token** (from send until the first event). The working pill shows immediately. If nothing arrives for 1.5s, a three-line shimmer placeholder appears where the answer will go.
2. **Reasoning.** A `ReasoningRow` collapsed by default: "Thinking · 4s", with the text shimmering. Tapping it expands the raw reasoning in `body-medium` `on-surface-variant`.
3. **Text streaming.** Text grows with a breathing caret at the end. Code fences render as a `CodeBlock` as soon as the opening fence arrives, and syntax highlighting runs per line when the line completes.
4. **Tool running.** The group header shimmers the current action ("Running `npm test`"), and the row shows a live duration.
5. **Tool finished.** The row's icon crossfades to its done or failed state. A failure shows `error` and expands the row automatically.
6. **Turn finished.** The caret disappears, the stop button morphs back to send, and the group collapses to its summary. A stats line fades in under the answer: "Claude Opus 5 · 12.4k tokens · $0.42 · 1m 12s".
7. **Turn failed.** An `ErrorCard` shows under the partial answer, with the provider error in plain words and "Retry" (which re-sends the same text).
8. **Aborted.** A marker line reads "Stopped by you". The partial answer is kept.

## The feedback ladder

Use the lowest rung that the user can't miss.

1. **Inline change.** The thing itself changes: the row updates, the switch flips, the pill swaps. This is the default.
2. **Snackbar.** For results of actions whose effect is out of view, and for every Undo. One line, one action, 4s, or 10s when it has an action. Never stack them: a new one replaces the old.
3. **Card in the feed.** Anything the agent needs from the user, and errors tied to a turn.
4. **Dialog.** Only for irreversible actions. The title is the question ("Delete this chat?"). The body gives the consequence. The confirm button names the act, in `error` if destructive.
5. **Notification.** Only when the app is not in the foreground on that chat.

## Microcopy for states

| Situation | Copy |
| --- | --- |
| Can't reach the server | Can't reach your computer. Check that OpenCode is running, then retry. |
| Auth rejected | Your pairing expired. Scan a new code to reconnect. |
| Model call failed | The model returned an error: rate limit reached. Try again in a minute or pick another model. |
| Permission denied by user | Denied. The agent will try another way. |
| Clone failed | Clone failed. git said: "Repository not found." |
| Empty project | No chats in this project yet. |
| Empty Inbox | Nothing needs you. |
| Search no results | No chats match "auth redirect". |
