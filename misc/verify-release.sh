#!/bin/bash
# Verifies a GitHub release against the identity that was asked for (spec 015, FR-004, FR-006, SC-002, contracts/verify-version.md, release level).
# usage: GITHUB_REPOSITORY=owner/repo misc/verify-release.sh (--draft | --published) <tag> <version> <commit>
#   --draft      before publication: still a draft, tag name, title, TARGET commit, the full commit in the notes, exactly the four expected files.
#                A draft has no git tag (probe of 2026-10-22, research R11b): a git tag found for it is wrong.
#   --published  right after publication: no longer a draft, pre-release flag matching the label, and the git TAG points to the commit (never moved).
# One line per place  OK | WRONG | MISSING; exit 0 only if every place is OK. Needs gh (authenticated) and python3.
set -uo pipefail
MODE="${1:?--draft or --published}"; TAG="${2:?tag}"; VERSION="${3:?version}"; COMMIT="${4:?commit}"
REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY (owner/repo) is required: a bare gh may resolve to another repository}"
case "$MODE" in --draft|--published) ;; *) echo "unknown mode: $MODE" >&2; exit 2;; esac
[[ "$TAG" == "v$VERSION" ]] || { echo "the tag $TAG is not v$VERSION" >&2; exit 2; }
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "not a full commit: $COMMIT" >&2; exit 2; }
fail=0
ok()   { echo "OK      $1: $2"; }
wrong(){ echo "WRONG   $1: read '$2' (expected '$3')"; fail=1; }
miss() { echo "MISSING $1: $2"; fail=1; }
cmp_() { if [ "$2" = "$3" ]; then ok "$1" "$2"; else wrong "$1" "$2" "$3"; fi; }

JSON="$(gh release view "$TAG" --repo "$REPO" --json isDraft,tagName,name,targetCommitish,body,assets,isPrerelease 2>/dev/null)" || { miss "release" "no release $TAG in $REPO"; exit 1; }
field() { python3 -c 'import json,sys; d=json.load(sys.stdin); v=d.get(sys.argv[1]); print(v if not isinstance(v,bool) else str(v).lower())' "$1" <<< "$JSON"; }
if [[ "$VERSION" == *-* ]]; then PRE=true; else PRE=false; fi

# the git tag, if any: lightweight tags point to the commit, annotated ones to a tag object
tagsha() {
  local ref sha type
  ref="$(gh api "repos/$REPO/git/ref/tags/$TAG" 2>/dev/null)" || return 1
  sha="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["object"]["sha"])' <<< "$ref")"
  type="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["object"]["type"])' <<< "$ref")"
  if [ "$type" = tag ]; then sha="$(gh api "repos/$REPO/git/tags/$sha" --jq .object.sha 2>/dev/null)" || return 1; fi
  echo "$sha"
}

if [ "$MODE" = --draft ]; then
  cmp_ "state" "$([ "$(field isDraft)" = true ] && echo draft || echo published)" "draft"
  cmp_ "tag name" "$(field tagName)" "$TAG"
  cmp_ "title" "$(field name)" "TuxGuitarAdvanced $TAG"
  cmp_ "target commit" "$(field targetCommitish)" "$COMMIT"
  if python3 -c 'import json,sys; sys.exit(0 if sys.argv[1] in json.load(sys.stdin).get("body","") else 1)' "$COMMIT" <<< "$JSON"; then ok "notes" "state the full commit"; else miss "notes" "the full commit $COMMIT is not in the notes of the release"; fi
  GOT="$(python3 -c 'import json,sys; print("\n".join(sorted(a["name"] for a in json.load(sys.stdin).get("assets",[]))))' <<< "$JSON")"
  WANT="$(printf '%s\n' "TuxGuitarAdvanced-$TAG-linux-amd64.deb" "TuxGuitarAdvanced-$TAG-linux-x86_64.tar.gz" "TuxGuitarAdvanced-$TAG-macos-arm64.zip" "TuxGuitarAdvanced-$TAG-windows-x86_64.zip" | sort)"
  while IFS= read -r want; do echo "$GOT" | grep -qxF "$want" && ok "asset" "$want" || miss "asset" "$want is not attached"; done <<< "$WANT"
  while IFS= read -r got; do [ -n "$got" ] && ! echo "$WANT" | grep -qxF "$got" && wrong "asset" "$got" "one of the four packages"; done <<< "$GOT"
  if ts="$(tagsha)"; then wrong "git tag" "a tag exists ($ts)" "none before publication"; fi
else
  cmp_ "state" "$([ "$(field isDraft)" = true ] && echo draft || echo published)" "published"
  cmp_ "pre-release flag" "$(field isPrerelease)" "$PRE"
  if ts="$(tagsha)" && [ -n "$ts" ]; then cmp_ "git tag $TAG points to" "$ts" "$COMMIT"; else miss "git tag" "no tag $TAG in $REPO"; fi
fi
[ "$fail" = 0 ] && echo "release verified ($MODE): $TAG, commit ${COMMIT:0:7}"
exit $fail
