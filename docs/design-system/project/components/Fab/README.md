The floating action button starts the screen's main creation: a new chat on Home, or a new project on Projects.

## Anatomy
- Extended FAB (medium): 56 tall, `radius-lg`, `primary-container` / `on-primary-container`, `elevation-3`.
- Icon-only FAB: 56, `radius-lg`.
- FAB menu: the FAB morphs into a round 56 close button (`primary`), and items stack upward as 56-tall pills on `primary-container`, staggered 30ms apart on `spring-default-spatial`.

## Compose
`MediumExtendedFloatingActionButton(expanded = !listState.isScrollingDown)`. For the menu: `FloatingActionButtonMenu` + `ToggleFloatingActionButton` + `FloatingActionButtonMenuItem`, with a scrim behind it.

## Rules
- One FAB per screen, and never on the chat screen, where the composer is the primary action.
- The menu has at most five items, each a verb phrase.
- Predictive back closes the menu.
