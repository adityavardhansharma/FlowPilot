The top app bar names the current place and holds two or three contextual actions.

## Variants
- **Chat:** `MediumFlexibleTopAppBar`, 112 expanded and 64 collapsed. The title is `headline-medium-emphasized`, up to 2 lines expanded, crossfading to `title-large` in 1 line when collapsed. The subtitle is the project shape at 16, the project name, and the branch in `code-small`.
- **Project detail:** `LargeFlexibleTopAppBar`, 120 expanded.
- **Home:** `AppBarWithSearch`.
- **Settings pages:** the small `TopAppBar`, 64.

## Compose
Use `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` with `Modifier.nestedScroll`. The container is `surface` at rest and `surface-container` when scrolled (`scrolledContainerColor`).

## Rules
- At most two action icons plus overflow. Review is always the first action in a chat.
- Tapping the chat title opens rename. It shows no pencil icon, and the long-press tooltip says "Rename".
- Title text is the chat's own title. Never show a raw session id.
