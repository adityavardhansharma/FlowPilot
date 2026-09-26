The terminal view is a real shell on your computer (PTY over WebSocket), for when you need to run something yourself.

## Anatomy
- Full screen on `code-bg` with `code-ink` text in Google Sans Code 13/20. The prompt is `terminal-prompt`. ANSI colors map to the `code-*` and `diff-*` tokens.
- The extra-keys row above the IME holds Esc, Tab, Ctrl, Alt, the arrow keys, `|`, `/` and `~`. Each key is XS tonal with `radius-md`. Ctrl and Alt are sticky toggles that show a selected state.
- Pinch to zoom text from 10 to 18.

## Phase
Phase 2. Terminals opened by you live in the project's Terminal tab. Shell tool output from the agent stays in tool rows, not here.
