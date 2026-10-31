#!/data/data/com.termux/files/usr/bin/bash
# Заливает проект лаунчера в приватный репозиторий Haruxa/margymc-launcher.
set -u
cd "$(dirname "$0")"
REPO="Haruxa/moon-launcher"

gh api -X POST user/repos -f name=moon-launcher -F private=true -f description="Moon launcher" >/dev/null 2>&1

push_file() {
  local path="$1"
  local b64
  b64=$(base64 -w0 "$path")
  local sha
  sha=$(gh api "repos/$REPO/contents/$path" --jq .sha 2>/dev/null)
  local args=(-X PUT "repos/$REPO/contents/$path" -f message="update $path" -f content="$b64")
  [ -n "$sha" ] && args+=(-f sha="$sha")
  if gh api "${args[@]}" >/dev/null 2>&1; then echo "ok   $path"; else echo "FAIL $path"; fi
}

find . -type f \
  \( -name '*.kt' -o -name '*.kts' -o -name '*.xml' -o -name '*.properties' -o -name '*.yml' \
     -o -name '*.py' -o -name '*.sh' -o -name '*.png' \) \
  -not -path './build/*' -not -path './.gradle/*' | sed 's|^\./||' | sort | while read -r f; do
  push_file "$f"
done
