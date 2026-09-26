Tabs switch between views of the same object, such as a project's Chats, Files and Changes.

## Compose
`PrimaryTabRow` with `TabRowDefaults.PrimaryIndicator` (a 3dp, content-width, rounded-top indicator). Use `SecondaryTabRow` for sub-views inside a pane (Review: Unified or Split).

## Rules
- Two to four tabs, labels only, one word each where possible.
- Counts sit in a neutral badge, not the error badge.
- Swiping between tabs with `HorizontalPager` is allowed only when no tab has horizontal scroll content (code). Otherwise tabs are tap-only.
