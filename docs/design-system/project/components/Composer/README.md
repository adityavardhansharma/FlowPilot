The composer is where every message starts: text, attachments, agent, model and send, all within thumb reach.

## Anatomy
- A container on `surface-container-high` with `radius-xl`, 8 padding, docked 8 above the IME.
- The field is `body-large`, 1 to 6 lines, then scrolls. The placeholder reflects the state: "Ask the agent to change something…" when idle, "Steer or queue a message…" while running.
- The toolbar row holds: attach `+` (opens a sheet: Photo, File from phone, File from project, Screenshot of device), the Build/Plan `ButtonGroup`, the model chip, a spacer, and send.
- **Send:** a 48 circle in `primary`. It's disabled when empty. While running it becomes **Stop**: `on-surface` fill, a `stop` icon, and the corners morph from round to 14 on `spring-fast-spatial`. With text typed while running, it becomes the `SplitButton` (steer or queue).

## Behavior
- The draft is saved per chat, restored on open, and kept on failure.
- Enter sends on hardware keyboards and Shift+Enter adds a newline. On a touch keyboard, Enter is a newline.
- `/` opens the command popover and `@` opens file mentions.
- Paste an image to attach it. Paste a long text (over 2k characters) and it offers "Attach as file".
- It rides the IME with `imePadding()` and `WindowInsets.ime` animation, and never jumps.

## Compose
A custom `Surface(shape = MaterialTheme.shapes.extraLarge, color = surfaceContainerHigh)` with `BasicTextField(state = rememberTextFieldState())`. The send/stop morph is `animateDpAsState(cornerRadius, fastSpatialSpec())` inside `IconButton(shapes = …)`.
