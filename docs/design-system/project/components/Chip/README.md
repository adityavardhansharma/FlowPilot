Chips filter a list, show an attachment, or offer a suggested prompt.

## Anatomy
- 32 tall with `radius-sm`. Unselected chips have an `outline-variant` border. Selected chips fill `secondary-container` with a leading `check`.
- Input chips (attachments) are `radius-full` with a trailing remove icon at 18.

## Compose
- `FilterChip` for Home filters.
- `InputChip` for composer attachments and `@file` mentions.
- `SuggestionChip` for starter prompts in an empty chat.
- `AssistChip` for "Open in Review" under a diff.

## Rules
- Filter chips scroll horizontally with 16dp edge padding and are never wrapped.
- Starter prompts are specific to the project ("Explain the auth flow"), generated from the file tree. They never say "Ask me anything".
