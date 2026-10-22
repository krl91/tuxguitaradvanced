#!/bin/bash
# Self-test of misc/verify-release.sh (spec 015, FR-004, FR-006, SC-002): with a fake `gh` it must pass on a correct draft and on a correct published
# release, and fail, naming the place, when ONE value is wrong.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFY="$ROOT/misc/verify-release.sh"
TMP="$(mktemp -d /tmp/test-verify-release.XXXXXX)"; trap 'rm -rf "$TMP"' EXIT
TAG=v0.6.0-beta.1; VERSION=0.6.0-beta.1; COMMIT=fc50a2f43aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa; OTHER=0123456789abcdef0123456789abcdef01234567
fail=0
# a fake gh: `gh release view ...` prints $FAKE_RELEASE; `gh api repos/R/git/ref/tags/T` prints $FAKE_REF (or fails like a 404 when it is empty)
mkdir -p "$TMP/bin"
cat > "$TMP/bin/gh" <<'GH'
#!/bin/bash
case "$1 $2" in
  "release view") printf '%s' "$FAKE_RELEASE";;
  "api repos/"*) if [ -n "${FAKE_REF:-}" ]; then printf '%s' "$FAKE_REF"; else echo '{"message":"Not Found"}' >&2; exit 1; fi;;
  *) echo "fake gh: unexpected $*" >&2; exit 9;;
esac
GH
chmod +x "$TMP/bin/gh"
assets() { python3 -c 'import json,sys; print(json.dumps([{"name":n} for n in sys.argv[1:]]))' "$@"; }
ASSETS_OK="$(assets TuxGuitarAdvanced-$TAG-macos-arm64.zip TuxGuitarAdvanced-$TAG-windows-x86_64.zip TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz TuxGuitarAdvanced-$TAG-linux-amd64.deb)"
rel() { # draft tag name commit body assets prerelease
  python3 - "$@" <<'PY'
import json,sys
draft,tag,name,commit,body,assets,pre=sys.argv[1:8]
print(json.dumps({"isDraft":draft=="true","tagName":tag,"name":name,"targetCommitish":commit,"body":body,"assets":json.loads(assets),"isPrerelease":pre=="true"}))
PY
}
BODY="Notes. Built from commit $COMMIT."
run() { # mode  (uses FAKE_RELEASE and FAKE_REF from the environment)
  PATH="$TMP/bin:$PATH" GITHUB_REPOSITORY=krl91/tuxguitaradvanced "$VERIFY" "$1" "$TAG" "$VERSION" "$COMMIT" 2>&1
}
check() { # name expected-exit expected-word mode
  local out code; out="$(run "$4")"; code=$?
  if { [ "$2" = 0 ] && [ "$code" = 0 ]; } || { [ "$2" != 0 ] && [ "$code" != 0 ] && echo "$out" | grep -q "$3"; }; then echo "ok: $1"
  else echo "FAILED: $1 (exit $code)"; echo "$out" | head -4; fail=1; fi
}
export FAKE_REF=""
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "draft: a correct draft passes" 0 "" --draft
export FAKE_RELEASE="$(rel false "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "draft: a release already published fails" 1 "already published\|WRONG" --draft
export FAKE_RELEASE="$(rel true v0.6.0-beta.2 "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "draft: a wrong tag name fails" 1 "WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitar $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "draft: a wrong title fails" 1 "WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$OTHER" "$BODY" "$ASSETS_OK" true)"
check "draft: a wrong target commit fails" 1 "WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "master" "$BODY" "$ASSETS_OK" true)"
check "draft: a branch name as target fails" 1 "WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "Notes without the commit" "$ASSETS_OK" true)"
check "draft: notes without the full commit fail" 1 "WRONG\|MISSING" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "Notes with the short commit ${COMMIT:0:7}" "$ASSETS_OK" true)"
check "draft: notes with only the short commit fail" 1 "WRONG\|MISSING" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$(assets TuxGuitarAdvanced-$TAG-macos-arm64.zip TuxGuitarAdvanced-$TAG-windows-x86_64.zip TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz)" true)"
check "draft: a missing asset fails" 1 "MISSING\|WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$(assets TuxGuitarAdvanced-$TAG-macos-arm64.zip TuxGuitarAdvanced-$TAG-windows-x86_64.zip TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz TuxGuitarAdvanced-$TAG-linux-amd64.deb extra.txt)" true)"
check "draft: an extra asset fails" 1 "WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$(assets TuxGuitarAdvanced-v0.6.0-macos-arm64.zip TuxGuitarAdvanced-$TAG-windows-x86_64.zip TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz TuxGuitarAdvanced-$TAG-linux-amd64.deb)" true)"
check "draft: an asset named with another version fails" 1 "MISSING\|WRONG" --draft
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"; export FAKE_REF='{"ref":"refs/tags/v0.6.0-beta.1","object":{"sha":"'$COMMIT'","type":"commit"}}'
check "draft: a git tag that already exists fails (a draft must not have one)" 1 "WRONG" --draft
# published
export FAKE_RELEASE="$(rel false "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "published: the tag on the commit passes" 0 "" --published
export FAKE_REF='{"ref":"refs/tags/v0.6.0-beta.1","object":{"sha":"'$OTHER'","type":"commit"}}'
check "published: a tag on another commit fails" 1 "WRONG" --published
export FAKE_REF=""
check "published: no git tag fails" 1 "MISSING" --published
export FAKE_REF='{"ref":"refs/tags/v0.6.0-beta.1","object":{"sha":"'$COMMIT'","type":"commit"}}'
export FAKE_RELEASE="$(rel true "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" true)"
check "published: a release still in draft fails" 1 "WRONG" --published
export FAKE_RELEASE="$(rel false "$TAG" "TuxGuitarAdvanced $TAG" "$COMMIT" "$BODY" "$ASSETS_OK" false)"
check "published: a labelled version not marked pre-release fails" 1 "WRONG" --published
unset FAKE_REF
exit $fail
