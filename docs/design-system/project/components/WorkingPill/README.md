The working pill floats above the composer and tells you, at a glance, that the agent is busy and for how long.

## Anatomy
- 36 tall, `primary-container`, with a 20dp `LoadingIndicator` in `on-primary-container`, then "Working · 0:42" in tabular `label-large`.
- When anything waits on you, it becomes the amber "Needs you · 1 approval". Tapping it scrolls to the card.
- The standalone `LoadingIndicator` is 48 contained and 38 bare. It's used for page loads, never for the turn state.

## Compose
`LoadingIndicator(color = onPrimaryContainer, modifier = Modifier.size(20.dp))` and `ContainedLoadingIndicator()`. Never use `CircularProgressIndicator` for indeterminate waits.

## Rules
- The pill fades in 400ms after send, so fast turns never flash it, and it leaves on finish.
- It's a polite live region. TalkBack hears "Agent working" once, and then "Agent finished".
