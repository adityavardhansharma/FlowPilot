Dialogs confirm irreversible actions, and nothing else.

## Anatomy
`surface-container-high`, `radius-xl`, 24 padding, with an optional 24 icon in `secondary`. The title is `headline-small`, phrased as the question. The body gives the exact consequence. Actions are Cancel (text) and a confirm that names the act (danger fill when destructive).

## Uses
Delete chat, discard worktree changes, forget a computer, force-kill a shell. Everything else uses Undo.

## Compose
`AlertDialog(icon, title, text, confirmButton, dismissButton)`.
