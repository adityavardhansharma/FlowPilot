A subagent card stands in for a child chat the agent started. You can follow it without losing your place.

## Anatomy
- A card (`surface-container-low`, `radius-lg-increased`) with a Flower shape in `secondary-container`.
- The agent name in `title-medium`, and its live action (shimmering), then a status pill and a chevron.

## Behavior
- Tapping it pushes the child chat with a shared-element transition from the card to the app bar. Back returns you to the same scroll position.
- When the child finishes, the card shows the child's final summary line and "Done".
