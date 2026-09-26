The short navigation bar switches between the three top-level places: Chats, Projects and Inbox.

## Anatomy
- 64 tall on `surface-container`.
- Items have a 56×32 pill indicator in `secondary-container`. The selected icon is FILL 1 and its label turns `secondary`.
- The Inbox badge uses `tertiary` / `on-tertiary` (needs you) and shows the count up to 99+.

## Compose
`ShortNavigationBar` + `ShortNavigationBarItem` on compact width, and `WideNavigationRail` on medium and up, both through `NavigationSuiteScaffold`. The indicator animates width on `spring-fast-spatial`.

## Rules
- It hides on the chat screen and while the keyboard is open.
- Re-tapping the current destination scrolls it to the top and then clears the filters.
- Never add a fourth destination. Settings lives behind the server chip.
