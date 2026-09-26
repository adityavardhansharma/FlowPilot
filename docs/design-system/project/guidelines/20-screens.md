# Screen specs

Measurements are dp. Phone specs assume a compact width of 360–412dp, and tablet notes follow each one. Every screen draws edge to edge and pads by `WindowInsets.safeDrawing`.

## Home (Chats tab)

| Region | Spec |
| --- | --- |
| Top app bar | `AppBarWithSearch` 64 tall. Leading `ServerChip`, then a search field on `surface-container-high` with `radius-full`, then a trailing avatar icon button that opens Settings. |
| Filter row | `FilterChip`s scrolling horizontally: All, Needs you, Working, Pinned, then one chip per project with its shape. 8 gap, 16 side margin. |
| List | Segmented groups per time bucket. Group header in `title-small` `on-surface-variant`, 24 top and 8 bottom. Rows are 72 tall with a 2 gap. |
| FAB | Bottom-right, 16 from the edges and above the nav bar. Extended when idle, collapses on scroll. |
| Nav | `ShortNavigationBar` 64: Chats `chat_bubble`, Projects `folder`, Inbox `inbox` with a `tertiary` badge. |

Tablet: list-detail. The list pane is 360 wide and the chat fills the rest. The FAB moves into the rail's header.

## Projects tab

- **Grid** on phone: 2 columns of project cards, 20 radius.
  - Each card shows its project shape at 48, the name in `title-medium`, and the path in `code-small`, truncated in the middle ("~/code/…/flowpilot").
  - A footer line shows the chat count and last activity.
  - A git branch pill appears when `vcs` reports one.
- **Top actions:** a `SplitButton` "New project". The leading part creates a folder. The trailing arrow offers Clone repository and Open existing folder.
- **Project detail** uses a `LargeFlexibleTopAppBar`: the name in `headline-medium-emphasized` and the path as the subtitle, collapsing to a small bar on scroll. It has tabs (`PrimaryTabRow`): Chats, Files, Changes.

## Inbox tab

- A list of every pending approval and question across chats, oldest first, each as a compact `PermissionCard` with the chat title above it.
- Answering removes the card with a height collapse on `spring-default-spatial`.
- Empty state: "Nothing needs you. The agents will ask here when they do."

## Chat

| Region | Spec |
| --- | --- |
| Top app bar | `MediumFlexibleTopAppBar` 112 that collapses to 64. Title: the chat title in `headline-medium-emphasized`, collapsing to `title-large`. Subtitle: project name and branch. Actions: Review (`difference`, badged with the changed file count) and the overflow menu. |
| Feed | A `LazyColumn` of turns (`reverseLayout = false`, anchored to the bottom by our own scroll controller). 16 side margins and 20 between turns. User bubbles align end with a max width of 85%. Assistant content is full width. |
| Working pill | Floats 12 above the composer, centered: `LoadingIndicator` at 20, then "Working · 0:42", tabular. It is a polite live region. |
| Jump to latest | A 40-tall pill on `elevation-2` at the bottom-right above the composer, showing the unread count. |
| Composer | Docked at the bottom above the IME, with 8 around and a `radius-xl` container. The field is on top. The toolbar row below it holds `+` attach, the agent toggle, the model chip, a spacer and send. |

Tablet (expanded): the supporting pane on the right, 400 wide, has three tabs: Review (the diff list), Files and Device.

## Model picker sheet

- A `ModalBottomSheet` that opens at 60% and expands to full height.
- The drag handle is 32×4. The search field is 56 tall.
- Section headers are `label-large` `on-surface-variant`.
- Rows are `SegmentedListItem`s, 56 tall:
  - Leading: the provider logo at 24.
  - Headline: the model name.
  - Supporting text: the context window and cost per million tokens.
  - Trailing: the selected check.
- A sticky footer row reads "Manage models" and opens Settings → Models.

## Settings

- The root is a segmented list in groups:
  - **Computer:** connected server, pair another, and switch between them.
  - **Models:** visibility and defaults.
  - **Chat:** default agent, delivery default, auto-scroll, show reasoning.
  - **Appearance:** theme, dynamic color, text size preview.
  - **Notifications.**
  - **Device:** adb path, stream quality.
  - **About.**
- **Model visibility:** one `SegmentedListItem` per model with a trailing `Switch`, grouped by provider. The provider header carries a "Show all / Hide all" text button. The search field filters. Changes save instantly. There is no Save button.

## Review (changes)

- The file list comes from `vcs`: status letter pill (M, A, D), path in `code-small`, then `+12 −3` in tabular `label-medium`, in `diff-add-ink` and `diff-remove-ink`.
- Tapping a file opens `DiffView` full screen with unified diffs, and hunk headers stick while scrolling.
- The bottom `HorizontalFloatingToolbar` offers Previous file, Next file, Copy path, and "Ask about this", which quotes the hunk into the composer.
- Commit is a Phase 2 action: a `FlexibleBottomAppBar` with a message field and a Commit button. It runs `git commit` through the shell.

## Terminal (Phase 2)

- Full screen on `code-bg`, in Google Sans Code 13/20.
- An extra-keys row sits above the IME: Esc, Tab, Ctrl, Alt, the arrow keys, `|`, `/` and `~`. Each is a 40-tall tonal key, `radius-md`.
- The prompt color is `terminal-prompt`.

## Device viewer (Phase 2 tier 1, Phase 3 tier 2)

- The device frame is centered with `radius-xl-increased` and letterboxed on `surface-container-lowest`.
- The top bar shows the device name and a refresh interval chip (1s, 2s, 5s, Paused).
- A `HorizontalFloatingToolbar` (vibrant) offers Home, Back, Recents, Rotate, Screenshot to chat and Type text.
- A tap on the frame sends `adb shell input tap x y`, mapped from frame coordinates. A 300ms ripple marks the touch point.
