# Connection and data reliability implementation

This implements the architecture/reliability follow-up to the [27 September review](architecture-review-2026-09-27.md). Product milestones M4–M8 remain out of scope, as requested.

## Behavior changes

| Review finding | Implementation |
| --- | --- |
| Stale chat refreshes overwrite SSE | One connection-owned `SessionRepository` per opened session; recovery generations cancel obsolete work; durable live events and local commands are overlaid on fetched history. Auxiliary reads do not block transcript publication. |
| Reconnect only fetches the latest page | Replay resumes from the saved applied sequence. Durable events are deduplicated. Missing/expired/unavailable logs fall back to REST paging through the previously cached window, including multi-page gaps. A failed recovery retains saved data and keeps the chat in catch-up state. |
| Failed reads erase pending requests | Permissions/forms retain last successful values. Pending refresh merges successful locations only, with revisions protecting ask/reply events and bounded request concurrency. Inbox displays refresh errors alongside retained requests. |
| Slow screens block streaming | Network callbacks use bounded nonblocking sends. Overflow explicitly invalidates synchronization. Home invalidation scheduling is separate from ingestion; screens observe repository state. |
| Shallow/racy JSON cache | Room stores message rows plus session state/checkpoint in a transaction, all loaded Home/chat pages, pending requests, and resumable clone job IDs. Existing JSON Home/chat snapshots migrate on read. Schema v1 is checked in. |
| Token-by-token UI work | Delta builders combine updates before reduction; stream presentation is published on a roughly 16 ms schedule off Main. No idle frame timer runs when nothing changes. Settled Markdown blocks and unchanged feed rows are reused; parsing/feed construction run off Main. |
| Repeated catalogs/serial fetches | Projects, active sessions, models, agents and defaults have shared single-flight caches; location-scoped catalogs retain separate keys. Session metadata/permissions/forms/inbox/status reads run concurrently after the transcript is visible. |
| Retry/lifecycle ambiguity | Explicit offline/connecting/catching-up/live/auth-required/unsupported states; jittered capped backoff; auth failures stop retrying; network/foreground triggers coordinate recovery. Server version is rechecked after connection. Foreground exit suspends streaming and recovery. |
| Unconfirmed writes/queue edit | Prompt response IDs reconcile optimistic messages. A lost response is described as unconfirmed, with no automatic mutation replay. Queue editing waits for cancellation success. Model/agent/permission/form actions update confirmed state after success. |
| Draft/navigation races | Drafts use server-scoped keys, migrate legacy drafts to the active server once, respect typing during hydration, and flush on ViewModel disposal. Search and folder browsing cancel obsolete requests and validate the current request. Re-pairing preserves the saved server identity; UI instances use a separate connection identity. |
| Cancellation/body leaks | Cancellation exceptions propagate; full-body HTTP reads remain owned by the cancellable OkHttp call; HTTP calls have a total deadline; error/undelivered responses close. Transport retries are restricted to explicit read retries. |
| Clone progress and filesystem risk | Removed the fixed sentinel file. Progress keeps an 8 KiB tail, drains remaining output after exit, and persists shell job identity to reattach after navigation or restart. |
| Diagnostics and trust | A 128-entry numerical diagnostics buffer records transport/sync timing and overflow without credentials, URLs, prompts or tool output. Settings can copy it. User-installed CA trust is restricted to debug builds. Cleartext remains supported for existing LAN endpoints. |
| Verification gates | CI now runs app repository tests, lint, and instrumentation compilation as well as core tests/APK build. New deterministic sync/transport tests and Android database/workload tests are included. |

## Ownership

`ServerConnection` owns transport, `HomeRepository`, `Pending`, shared catalogs, and opened-session repositories. ViewModels own presentation and user commands. Repositories retain state while navigating and expose observable snapshots; Room supplies restart/offline history and transactionally couples settled state to replay checkpoints. In-flight text remains an in-memory overlay. Repositories are disposed with the connection, and streams/recovery pause when the app backgrounds.

A replay and a REST snapshot do not share an atomic server revision. During catch-up, the client buffers durable live events and omits buffered ephemeral deltas that the REST snapshot might already contain. Authoritative `*.ended` events repair the tail. This deliberately avoids duplicated token text during recovery; temporary tail staleness is preferable to fabricated output. State remains visible throughout recovery.

## Verification commands

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew \
  :core:test :app:testDebugUnitTest :app:connectedDebugAndroidTest \
  :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The Android instrumentation suite runs on an API 35 x86_64 emulator. It checks 1,000-message persistence after database reopen, transaction consistency under concurrent writes, isolation when clearing servers, Home pages beyond the initial 60, and a repeatable Markdown/feed CPU workload. The workload logs full-parser versus incremental-parser time; it is not a Compose frame-rate benchmark. One API 35 emulator run measured 2,539 ms for full parsing versus 53 ms for incremental parsing across 100 updates over 200 settled paragraphs, with identical output and matching 1,000-message feed rows. These are synthetic debug-build CPU measurements, not phone latency or frame-rate claims.

Tests exercise delayed snapshots, stale connection responses, repeated replay, unavailable logs with multi-page gaps, buffer overflow, batching, authoritative final text, canceled body reads, authentication rejection, failed pending refreshes, replies racing refreshes, cached history restart, single-flight resources, Markdown prefix equivalence and unchanged feed identity.

## Recorded validation

- Core: 41 passed, 2 live-server tests skipped (43 discovered).
- App repository tests: 3 passed.
- Android API 35 instrumentation: 5 passed, including legacy migration and the synthetic streaming workload.
- Android lint: no errors; 15 warnings and 1 hint remain (including existing LAN cleartext policy, UI conventions, and resource/deprecation items).
- Debug and minified release APK builds passed. The minified release APK also installed and cold-launched successfully on the API 35 emulator with no recorded crash.

## Operational limits and next validation

- No live OpenCode server credentials were supplied, so the two existing live-server tests remain skipped. Validate replay and shell contracts against the deployed v2 server, especially experimental-log behavior, over LAN and Tailscale before release.
- No background service or locked-phone notification delivery is promised. That is M6 product work; this change deliberately pauses foreground-only syncing when backgrounded.
- No prompt/shell idempotency contract is assumed. An unconfirmed mutation requires checking server state before a manual retry.
- Cleartext HTTP remains a LAN compatibility option. Use HTTPS or an encrypted tunnel for remote connections. Release builds no longer globally trust user-installed certificate authorities.
- The 16 ms publication schedule is a batching target, not a guaranteed 60 fps result. Device-level Compose Macrobenchmark, battery/network-handoff testing, and release-device latency targets still require a representative phone and live server. Emulator CPU timings must not be presented as phone frame rates.
- Opened session repositories remain cached for the active connection to keep navigation and event ownership consistent. Large-session retention should be profiled during the whole-day workload before defining an eviction policy.
- The original audit is historical; its source line references and original test counts refer to `ee95a4f`. This document describes the follow-up implementation.
