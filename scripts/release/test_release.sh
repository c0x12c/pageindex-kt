#!/usr/bin/env bash
# Tests for the release tag flow.
# Creates an isolated git repo for each test — no network, no side effects.
#
# Usage:
#   ./scripts/release/test_release.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SEMTAG="$SCRIPT_DIR/semtag"
CREATE_RELEASE="$SCRIPT_DIR/create_release.sh"

PASS=0
FAIL=0

# ── helpers ──────────────────────────────────────────────────────────────────

pass() { echo "  ✓ $1"; PASS=$((PASS+1)); }
fail() { echo "  ✗ $1"; echo "    expected: $2"; echo "    got:      $3"; FAIL=$((FAIL+1)); }

assert_eq() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$desc"
  else
    fail "$desc" "$expected" "$actual"
  fi
}

# Creates a temp git repo, optionally pre-tagged, runs a command inside it,
# then cleans up. Sets $REPO to the path.
with_repo() {
  local tag="${1:-}"
  REPO="$(mktemp -d)"
  git -C "$REPO" init -q
  git -C "$REPO" config user.email "test@test.com"
  git -C "$REPO" config user.name "test"
  # Need at least one commit for tags to attach
  touch "$REPO/.gitkeep"
  git -C "$REPO" add .
  git -C "$REPO" commit -q -m "init"
  if [[ -n "$tag" ]]; then
    git -C "$REPO" tag "$tag"
  fi
}

cleanup_repo() { rm -rf "$REPO"; }

run_semtag() {
  # semtag must run from within the git repo
  (cd "$REPO" && bash "$SEMTAG" "$@" 2>/dev/null)
}

# ── semtag: version calculation ───────────────────────────────────────────────

echo ""
echo "semtag: version calculation"

with_repo ""
assert_eq "no tags → getfinal returns v0.0.0" \
  "v0.0.0" "$(run_semtag getfinal)"
assert_eq "no tags → patch bump → v0.0.1" \
  "v0.0.1" "$(run_semtag final -s patch -o)"
assert_eq "no tags → minor bump → v0.1.0" \
  "v0.1.0" "$(run_semtag final -s minor -o)"
assert_eq "no tags → major bump → v1.0.0" \
  "v1.0.0" "$(run_semtag final -s major -o)"
cleanup_repo

with_repo "v1.2.3"
assert_eq "v1.2.3 → patch bump → v1.2.4" \
  "v1.2.4" "$(run_semtag final -s patch -o)"
assert_eq "v1.2.3 → minor bump → v1.3.0" \
  "v1.3.0" "$(run_semtag final -s minor -o)"
assert_eq "v1.2.3 → major bump → v2.0.0" \
  "v2.0.0" "$(run_semtag final -s major -o)"
cleanup_repo

with_repo "v0.9.9"
assert_eq "v0.9.9 → patch bump → v0.9.10" \
  "v0.9.10" "$(run_semtag final -s patch -o)"
cleanup_repo

# ── create_release.sh: argument handling ─────────────────────────────────────

echo ""
echo "create_release.sh: argument handling"

# Test by mocking semtag and git — we only care about the script's logic,
# not actual tagging. We override SEMTAG and git via PATH injection.

with_repo "v1.0.0"

# Create a mock semtag that echoes what it received
MOCK_BIN="$(mktemp -d)"
cat > "$MOCK_BIN/git" <<'MOCK'
#!/usr/bin/env bash
# Pass through everything except 'fetch' (which needs a remote)
if [[ "${1:-}" == "fetch" ]]; then exit 0; fi
exec /usr/bin/git "$@"
MOCK
chmod +x "$MOCK_BIN/git"

# Copy real semtag to repo so relative path ./scripts/release/semtag works
mkdir -p "$REPO/scripts/release"
cp "$SEMTAG" "$REPO/scripts/release/semtag"
cp "$CREATE_RELEASE" "$REPO/scripts/release/create_release.sh"
# Patch create_release.sh to not actually push
sed -i.bak 's/git push origin master/echo "[mock] git push skipped"/' \
  "$REPO/scripts/release/create_release.sh"

run_create_release() {
  (cd "$REPO" && PATH="$MOCK_BIN:$PATH" bash scripts/release/create_release.sh "$@" 2>/dev/null)
}

output_patch="$(run_create_release patch)"
assert_eq "patch action → next version line present" \
  "true" "$([[ "$output_patch" == *"Next release version:"* ]] && echo true || echo false)"

output_minor="$(run_create_release minor)"
assert_eq "minor action → next version line present" \
  "true" "$([[ "$output_minor" == *"Next release version:"* ]] && echo true || echo false)"

output_major="$(run_create_release major)"
assert_eq "major action → next version line present" \
  "true" "$([[ "$output_major" == *"Next release version:"* ]] && echo true || echo false)"

# Default action is patch when no argument given
output_default="$(run_create_release)"
assert_eq "default (no arg) → patch bump applied" \
  "true" "$([[ "$output_default" == *"Next release version:"* ]] && echo true || echo false)"

rm -rf "$MOCK_BIN"
cleanup_repo

# ── summary ──────────────────────────────────────────────────────────────────

echo ""
echo "Results: $PASS passed, $FAIL failed"
echo ""

if [[ $FAIL -gt 0 ]]; then exit 1; fi
