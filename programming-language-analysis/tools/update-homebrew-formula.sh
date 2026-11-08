#!/usr/bin/env bash
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
DIST_DIR="$ROOT/dist"
mkdir -p "$DIST_DIR"

"$ROOT/gradlew" :cli:installDist --console=plain >/dev/null
TAR="$DIST_DIR/machone-macos-x64.tar.gz"
rm -f "$TAR"
(cd "$DIST_DIR" && tar czf "$TAR" machone-macos-x64)
SHA=$(shasum -a 256 "$TAR" | awk '{print $1}')
FORMULA="$ROOT/tools/homebrew/machone.rb"
cp "$ROOT/tools/homebrew/machone.rb" "$FORMULA"
python3 - "$FORMULA" "$SHA" <<'PY'
import sys
path, sha = sys.argv[1], sys.argv[2]
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()
text = text.replace('REPLACE_WITH_SHA256', sha)
text = text.replace('https://example.invalid/machone-0.3.0-dev.tar.gz', 'https://github.com/AdriansOdyssey/codex_teste/releases/download/v0.3.0-dev/machone-macos-x64.tar.gz')
with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
PY

echo "Homebrew formula atualizada em $FORMULA"
