A tool row is one tool call, in one line: what it did, to what, and how it ended.

## Anatomy
- A 20 icon from the iconography table, then a sentence in `body-medium` with paths and commands in `code-small`, then trailing meta in `label-medium` tabular.
- Meta: line count, match count, `+adds −removes` in `diff-add-ink` / `diff-remove-ink`, exit code and duration, result count or size.
- Running: the meta slot shows a live duration or a 14dp loading shape. Done: meta only, since a plain row is success. Failed: the row fills `error-container`, with an `error` icon.

## Tap targets (details sheet)
| Tool | Details on tap |
| --- | --- |
| read | The file in a `CodeBlock` with line numbers, scrolled to the range |
| glob, grep | The file list, and matches with context |
| edit, patch, write | A `DiffView`, with "Open in Review" |
| shell | The command and full output in a terminal-styled `CodeBlock`, with Copy and Run again |
| websearch | Result cards (title, domain, snippet). Tapping one opens a Custom Tab |
| webfetch | Domain, title, and fetched text as markdown |
| browser.* | Action log with screenshots full width, zoomable |
| subagent | Opens the child chat (`SubagentCard`) |
| MCP | Server name, tool name, and pretty-printed JSON input and output |

## Data
- Built from `session.tool.called` (input), `session.tool.progress` (live output) and `session.tool.success` / `failed` (`content[]` of text and file parts).
- Streaming input (`session.tool.input.*`) fills the sentence as it arrives, so "Editing `redirect…`" appears before the call runs.

## Rules
- The sentence is always past tense when done and present participle when running.
- Paths are truncated from the middle, keeping the file name.
