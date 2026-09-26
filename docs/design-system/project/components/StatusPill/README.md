A status pill states what a chat or turn is doing right now, in words, an icon and a tone.

## Anatomy
24 tall, `radius-full`, `label-medium` with a 16 icon, and padding of 8 at the start and 10 at the end.

| Status | Tone | Icon |
| --- | --- | --- |
| Working | `primary-container` | A 12dp morphing loading shape |
| Needs you | `tertiary-container` | `front_hand` |
| Failed | `error-container` | `error` |
| Done, Queued, Stopped | `surface-container-highest` | `check_circle`, `schedule`, `stop_circle` |

## Rules
- Only these six statuses exist. Don't add colors for other states.
- Duration text is tabular and updates once a second. There's no animation on the digits.
- Tone changes crossfade on `spring-default-effects`. The width changes on `spring-fast-spatial`.
