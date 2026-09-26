Buttons trigger one named action and are the only way to commit a decision.

## Anatomy
- Container: `radius-full` at rest. The square variant is `radius-md` at S and `radius-lg` at M. On press it morphs to `radius-sm` at XS and S, `radius-md` at M, and `radius-lg` at L and XL.
- Label: `label-large` at XS and S, `title-medium` at M, `headline-small` at L. An optional leading icon is 20dp at S and 24dp at M.
- Heights: XS 32, S 40, M 56, L 96, XL 136. The touch target is always at least 48.

## Emphasis
| Variant | Use | Colors |
| --- | --- | --- |
| Filled | The one primary action per surface: Send, Allow once, Connect, Clone | `primary` / `on-primary` |
| Tonal | Secondary positive action: Always allow, Retry | `secondary-container` / `on-secondary-container` |
| Outlined | Neutral or negative alternative: Deny, Cancel | `outline-variant` border, `on-surface-variant` text |
| Text | Low-emphasis inline action: Enter address, Show all | `primary` text |
| Elevated | Only on busy or image backgrounds (the device viewer) | `surface-container-low`, `elevation-1` |
| Danger | Confirming a destructive dialog | `error` / `on-error` |

## Compose
`Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `ElevatedButton` with `shapes = ButtonDefaults.shapes()`. Sizes use `ButtonDefaults.contentPaddingFor(height)`, with `ButtonDefaults.MediumContainerHeight` and so on.

## Rules
- The label is a verb, in sentence case, 1 to 3 words. Never "OK" or "Yes".
- Put at most one filled button in a view. Two filled buttons side by side means one of them is wrong.
- M is the default size for full-width actions on phone screens (pairing, the empty state). S is the default inside cards.
- Disabled buttons explain themselves: tapping one shows a tooltip saying why ("Connect to your computer first").
- Haptic `CONFIRM` on commit actions, and `REJECT` on Deny, Delete and Stop.
