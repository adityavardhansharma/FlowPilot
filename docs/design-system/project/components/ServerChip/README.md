The server chip shows which computer you're connected to and whether the connection is healthy.

## States
- **Connected:** a jade dot and the computer's name.
- **Reconnecting:** a rotating `sync` icon. It appears only after 2s of disconnection, so blips stay invisible.
- **Offline:** `error-container` with `cloud_off`.

## Behavior
- Tapping it opens a sheet with the connection details (address, OpenCode version, latency) and actions: Switch computer, Pair another, Settings.
- It is 40 tall inside the 64 top bar, with a touch target of at least 48.

## Compose
An `AssistChip`-shaped `Surface(shape = CircleShape, color = surfaceContainerHighest)` with an `AnimatedContent` label keyed by connection state on `defaultEffectsSpec()`.
