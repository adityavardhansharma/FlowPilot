FlowPilot is a native Android app for driving OpenCode agents from your phone. It is built in Jetpack Compose on **Material 3 Expressive** (`androidx.compose.material3` 1.5, `MaterialExpressiveTheme`, `MotionScheme.expressive()`). This book is the source of truth for every screen. The sections after it cover UX principles, flows, screen specs, states, and the Compose mapping.

## What FlowPilot should feel like

A calm cockpit with loud moments. Most of the time you are reading: long markdown answers, tool logs, diffs. That surface stays quiet, with neutral containers, one accent and generous line height. Expressive energy (shape morphs, springs, bold color) is spent only where something **happens**: you send, the agent starts working, it needs you, it finishes, something fails. If everything bounces, nothing does.

Five rules decide every call:

1. **Status is shape and word, never color alone.** Working, needs you and failed each have a color, an icon, a label and (where there is room) a shape.
2. **Stream, don't jump.** Text grows in place, the list never scrolls under your thumb, and layout changes animate on spatial springs.
3. **Show the work, fold the noise.** Tool calls collapse into one line that says what is happening now. Details are one tap away, never zero taps.
4. **Thumb first.** Every primary action sits in the bottom 40% of the screen: composer, FAB, approval buttons, floating toolbar.
5. **Never lose the user's words.** Drafts persist per chat, queued messages stay visible, and a failed send keeps the text in the composer.

## Content fundamentals

- **Voice.** Plain, short and specific. Talk like a senior teammate, not a bot. Never use "Oops", "Uh-oh" or exclamation marks. No emoji anywhere in UI copy.
- **Casing.** Sentence case everywhere: buttons, titles, menu items, settings. The only capitals are proper nouns (OpenCode, GitHub, Tailscale) and model names as providers write them (Claude Opus 5, GPT-6 Luna).
- **Person.** Address the user as "you". Call the agent "the agent", or the agent's name when it matters (Build, Plan). Never "I" in system copy.
- **Nouns people know.** Say *chat*, not session. Say *project*, not location or worktree root. Say *computer*, not server, in onboarding (Settings may say "server"). Say *approval*, not permission request.
- **Buttons are verbs.** Allow once, Always allow, Deny, Send, Stop, Clone, Scan QR code. A button never says OK or Yes when it can name its action.
- **Numbers.** Use relative times in lists (2m, 3h, Yesterday, Sep 24) and absolute times in detail views. Token counts are rounded (12.4k). Costs use two decimals ($0.42). Durations read 0:42, then 12m.
- **Errors say what happened and what to do.** "Can't reach your computer. Check that OpenCode is running, then retry." Never "Something went wrong."

Real copy examples:

| Moment | Copy |
| --- | --- |
| Working pill | Working · 0:42 |
| Work group, running | Reading `src/auth/redirect.kt`… |
| Work group, done | Explored 6 files, ran 2 commands |
| Approval card title | Run `npm test`? |
| Approval card body | The agent wants to run a shell command in **flowpilot-app**. |
| Queued chip | Queued · sends when the agent finishes |
| Empty Home | No chats yet. Start one on any project on your computer. |
| Offline | You're offline. Showing saved chats. |
| Pair success | Connected to MacBook Pro · OpenCode 2.0.18 |

## Color

- **Dynamic color is on by default.** On Android 12+ the app uses the user's wallpaper scheme (`dynamicLightColorScheme`/`dynamicDarkColorScheme`). The FlowPilot scheme in this system is the fallback, and it's an option in Settings (Appearance → Use FlowPilot colors). Every rule below is written in *roles*, so it holds for both.
- **Brand scheme.** The seed is jade `#0f7b6c`, run through Material Tonal Spot on the 2025 (Expressive) color spec. `tertiary` is replaced with an amber palette (seed `#e0930b`, Vibrant), so the product has three meaning colors that differ in both hue and lightness:
  - `primary` (jade): the agent is working, your selection, send.
  - `tertiary` (amber): **the agent needs you**. Use it only for approvals, questions and pending badges.
  - `error`: failed, destructive.
- **Surfaces carry hierarchy, not shadows.**
  - Screen: `surface`.
  - Assistant cards and list groups: `surface-container-low`.
  - Work groups, sheets and the nav bar: `surface-container`.
  - Composer, menus and search: `surface-container-high`.
  - Selected and pressed rows, text-field fills: `surface-container-highest`.
