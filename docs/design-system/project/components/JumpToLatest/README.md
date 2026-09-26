Jump to latest appears when you've scrolled away from the live end of a chat, and brings you back.

## Behavior
- It appears when the user is more than 48dp above the bottom. It shows a count of new items since then ("3 new"), or just the arrow when nothing is new.
- Tapping it scrolls on `spring-default-spatial`, or jumps and then settles when it's more than 3 screens away. Auto-follow resumes.
- It sits at the bottom-end, 12 above the composer, on `surface-container-high` with `elevation-2`. The pill enters and leaves with a scale from 0.8 on `spring-fast-spatial`.
