#!/usr/bin/env zsh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec "$REPO_DIR/Run CampusVista.command"
