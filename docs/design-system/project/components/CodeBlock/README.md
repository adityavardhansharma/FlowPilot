A code block shows code or command output in Google Sans Code with syntax color.

## Anatomy
- `code-bg` fill and `radius-lg`. A header bar shows the language (`label-medium` `on-surface-variant`), plus wrap and copy icon buttons (narrow).
- Body: `code-medium` 13/20, 12 and 16 padding, horizontal scroll by default. Wrap is a per-block toggle.
- Syntax: `code-keyword`, `code-string`, `code-number`, `code-comment` (italic), `code-function`, and `code-ink` for the rest. These stay fixed under dynamic color.
- More than 24 lines: clip with a fade and a "Show all 212 lines" row.

## Behavior
- Copy gives the `CONFIRM` haptic and swaps the icon to `check` for 1.5s, with no snackbar.
- Horizontal scroll never steals the vertical feed scroll. Consume horizontal drags only.
- Terminal output uses the same block without the language bar. `terminal-prompt` colors the `$`.
