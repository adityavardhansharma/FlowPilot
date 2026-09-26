The floating toolbar holds the actions for a full-screen viewer (Review, the device viewer) close to the thumb.

## Anatomy
- 64 tall, `radius-full`, `elevation-2`, 8 padding. The standard style is `surface-container`. The vibrant style (`primary-container`) is for the device viewer.
- Up to 5 icon buttons plus an optional filled button as the primary action.

## Compose
`HorizontalFloatingToolbar(expanded = !scrolledDown, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors())` with `FloatingToolbarDefaults.exitAlwaysScrollBehavior`.

## Rules
- It hides on scroll down and returns on scroll up.
- Never on a screen that has a composer.
