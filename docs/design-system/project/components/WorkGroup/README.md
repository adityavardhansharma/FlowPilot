A work group gathers the consecutive tool calls of a turn into one card that reads as a sentence.

## Anatomy
- `surface-container-low`, `radius-lg-increased`, with 4 padding. Rows are `ToolRow`s with 12 inner radius.
- **Header while live:** the loading shape, then the present-tense current action (shimmering), then the elapsed time, then a collapse chevron.
- **Header when done:** a `check_circle`, then a past-tense summary built from counts ("Explored 6 files, edited 1, ran 2 commands"). If any tool failed, the header uses `error` and "1 failed".

## Behavior
- Expanded while running and collapsed when the turn ends. It stays expanded if the user expanded it or anything failed.
- New rows insert with `animateItem()` on `spring-default-spatial`. Height changes spring too.
- More than 8 rows: the oldest collapse into "+5 earlier steps".
- Text that arrives between tools closes the group. The next tool starts a new group.

## Summary grammar
Verbs in a fixed order: Explored (read, glob, grep), Searched the web (websearch, webfetch), Edited, Created, Ran (shell), Used (MCP). Counts use numerals, and zero counts are omitted.
