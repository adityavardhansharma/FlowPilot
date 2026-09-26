The model picker sheet chooses the model (and its reasoning variant) for the next message, showing only the models you've made visible.

## Anatomy
- A `ModalBottomSheet` on `surface-container-low`, opening at 60% height and expanding to full.
- A search field at the top. Then a Recent group (3 models), then one segmented group per provider in the order set in Settings.
- Rows: a provider mark (a 24 icon asset), the model name (`body-large`), and a supporting line with the context window, capabilities and price per million tokens. The selected row is `secondary-container` with a trailing `check`.
- The reasoning variant `ButtonGroup` appears under the selected model only when it has variants.
- The footer is "Manage models", which opens Settings → Models.

## Data
Models come from the provider endpoints of the server. Visibility is a client-side set in DataStore, keyed by `provider/model`, the same approach as OpenCode Desktop. Hidden models are only reachable through search, which shows "Hidden" on their rows.

## Rules
- Tapping a row selects it and closes the sheet (`CONFIRM` haptic). Changing the variant doesn't close it.
- The choice sticks per chat. New chats use the default model from Settings.
