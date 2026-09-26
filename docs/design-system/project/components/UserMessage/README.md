A user message is what you sent, in a jade bubble aligned to the end.

## Anatomy
- `primary-container` / `on-primary-container`, `body-large`, with 10 and 16 padding.
- Corners are 24 with a 6 tail at the bottom-end corner. The max width is 85%.
- Attachments appear as input chips inside the bubble, above the text. Images show as 96dp thumbnails with `radius-md`.

## Behavior
- Long-press opens a menu: Copy, Edit and resend (forks from here), Revert to here.
- The bubble rises from the composer on send (`spring-default-spatial`, plus a fade on `default-effects`).
- A failed send keeps the bubble with an `error` icon and "Not sent. Tap to retry". The text also stays in the composer.
- Queued messages show at 60% opacity with a caption until they're delivered.
