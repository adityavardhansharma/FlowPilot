# FlowPilot

FlowPilot is a native Android app, written in Kotlin with Jetpack Compose and Material 3 Expressive, for driving OpenCode v2 agents from your phone.

## How to run

On your computer, install OpenCode 2 once:

```sh
npm i -g @opencode/cli
```

Then let the background service listen on your network, start it, and print a pairing code:

```sh
opencode service set hostname 0.0.0.0
opencode service start
opencode pair
```

If the service was already running, run `opencode service restart` after the `set` command so it picks up the new hostname.

In FlowPilot, tap **Scan QR code** and point the camera at the QR code `opencode pair` shows. You can also tap **Enter address** and paste the link it prints. Your phone and computer need to be on the same Wi-Fi, or on the same Tailscale network. The service listens on port 49374.

On Android 17 and later, allow **Nearby devices** when FlowPilot asks. Android needs that permission before an app can reach a computer on your Wi-Fi.

If the QR code shows an address your phone can't reach, for example because of a VPN or Tailscale, print the link with a reachable URL instead: `opencode pair --url http://<your-computer's-ip>:49374`.

## Docs

- [Build plan](docs/build-plan.md): features, architecture, milestones M0–M8, and the three phases.
- [OpenCode v2 API design](docs/opencode-android-design.md): the verified API shapes the app uses.
- [Design system](docs/design-system/project/README.md): the brand book, `tokens.json`, the UX guidelines under `guidelines/`, and 45 component specs with HTML previews under `components/`.
- Live design system: https://claude.ai/artifact/746u49XQQC3nxFMnydLBEg (private until shared).
