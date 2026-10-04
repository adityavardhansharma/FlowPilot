#!/usr/bin/env bash
# Uploads the FlowPilot release key to the GitHub repository secrets the Release workflow reads.
#
#   scripts/set-release-secrets.sh path/to/flowpilot-release.jks [owner/repo]
#
# Run it once (and again only if the key changes). The passwords are read without echoing, passed to `gh` on
# stdin, and never written to disk or put on a command line. Needs the GitHub CLI, signed in with access to the
# repository's secrets; keytool (from any JDK) is used to check the passwords first when it is available.
set -euo pipefail

usage() { echo "Usage: $0 path/to/release.jks [owner/repo]" >&2; exit 2; }

[ $# -ge 1 ] && [ $# -le 2 ] || usage
KEYSTORE=$1
[ -f "$KEYSTORE" ] || { echo "No keystore at $KEYSTORE" >&2; exit 1; }
command -v gh >/dev/null || { echo "The GitHub CLI (gh) is needed: https://cli.github.com" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Sign in first: gh auth login" >&2; exit 1; }

REPO=${2:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}
echo "Setting release secrets on $REPO"

# Keep the secrets in this shell only, and forget them however the script ends.
trap 'unset FLOWPILOT_STOREPASS FLOWPILOT_KEYPASS' EXIT

read -rsp "Keystore password: " FLOWPILOT_STOREPASS; echo
[ -n "$FLOWPILOT_STOREPASS" ] || { echo "The keystore password can't be empty." >&2; exit 1; }
read -rp "Key alias [flowpilot]: " ALIAS
ALIAS=${ALIAS:-flowpilot}
read -rsp "Key password (Enter if it is the keystore password): " FLOWPILOT_KEYPASS; echo
FLOWPILOT_KEYPASS=${FLOWPILOT_KEYPASS:-$FLOWPILOT_STOREPASS}
export FLOWPILOT_STOREPASS FLOWPILOT_KEYPASS

# Check the keystore opens and holds the alias before uploading anything. keytool reads the password from the
# environment (-storepass:env), so it never appears in the process list.
if command -v keytool >/dev/null; then
    if ! CERT=$(keytool -list -v -keystore "$KEYSTORE" -storepass:env FLOWPILOT_STOREPASS -alias "$ALIAS" 2>/dev/null); then
        echo "The keystore didn't open with that password, or has no alias '$ALIAS'." >&2
        exit 1
    fi
    echo "Certificate SHA-256: $(echo "$CERT" | grep -m1 'SHA256:' | awk '{print $2}')"
    echo "It should match the fingerprint in docs/releasing.md."
else
    echo "keytool not found; skipping the password check."
fi

# Each value goes to gh on stdin. printf is a shell builtin, so the passwords never reach a command line.
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set FLOWPILOT_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$FLOWPILOT_STOREPASS" | gh secret set FLOWPILOT_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set FLOWPILOT_KEY_ALIAS --repo "$REPO"
printf '%s' "$FLOWPILOT_KEYPASS" | gh secret set FLOWPILOT_KEY_PASSWORD --repo "$REPO"

echo "Done. Start a release from Actions → Release → Run workflow."
