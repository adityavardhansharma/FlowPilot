The permission card asks you to approve one action the agent wants to take, showing exactly what will happen.

## Anatomy
- `tertiary-container` / `on-tertiary-container` (amber means "needs you"), `radius-lg-increased`, 16 padding.
- An overline with `front_hand` and "Approval needed · shell" (`label-medium`).
- The question as the emphasized title (`title-medium-emphasized`): "Run `npm test`?", "Edit `redirect.ts`?", "Fetch `docs.github.com`?".
- A one-line body saying where it will happen.
- The payload: a command in a `CodeBlock`, an edit as an inline `DiffView`, or a URL and domain.
- Three buttons, left to right: **Allow once** (filled, primary), **Always allow** (a tonal fill of `on-tertiary-container` at 12%), **Deny** (outlined, in the card's ink). On narrow screens or at large font sizes they wrap to a column. They are separate buttons, not a connected group.

## Behavior
- The reply is `{decision: "once" | "always" | "reject"}`. The card collapses in place (`spring-default-spatial`) to a one-line receipt.
- "Always allow" shows the rule it creates underneath ("Allows `npm test*` in this project"), with Undo in a snackbar.
- The same card renders in Inbox and in the notification (Allow once and Deny actions, with Always allow in the app only).
- Haptics: `CONFIRM` on allow, `REJECT` on deny.
- It is never auto-dismissed or timed. If the server resolves it elsewhere, it collapses with "Answered on your computer".
