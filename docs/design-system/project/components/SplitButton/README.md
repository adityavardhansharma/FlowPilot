The split button sends a message while the agent is working, choosing between steering now and queueing for later.

## Anatomy
- The leading button holds the icon and label. The trailing button holds a chevron that rotates 180° when the menu is open.
- There's a 2dp gap between them, and the inner corners are `radius-sm`. When the menu opens, the trailing part goes fully round.
- The menu is a `DropdownMenu` on `surface-container-high`, with two items that each carry a supporting line.

## Compose
`SplitButtonLayout(leadingButton = { SplitButtonDefaults.LeadingButton(...) }, trailingButton = { SplitButtonDefaults.TrailingButton(checked = expanded, ...) })`.

## Behavior
- It appears only while a turn is running. When idle, the composer shows the plain send button.
- The leading action follows the "Delivery default" setting (steer by default). A long-press on the send button also opens the menu.
- In the composer it collapses to the 48 send circle plus a 24-wide chevron to save space. The same anatomy is also used for "New project" on the Projects tab.
