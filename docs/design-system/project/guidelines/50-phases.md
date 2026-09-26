# Phases: what the design system ships when

The build is split into three phases. Each phase lists the screens and components it needs from this system, so design work never runs ahead of what's being built. The engineering milestones (M0–M8) are in the build plan.

## Phase 1: everything working

**Goal:** a daily driver. Pair, see every chat, read full history, stream answers, send, stop, approve, and start chats in existing, new or cloned projects.

Milestones: M0 (foundations), M1 (connect and Home), M2 (chat and streaming), M3 (send and projects), and the approvals and forms slice of M4.

| Needs | Components |
| --- | --- |
| Pairing | `PairingScreen`, Button, text fields, `LoadingIndicator` |
| Home | `ThreadRow`, `StatusPill`, `ServerChip`, `ProjectShape`, `EmptyState`, FAB, `ShortNavigationBar`, search |
| Chat | `UserMessage`, `AssistantMessage`, `ReasoningRow`, `WorkGroup`, `ToolRow`, `CodeBlock`, `DiffView` (inline), `WorkingPill`, `JumpToLatest`, `ErrorCard`, markers |
| Composer | `Composer`, send and stop, `SplitButton` (steer/queue), `QueuedChip`, `ModelPickerSheet`, `AgentToggle` |
| Human in the loop | `PermissionCard`, `FormCard`, Inbox |
| Projects | New chat sheet, project cards, folder browser, clone progress (`WavyProgress`) |
| Settings | Settings list, model visibility |
| Surfaces | Sheet, dialog, snackbar, basic notifications |

## Phase 2: almost full parity with t3code

**Goal:** everything t3code mobile does that OpenCode can support, plus the Android platform touches.

Milestones: the rest of M4 (runtime presets, archive and pin, fork, undo, compact), M5 (power tools), M6 (background, terminal, device tier 1) and M7 (polish and platform).

| Needs | Components |
| --- | --- |
| Power composer | `CommandPopover` (`/` commands, `@` files, skills), attachments, dictation |
| Review | Full-screen `DiffView`, file list, line comments, floating toolbar, commit bar |
| Files | Files browser and viewer |
| Terminal | `TerminalView` with the extra-keys row |
| Devices, tier 1 | `DeviceViewer` with adb screenshots on an interval |
| Platform | Notifications with actions and inline reply, Live Update progress, Glance widget, QS tile, share target, shortcuts |
| Tablet | List-detail-supporting layout, `WideNavigationRail` |
| Chat management | Swipe actions, fork, rename, move, archive views, snooze, usage stats |

## Phase 3: the really hard features

**Goal:** things no mobile coding client does well. Each needs server-side work, a custom plugin, or real-time media.

| Feature | Why it's hard | Design needs |
| --- | --- | --- |
| Live device, tier 2 | A custom OpenCode plugin (`@flowpilot/opencode-devices`) exposes agent tools and an RPC and tunnel stream. The phone decodes H.264 at 30 fps with low-latency input | `DeviceViewer` live mode, latency badge, PiP mini-player |
| Web preview | Tunnel the agent's dev server through the browser plugin's RPC and tunnel pattern and render it in a WebView tab | Preview tab, address bar, reload, device-width toggle |
| Live browser tool view | Stream the browser plugin's page as the agent drives it | `ToolRow` browser variant with a live thumbnail |
| Parallel agents in worktrees | Spawn, compare and merge several chats on git worktrees | Compare view, worktree chips, merge flow |
| Remote access without VPN | A relay so the phone reaches the computer outside the LAN or Tailscale, with end-to-end encryption | Connection health screen |
| Voice mode | Hands-free conversation with interruption | Full-screen voice surface |
