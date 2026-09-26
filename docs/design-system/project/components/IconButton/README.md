Icon buttons run a frequent, well-understood action where a label would crowd the layout.

## Anatomy
- The container is 40, with widths narrow 32, uniform 40 and wide 52. The touch target is 48.
- The icon is 24 at weight 400.
- At rest the container is `radius-full`. Pressed, it becomes `radius-md`. A toggled-on button stays square (`radius-md`), filled `secondary-container`, with a FILL 1 icon. The shape change carries the state, not only the color.

## Compose
`IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `IconToggleButton` and `FilledTonalIconToggleButton`, with `shapes = IconButtonDefaults.shapes()` and `IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)` for the wide width.

## Rules
- Every icon button has a `contentDescription` and a `TooltipBox` with a `PlainTooltip` on long-press.
- Use only icons from the iconography table. Never invent a metaphor.
- Leave 8dp between adjacent icon buttons (the 48 targets may overlap visually).
