A diff view shows a file change as a unified diff with line numbers.

## Anatomy
- The header shows the path (`code-small` `on-surface`) and `+n −n` counts.
- Hunk headers are `on-surface-variant`.
- Added lines: `diff-add-bg` / `diff-add-ink` with a `+` gutter sign. Removed lines: `diff-remove-bg` / `diff-remove-ink` with a `−` sign. The sign is always present, so meaning never rests on color alone.
- The gutter is a 28 line number column at 80% `on-surface-variant`.

## Variants
- **Inline** in a tool's details sheet: the first 3 hunks, then "Open in Review".
- **Review screen:** full file with sticky hunk headers, and a split view on expanded widths. A long-press on a line adds a comment that goes to the agent.

## Rules
- Never syntax-highlight inside diff lines. Add and remove colors win.
- Word-level changes get a stronger tint of the same token at 2× opacity.
