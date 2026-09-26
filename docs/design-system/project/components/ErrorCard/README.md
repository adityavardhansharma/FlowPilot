An error card sits where something failed, says what happened in plain words, and offers the fix.

## Anatomy
`error-container` / `on-error-container`, `radius-lg-increased`, with an `error` icon, a `title-medium` cause, a `body-medium` explanation, and one or two actions.

## Rules
- Map known server errors to human copy (auth, rate limit, context overflow, provider down, tool crashed). Unknown errors show the server message verbatim under "Details".
- Retry re-sends the same prompt. The composer is left alone.
- Never stack two error cards. A repeat failure updates the same card's count ("Failed 2 times").
