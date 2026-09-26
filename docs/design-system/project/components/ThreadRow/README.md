A thread row is one chat in a list: where it runs, what it's called, what happened last, and whether it needs you.

## Anatomy (72 tall, segmented)
- **Leading:** a `ProjectShape` at 40.
- **Headline:** the title in `body-large`, one line, ellipsized. Unread titles are weight 600.
- **Supporting line:** in `body-medium` `on-surface-variant`. It shows:
  - the last assistant line, or
  - the live action (shimmering) while working, or
  - the pending question when it needs you.
- **Trailing:** a relative time in `label-medium` tabular, and below it a `StatusPill` or unread dot.

## States
Idle, unread (bold with a `primary` dot), working (jade pill with a live line), needs you (amber pill, and the row moves to the "Needs you" group), failed (red pill), pinned (`push_pin` after the time), and selected on tablet (`secondary-container`).

## Compose
`SegmentedListItem(onClick, shapes = ListItemDefaults.segmentedShapes(index, count), leadingContent, supportingContent, trailingContent)` inside `LazyColumn` with `key = session.id` and `Modifier.animateItem()`.

## Gestures
- Swipe end to pin and start to archive (`SwipeToDismissBox`). The background reveal shows the icon and word.
- Long-press opens a menu: Rename, Move to project, Fork, Copy link, Archive, Delete.

## TalkBack
The row reads as one node: "Fix OAuth redirect loop, flowpilot-app, needs you: approval to run npm test, 2 minutes ago".
