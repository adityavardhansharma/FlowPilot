An assistant message is the agent's answer: markdown set straight on the surface, streaming in place.

## Anatomy
- No bubble. `body-large` with a 26 line height on `surface`, full width, and a 640 maximum on tablets.
- Markdown: headings step down to `title-large` / `title-medium`. Lists have 8 between items. Inline code is `code-medium` on `surface-container-highest` with `radius-sm`. Links are `primary`, underlined. Tables scroll horizontally inside a `radius-lg` container.
- Fenced code renders as a `CodeBlock`.
- The streaming caret is a 10dp `primary` dot that breathes (scale 1 to 0.55). It's removed when the text ends.
- The stats line (model, tokens, cost, duration) is `label-medium` `on-surface-variant`. It fades in when the turn finishes.

## Streaming rules
- Append deltas in place, batched per frame. There's no per-token animation or fade. The block grows through layout, not a spring.
- Re-parse only the open block. An unclosed fence renders as code immediately.
- Keep the scroll anchored. See `JumpToLatest`.
- Announce the full text to TalkBack once, on `session.text.ended`.

## Actions
Long-press (or the overflow at the end of the turn): Copy, Copy as markdown, Share, Fork from here, Retry with another model.
