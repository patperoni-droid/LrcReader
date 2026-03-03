#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
hooks_dir="$repo_root/tools/githooks"

if [[ ! -d "$hooks_dir" ]]; then
  echo "Hooks directory not found: $hooks_dir" >&2
  exit 1
fi

chmod +x "$hooks_dir"/pre-push
git config core.hooksPath "$hooks_dir"

echo "Git hooks installed."
echo "core.hooksPath=$hooks_dir"
