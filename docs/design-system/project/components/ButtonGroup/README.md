A connected button group picks one of two to four options that apply immediately, such as the Build/Plan agent toggle.

## Anatomy
- The segments are 40 tall with 2dp gaps between them.
- Outer corners are `radius-full`. Inner corners are `radius-sm`.
- The selected segment morphs fully round and fills with `secondary` / `on-secondary`. Its icon switches to FILL 1.
- Unselected segments are `surface-container-highest` / `on-surface-variant`.

## Compose
`ButtonGroup` containing `ToggleButton`s, with `ButtonGroupDefaults.connectedLeadingButtonShapes()`, `connectedMiddleButtonShapes()` and `connectedTrailingButtonShapes()`. Set `Modifier.semantics { role = Role.RadioButton }` on each segment.

## Where it's used
- Agent toggle (Build / Plan) in the composer toolbar.
- Reasoning variant under the selected model in the model picker.
- Device refresh interval.
- Approval choices are **not** a connected group. They are separate buttons, because each one commits.

## Rules
- Two to four options only. Five or more calls for a menu or a sheet.
- Every option applies instantly. If a choice needs a Save button, it doesn't belong in a button group.
- Haptic `SEGMENT_TICK` on change. The morph uses `spring-fast-spatial`.