- **Text.** Use `on-surface` for primary text and `on-surface-variant` for secondary text, on any surface or surface-container token (every pair is at least 4.5:1 in both themes).
- **User bubbles** are `primary-container` / `on-primary-container`. Assistant text sits directly on `surface`, with no bubble. That asymmetry is the main way you tell the two apart.
- **Code and diffs** use the product tokens `code-bg`, `code-ink`, `code-keyword`, `code-string`, `code-number`, `code-comment`, `code-function`, `diff-add-bg`/`diff-add-ink` and `diff-remove-bg`/`diff-remove-ink`. These stay fixed under dynamic color, because syntax colors must not shift with the wallpaper. Every ink is 4.5:1 or better on its background in both themes.
- **Borders.** Use `outline` for borders that carry meaning (outlined buttons, focused fields, 3:1). Use `outline-variant` only for hairlines and dividers.
- **Focus ring.** A solid 3px `primary` ring with a 2px offset. It is at least 3:1 on every surface.
- **Scrim.** `scrim` at the `scrim` opacity (0.32) behind modal sheets and dialogs.

## Typography

- **Families.** Set every UI string in **Google Sans Flex**, the variable face Material 3 Expressive is designed around. It is open source, and we bundle it as a variable TTF in `res/font`. Set code, paths, diffs and terminal text in **Google Sans Code**.
- **Scale.** Use the Material type scale exactly as tokenized: `display-*`, `headline-*`, `title-*`, `body-*`, `label-*`.
- **Chat reading size.** Assistant and user message text is `body-large` (16/24). Assistant paragraphs get 26px line height for long reads, set in the renderer.
- **Emphasized styles are the Expressive signal.** They're one weight step up. Use them for the single most important string on a surface:
  - `title-medium-emphasized` for the decision in an approval card.
  - `headline-medium-emphasized` for the expanded chat title.
  - `label-large-emphasized` for the primary segment in a button group.
  - Never more than one emphasized style per card.
- **Roundness axis.** Google Sans Flex's `ROND` axis goes up to 100 on project avatars, badges and the cover wordmark. Body text stays at `ROND` 0.
- **Numerals.** Use tabular numerals (`fontFeatureSettings = "tnum"`) for timers, token counts, diff counts and costs.
- **Measure.** Assistant text is capped at 640dp wide on tablets. On phones it runs to the 16dp margins.

## Shape

- **Corner scale.** Use the Material corner scale including the Expressive "increased" steps: `radius-xs` 4, `radius-sm` 8, `radius-md` 12, `radius-lg` 16, `radius-lg-increased` 20, `radius-xl` 28, `radius-xl-increased` 32, `radius-xxl` 48, `radius-full`.
- **Buttons rest round and press square.** Buttons, icon buttons and the send button use `radius-full` at rest. On press they morph to the pressed radius (`radius-sm` for S, `radius-md` for M, `radius-lg` for L and XL) on `spring-fast-spatial`. That squish is the app's tactile signature, so never turn it off.
- **Connected button groups.**
  - Inner corners are `radius-sm` with 2px gaps.
  - The selected segment goes fully round.
  - This is the Build/Plan agent toggle and every two-to-four-way choice.
- **Segmented lists.**
  - Rows are separated by 2px gaps, not dividers.
  - The group's outer corners are `radius-lg-increased` and inner corners are `radius-xs`.
  - Home, Settings and the model picker all use segmented lists.
- **Material shapes library** (`MaterialShapes`, 35 shapes). Use it sparingly:
  - **Project avatars:** each project gets one shape and one tone, derived from its id, from this set: Cookie9Sided, Cookie6Sided, Clover4Leaf, Pentagon, Gem, Sunny, Pill, Arch, Flower. They hold the project's initial in `title-medium` with `ROND` 100.
  - **Loading indicator:** the stock `LoadingIndicator` morphs through its shape sequence.
  - **Pairing success:** the check sits in a Cookie12Sided that morphs to a Circle once.
  - Nothing else uses decorative shapes.
- **Containers.** Cards use `radius-lg-increased`, sheets and dialogs `radius-xl`, the composer `radius-xl`, code blocks `radius-lg`.

## Motion

Motion is physics, not easing curves. Use `MaterialTheme.motionScheme` (expressive) everywhere, and never hard-code `tween` durations.

| Token | Spring | Use it for |
| --- | --- | --- |
| `spring-fast-spatial` | damping 0.6, stiffness 800 (~350ms, visible overshoot) | Press squish, switch thumb, send↔stop morph, chip select |
| `spring-default-spatial` | damping 0.8, stiffness 380 (~410ms) | Work group expand/collapse, message insertion, FAB menu, sheet snap |
| `spring-slow-spatial` | damping 0.8, stiffness 200 (~570ms) | Pane resize, first-run hero |
| `spring-fast-effects` | damping 1, stiffness 3800 | State layers, icon crossfades |
| `spring-default-effects` | damping 1, stiffness 1600 | Color and alpha changes, pill color swaps |
| `spring-slow-effects` | damping 1, stiffness 800 | Screen crossfades |

- **Spatial vs effects.** Spatial springs (with bounce) are for anything that changes position, size or shape. Effects springs (no bounce) are for color and opacity. Never bounce a color.
- **Choreography.**
  - **Send:** the send button squishes, the user bubble slides up from the composer (`spring-default-spatial`), the send icon morphs into stop (square `radius-md` on `inverse`-style `on-surface` fill), and the working pill fades in.
  - **Finish:** the stop button morphs back and a single light haptic plays.
  - **New assistant text** does not animate per token. The caret breathes, and the block grows as layout, with no spring. Springs are only for insertions of whole items (a new tool row, a card).
