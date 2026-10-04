# Releasing FlowPilot

A release is one click once the signing key is in place.

1. **Once:** upload the release key to the repository secrets:

   ```sh
   scripts/set-release-secrets.sh path/to/flowpilot-release.jks
   ```

   It asks for the keystore password, the alias (default `flowpilot`) and the key password without echoing them,
   checks that the keystore opens, prints its certificate SHA-256 (compare it with the one below), and sets the four
   secrets with `gh secret set`. Nothing is written to disk. It needs the [GitHub CLI](https://cli.github.com),
   signed in with access to this repository.
2. **Every release:** **Actions → Release → Run workflow**. Leave the version blank to bump the patch of the latest
   `v*` tag (`v0.0.6` → `0.0.7`; `0.1.0` if there are no tags), or type one such as `0.3.0`.
3. The workflow runs the core tests, builds a release APK signed with the release key, and publishes it under
   **Releases** with generated notes. Download `FlowPilot-<version>.apk` on the phone and install it: it updates the
   installed app in place.

Releases are built from `main`. To try a branch first, pick it in the "Use workflow from" menu when starting the run.

## Signing

Every release is signed with the FlowPilot release key, so a new APK installs over the previous one. The workflow
stops with an error if the key is missing, instead of falling back to a debug key: each CI runner makes a new debug
key, and Android refuses to update an app with an APK signed by a different key.

The key lives only in these repository secrets (Settings → Secrets and variables → Actions), which
`scripts/set-release-secrets.sh` sets:

| Secret | What it holds |
| --- | --- |
| `FLOWPILOT_KEYSTORE_BASE64` | The PKCS12 keystore file, base64 on one line |
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
new APK as an update. The version name is what you type when starting the workflow, or the next patch version
when you leave it blank.

## Installing over older builds

APKs released before the release key existed were signed with throwaway debug keys. To move to a signed release,
uninstall that build once (your pairing is saved on the phone, so you will need to scan a new code from
`opencode pair`), then install the signed APK. Every signed release after that installs over the last.
