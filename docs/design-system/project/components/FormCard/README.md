The form card renders a question the agent asked, as a small form you answer inline.

## Anatomy
- A card on `surface-container-low` with a 2dp `tertiary` inner border, so it reads as "needs you" without competing with the approval card's fill.
- An overline with `help` in `tertiary`, and the question in `title-medium-emphasized`.
- Fields map from the form schema:
  - Single choice: `SegmentedListItem` radios.
  - Multi choice: checkboxes.
  - Text: a filled `TextField`.
  - Number: `TextField` with a numeric keyboard.
  - Boolean: a `Switch` row.
- Actions: **Send answer** (filled, disabled until the required fields are filled), and **Skip** (text) when the form allows it.

## Rules
- Preselect the agent's suggested default, if it gave one, with a "Suggested" label.
- After sending, collapse to a receipt: "You chose Postgres 16".
