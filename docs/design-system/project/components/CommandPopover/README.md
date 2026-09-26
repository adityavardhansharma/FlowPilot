The command popover completes `/` commands, skills and `@` file mentions as you type.

## Anatomy
- A menu on `surface-container-high`, `radius-lg`, `elevation-2`, anchored above the composer with up to 5 rows visible.
- Rows: an icon, the command in `code-medium`, and a one-line description. The highlighted row is `surface-container-highest`.
- For `@` files, rows show the file icon, the file name, and the directory in `code-small`, fuzzy matched with `fs.find`.

## Behavior
- It filters on every keystroke. Hardware arrows move the highlight, and Enter or Tab inserts.
- A chosen file becomes an `InputChip` in the composer and is sent as a file part.
- Esc or a tap outside closes it and keeps the typed text.
