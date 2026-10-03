# Releasing FlowPilot

Releases are built by the **Release** workflow from `main`, only when started by hand:
**Actions → Release → Run workflow**, then enter a version such as `0.3.0`. It runs the core tests, builds a signed
release APK, and publishes it as a GitHub release with generated notes.

## Signing

Every release is signed with the FlowPilot release key, so a new APK installs over the previous one. The workflow
stops with an error if the key is missing, instead of falling back to a debug key: each CI runner makes a new debug
key, and Android refuses to update an app with an APK signed by a different key.

The key lives only in these repository secrets (Settings → Secrets and variables → Actions):

| Secret | What it holds |
| --- | --- |
| `FLOWPILOT_KEYSTORE_BASE64` | The PKCS12 keystore file, base64 on one line (`base64 -w0 flowpilot-release.jks`) |
| `FLOWPILOT_KEYSTORE_PASSWORD` | The keystore password |
| `FLOWPILOT_KEY_ALIAS` | `flowpilot` |
| `FLOWPILOT_KEY_PASSWORD` | The key password (the same as the keystore password for PKCS12) |

The release key's certificate SHA-256 is
`FB:0B:2C:B7:4F:75:C5:56:4C:86:28:19:59:95:91:01:CC:E4:43:4D:F0:32:7F:77:FD:D8:47:D9:B8:05:A6:6E`.
Each release run prints the certificate it signed with in its summary; it must match this one.

Keep the keystore file and its password in a password manager with an offline backup. Never commit them:
`.gitignore` excludes `*.jks`. If the key is lost, no future APK can update an installed FlowPilot; people would
have to uninstall first.

## Version codes

The version code is the Release workflow's run number, so it rises with every release and Android accepts each
new APK as an update. The version name is what you type when starting the workflow.

## Installing over older builds

APKs released before the release key existed were signed with throwaway debug keys. To move to a signed release,
uninstall that build once (your pairing is saved on the phone, so you will need to scan a new code from
`opencode pair`), then install the signed APK. Every signed release after that installs over the last.
