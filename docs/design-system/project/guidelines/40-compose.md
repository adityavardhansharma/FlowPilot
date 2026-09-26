# Building it in Compose

This section maps every token and component in this system to Jetpack Compose. The names below were checked against the `androidx.compose.material3` source at 1.5.0-alpha. Expressive APIs need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

## Dependencies

| Library | Version | Why |
| --- | --- | --- |
| `androidx.compose.material3:material3` | 1.5.0-alpha (1.4.0 is the stable fallback) | Expressive components: ButtonGroup, SplitButton, FloatingToolbar, FAB menu, LoadingIndicator, SegmentedListItem, flexible app bars, ShortNavigationBar, WideNavigationRail |
| `androidx.compose.material3.adaptive:adaptive-navigation-suite` and `adaptive-layout` | latest | `NavigableListDetailPaneScaffold`, window size classes |
| `androidx.graphics:graphics-shapes` | 1.0+ | `RoundedPolygon` and `Morph`, which back `MaterialShapes` |
| `androidx.glance:glance-appwidget` + `glance-material3` | latest | Home screen widget (Phase 2) |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer-m3` | Streaming-friendly markdown with Compose nodes. Syntax highlighting comes from our own tokenizer, which is fed the `code-*` tokens |

Don't add `material-icons-extended`. Ship Material Symbols Rounded as vector drawables, generated for the icon list in the brand book.

## Theme

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowPilotTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FlowPilotDark      // generated from tokens.json, dark theme
        else -> FlowPilotLight
    }
    CompositionLocalProvider(LocalFlowPilotColors provides if (dark) CodeColorsDark else CodeColorsLight) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = FlowPilotShapes,   // extraSmall 4, small 8, medium 12, large 16, largeIncreased 20, extraLarge 28, extraLargeIncreased 32, extraExtraLarge 48
            typography = FlowPilotTypography,
            content = content,
        )
    }
}
```

- Generate `FlowPilotLight`/`Dark` and `CodeColors*` from `tokens.json` at build time with a small Gradle task, so the design system stays the single source.
- The code, diff and terminal tokens live in `LocalFlowPilotColors`, not in `ColorScheme`, and never change with dynamic color.
- `FlowPilotTypography` is `Typography()` with every style's `fontFamily` set to Google Sans Flex (a variable `Font` with `FontVariation.Settings`). The emphasized styles use the matching `*Emphasized` properties, one weight step up.

## Motion in code

- Read springs from the theme. Never write `tween()`.
  - Spatial: `MaterialTheme.motionScheme.fastSpatialSpec()`, `defaultSpatialSpec()` or `slowSpatialSpec()`.
  - Effects: `fastEffectsSpec()`, `defaultEffectsSpec()` or `slowEffectsSpec()`.
- Button press morphs come for free from `ButtonDefaults.shapes()` (the pressed shape) on `Button`, `IconButton` and `ToggleButton` in 1.5.
- For `animateItem()` in the feed, use `placementSpec = defaultSpatialSpec()` and `fadeInSpec = defaultEffectsSpec()`.
- For reduced motion, check `Settings.Global.ANIMATOR_DURATION_SCALE == 0`. Provide a `MotionScheme` whose specs are `snap()`.

## Component map

| FlowPilot component | Compose | Notes |
| --- | --- | --- |
| Button | `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton` with `ButtonDefaults.shapes()` | Sizes via `ButtonDefaults.MediumContainerHeight` etc. |
| ButtonGroup / AgentToggle | `ButtonGroup` with `ToggleButton`s and `ButtonGroupDefaults.connectedLeadingButtonShapes()` / middle / trailing | The selected segment goes round |
| IconButton | `IconButton`, `FilledTonalIconButton`, `IconToggleButton` with `IconButtonDefaults.shapes()` and widths narrow/uniform/wide | |
| SplitButton (Send) | `SplitButtonLayout` + `SplitButtonDefaults.LeadingButton` / `TrailingButton` | The trailing part opens `DropdownMenu` Send now / Queue |
| FAB / FAB menu | `MediumExtendedFloatingActionButton`; `FloatingActionButtonMenu` + `ToggleFloatingActionButton` + `FloatingActionButtonMenuItem` | The menu is used on Projects |
| Top app bar | `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `AppBarWithSearch` | Scroll behavior `exitUntilCollapsedScrollBehavior` |
| Navigation | `ShortNavigationBar` + `ShortNavigationBarItem`; `WideNavigationRail` at medium and up | Via `NavigationSuiteScaffold` |
| Search | `AppBarWithSearch` + `ExpandedFullScreenSearchBar` | |
| Tabs | `PrimaryTabRow` / `SecondaryTabRow` | |
| Lists | `SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)` | Home, Settings, pickers |
| Floating toolbar | `HorizontalFloatingToolbar` (vibrant colors for the device viewer) | |
| Loading | `LoadingIndicator`, `ContainedLoadingIndicator` | Never `CircularProgressIndicator` for indeterminate waits |
| Progress | `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` | Clone, uploads |
| Sheets | `ModalBottomSheet` | With a drag handle, and predictive back built in |
| Dialog | `AlertDialog` | |
| Snackbar | `SnackbarHost` + `Snackbar` | |
| Chips | `FilterChip`, `AssistChip`, `InputChip` | |
| Switch | `Switch` with thumb icon when checked | |
| Swipe | `SwipeToDismissBox` | |
| Pull to refresh | `PullToRefreshBox` with `PullToRefreshDefaults.LoadingIndicator` | |
| Shapes | `MaterialShapes.Cookie9Sided.toShape()` and friends | Project avatars |
| Tooltip | `TooltipBox` + `PlainTooltip` | Icon-only buttons |

Custom components (FlowPilot's own, built from `Surface`, `Row` and `Text`) are `ThreadRow`, `StatusPill`, `ServerChip`, `UserMessage`, `AssistantMessage`, `ReasoningRow`, `WorkGroup`, `ToolRow`, `CodeBlock`, `DiffView`, `PermissionCard`, `FormCard`, `ErrorCard`, `WorkingPill`, `JumpToLatest`, `SubagentCard`, `Composer`, `QueuedChip`, `TerminalView` and `DeviceViewer`. Each lives in `:core:designsystem` with a `@Preview` per state, and screenshot tests via Roborazzi.

## Performance rules for the chat feed

- Give every item a stable `key` (the message id plus part ordinal) and a `contentType`. Tool rows, text blocks and cards are separate content types.
- Keep streaming text in a `MutableState<String>` per block, appended in place. Only the last block recomposes on a delta. Batch deltas on the frame clock (`withFrameNanos`), never per event.
- Parse markdown only for the block that is still open. Closed blocks are cached as immutable `AnnotatedString`s.
- Target 60 fps on a 1,000-message chat. Measure it with Macrobenchmark and a baseline profile.
