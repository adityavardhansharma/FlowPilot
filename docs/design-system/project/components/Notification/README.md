Notifications reach you when the app isn't showing the chat: when the agent needs you, and when it's done.

## Channels
| Channel | Importance | Content | Actions |
| --- | --- | --- | --- |
| Needs you | High (heads-up) | The approval question and the chat title | Allow once, Deny, Open. A question gets inline reply |
| Finished | Default | "Done: 14 files changed" and the chat title | Open, Reply (inline) |
| Working | Low, ongoing | The current action and a live duration | Stop |

## Android 16
The working notification uses `Notification.ProgressStyle`, with one segment per tool call, and promotes to a Live Update chip in the status bar ("Working · 0:42").

## Rules
- Group by chat. Collapse older notifications into a summary.
- Tapping deep-links to the exact card.
- The small icon is the monochrome launcher shape.
