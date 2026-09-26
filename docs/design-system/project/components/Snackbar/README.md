Snackbars confirm an action whose effect is out of view, and carry Undo.

## Anatomy
`inverse-surface` / `inverse-on-surface`, `radius-md`, 48 minimum height, `elevation-3`. The action is in `inverse-primary`.

## Rules
- One line, one action. 4s without an action, 10s with one. A new snackbar replaces the old.
- It sits above the composer and the nav bar, never covering them.
- Never use a snackbar for something the agent needs from you. That's a card.
