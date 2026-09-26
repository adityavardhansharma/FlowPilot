Bottom sheets hold a focused choice or detail that belongs to the current screen: new chat, the model picker, tool details and attachments.

## Anatomy
- `surface-container-low`, top corners `radius-xl`, with a 32×4 drag handle.
- The title is `title-large` with 24 side padding. The content is segmented lists with 16 side padding.
- The scrim is `scrim` at 0.32.

## Compose
`ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false))`, with predictive back built in. Snap uses `spring-default-spatial`.

## Rules
- One question per sheet. Never nest sheets. A second level replaces the content with a back arrow.
- Sheets never hold destructive confirmations. Those use a dialog.
