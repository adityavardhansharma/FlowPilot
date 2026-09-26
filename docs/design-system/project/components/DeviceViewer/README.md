The device viewer mirrors an Android emulator or device attached to your computer, so you can watch and tap while the agent tests.

## Tiers
- **Tier 1 (Phase 2):** `adb exec-out screencap -p` through `/api/shell`, with the PNG read back via `fs.read`, on an interval of 1s, 2s or 5s. Taps map to `adb shell input tap x y`, and typing to `input text`.
- **Tier 2 (Phase 3):** the custom `@flowpilot/opencode-devices` plugin streams H.264 through the RPC and tunnel. "Live" appears in the interval group, with a latency badge ("68 ms"). There's a PiP mini-player while you're in the chat.

## Anatomy
- The frame is letterboxed on `surface-container-lowest`, with a 6dp `inverse-surface` bezel and `radius-xl-increased`.
- A touch shows a 32dp `primary` disc at 35% for 300ms.
- A vibrant `FloatingToolbar` holds Back, Home, Recents, Screenshot to chat (attaches the frame to the composer) and Type text.
- When no device is found, the empty state reads "No devices. Start an emulator on your computer, or connect a phone with USB debugging."
