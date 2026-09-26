Search finds any chat by title or message text, and it is the Home screen's top bar.

## Anatomy
- A 48-tall field on `surface-container-high`, `radius-full`, with a leading `search` icon and the placeholder "Search chats".
- Expanded, it becomes a full-screen search with recent searches and live results grouped by project, with matches bolded.

## Compose
`AppBarWithSearch` + `ExpandedFullScreenSearchBar` with a `rememberSearchBarState()`.

## Rules
- Titles search on the server list. Message text searches the local Room FTS cache, and an info row says "Searching saved messages on this phone".
- Results appear after 150ms of idle typing. Never block on the network.