- **Reduced motion.** When animations are off, springs become `snap()` and the loading indicator becomes a static shape with a determinate label. Nothing important relies on motion.
- **Predictive back.** Sheets, the chat screen and pickers follow the system predictive-back gesture (scale 0.9 and a corner increase while dragging).

## Elevation

Elevation is tonal. Shadows appear only on things that float over content: `elevation-2` for the floating toolbar and the jump-to-latest pill, and `elevation-3` for the FAB, FAB menu and snackbar. Cards, sheets and app bars never cast shadows. When the chat scrolls under the top app bar, the bar fills with `surface-container`.

## Layout

- **Window size classes.**
  - Compact (<600dp) is a single pane with 16dp margins.
  - Medium (600–840dp) is list-detail with 24dp margins.
  - Expanded (≥840dp) is a list-detail-supporting pane: chats | chat | Review/Files/Device.
  - Use `NavigableListDetailPaneScaffold`.
- **Edge to edge.** Draw behind the system bars. Pad content with `WindowInsets.safeDrawing`. The composer rides the IME with `imePadding()` and `WindowInsets.ime` animation, so it never jumps.
- **Touch.** 48dp minimum targets (`touch-target`), even for 32dp visuals. Leave 8dp between adjacent targets.
- **Spacing.** A 4dp grid. Use `space-4` (16) for phone margins and card padding, `space-3` (12) between feed items, `space-5` (20) between chat turns, and `space-12` (48) of breathing room above the composer.
- **Navigation.**
  - Compact: a short navigation bar with three destinations: **Chats**, **Projects**, **Inbox** (approvals and questions across all chats, badged in `tertiary`).
  - Medium and up: a wide navigation rail.
  - Settings opens from the server chip in the top app bar.
  - Opening a chat hides the nav bar.

## Iconography

- **Icon set.** Use **Material Symbols Rounded** (variable), downloaded as vector drawables for exactly the icons we use (the `material-icons` library is deprecated as of material3 1.4).
- **Size and axes.** Icons are 24dp at weight 400. `opsz` matches the size (20 or 24). In dark theme use `GRAD` -25 so icons don't glow.
- **Selected state is FILL 1**: nav items, toggled icon buttons, the starred model. Unselected is FILL 0.
- **Tool icons are fixed:**

| Tool | Symbol |
| --- | --- |
| read, glob, grep | `description`, `manage_search`, `search` |
| edit, patch, write | `edit_note`, `difference`, `note_add` |
| shell | `terminal` |
| websearch | `travel_explore` |
| webfetch | `language` |
| browser.* | `web` (screenshots: `screenshot_monitor`) |
| subagent | `smart_toy` |
| skill | `auto_awesome` |
| question / form | `help` |
| MCP / unknown | `extension` |
| device | `phone_android` |

- **App-level icons:**
  - Status: working uses the LoadingIndicator, needs-you `front_hand`, failed `error`, done `check_circle`.
  - Actions: send `arrow_upward`, stop `stop`, queue `schedule_send`, attach `add`, model `neurology`, agent Build `construction` / Plan `map`, new chat `edit_square`, project `folder_open`, clone `download`, pair `qr_code_scanner`.
- **No emoji** as icons or decoration, anywhere.
- **Logo.** There is no FlowPilot logo yet. Set the name in Google Sans Flex, weight 600, `ROND` 100. The launcher icon concept is a jade Cookie9Sided holding a white upward arrow (the send glyph). It's a placeholder until a real mark exists.

## Haptics

| Moment | HapticFeedbackConstants |
| --- | --- |
| Send, approve, clone started | `CONFIRM` |
| Deny, delete, stop | `REJECT` |
| Toggling a segment, switch, chip | `SEGMENT_TICK` |
| Long-press menus, drag start | `LONG_PRESS` / `DRAG_START` |
| Turn finished (app foreground) | `GESTURE_END` (light) |
| Streaming tokens, scrolling | none, ever |

## Accessibility

- Every status has an icon and a word as well as a color.
- Contrast is at least 4.5:1 for text and 3:1 for icons and borders in both themes (checked for every token pair above).
- Everything scales to 200% font size without clipping. Rows grow, and pills wrap to a second line.
- TalkBack:
  - Each tool row reads as one node ("Edited App.kt, 12 lines added, 3 removed, done").
  - Streaming text is announced when it finishes, not per token (`LiveRegionMode.Polite` on the finished message).
  - The working pill is a polite live region.
- Hardware keyboards:
  - Enter sends, Shift+Enter adds a newline.
  - Ctrl+K opens the command palette on tablets.
  - Esc stops a running turn after confirmation.
