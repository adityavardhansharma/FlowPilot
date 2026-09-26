Settings and model visibility are segmented lists of rows that change one value each, saved instantly.

## Anatomy
- Segmented groups with a `title-small` header (`on-surface-variant`) and an optional trailing text action ("Show all").
- Rows: an optional 24 leading icon, the title (`body-large`), the current value as the supporting line, and a trailing `Switch`, chevron or value.
- **Switch:** 52×32. The unchecked thumb is 16 in `outline`. The checked thumb is 24 in `on-primary` on a `primary` track, with a `check` icon. The thumb moves on `spring-fast-spatial` and plays `SEGMENT_TICK`.

## Compose
`SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)`, with a `Switch(thumbContent = { if (checked) Icon(check) })`. The whole row is the toggle target (`Modifier.toggleable`).

## Rules
- No Save buttons. Every change applies and persists immediately (DataStore).
- Model visibility: at least one model must stay visible. The last switch is disabled, with a tooltip explaining why.
