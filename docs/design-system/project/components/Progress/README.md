Progress indicators show how far along a known-length task is (wavy) or that a short wait is happening (the morphing loading indicator).

## Choose
- **LinearWavyProgressIndicator:** determinate work with a visible end, such as cloning, uploading an attachment, or downloading a screenshot. The wave amplitude drops to flat at 100%.
- **LoadingIndicator:** indeterminate waits under 10s, such as a page load, pairing, or history paging.
- **CircularWavyProgressIndicator:** a determinate ring around a thumbnail (upload).

## Compose
`LinearWavyProgressIndicator(progress = { p })`, `LoadingIndicator()` and `ContainedLoadingIndicator()`.

## Rules
- The active track is `primary`, the inactive track is `secondary-container`, and the stop dot is `primary`.
- Never show a percentage you don't have. Stay indeterminate until git reports progress.
