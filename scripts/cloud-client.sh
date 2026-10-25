#!/usr/bin/env bash
# Start/reuse only this helper's display process, then run the development client.
set -euo pipefail
irux_project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$irux_project_root/scripts/cloud-env.sh"
irux_display_root="${IRUX_DISPLAY_ROOT:-/workspace/toolchains/xvfb}"
irux_runtime_dir="${IRUX_CLIENT_DIRECTORY:-/workspace/client-smoke}"
irux_display_number="${IRUX_DISPLAY_NUMBER:-91}"
[[ "$irux_display_number" =~ ^[0-9]+$ ]] || { printf '%s\n' 'IRUX_DISPLAY_NUMBER must be an integer.' >&2; exit 1; }
[[ -x "$irux_display_root/usr/bin/Xvfb" ]] || { printf '%s\n' 'Run scripts/cloud-display-install.sh first.' >&2; exit 1; }
mkdir -p "$irux_runtime_dir"
export DISPLAY=":$irux_display_number"
export LIBGL_ALWAYS_SOFTWARE=1
irux_pid_file="$irux_display_root/display-$irux_display_number.pid"
irux_reuse=false
if [[ -r "$irux_pid_file" ]]; then
    read -r irux_display_pid < "$irux_pid_file" || true
    if [[ "${irux_display_pid:-}" =~ ^[0-9]+$ ]] && kill -0 "$irux_display_pid" 2>/dev/null; then
        irux_reuse="$(python3 - "$irux_display_pid" "$irux_display_root/usr/bin/Xvfb" "$DISPLAY" <<'PY'
import pathlib, sys
try:
    args = pathlib.Path('/proc/' + sys.argv[1] + '/cmdline').read_bytes().split(b'\0')
    same_binary = args[0].decode() == sys.argv[2]
    same_display = sys.argv[3].encode() in args
    print('true' if same_binary and same_display else 'false')
except (OSError, ValueError, UnicodeError):
    print('false')
PY
)"
    fi
fi
if [[ "$irux_reuse" != true ]]; then
    if [[ -S "/tmp/.X11-unix/X$irux_display_number" || -e "/tmp/.X$irux_display_number-lock" ]]; then
        printf 'Display %s belongs to another process; choose another IRUX_DISPLAY_NUMBER.\n' "$DISPLAY" >&2
        exit 1
    fi
    "$irux_display_root/usr/bin/Xvfb" "$DISPLAY" -screen 0 1280x800x24 -nolisten tcp -ac \
        > "$irux_display_root/display-$irux_display_number.log" 2>&1 &
    irux_display_pid=$!
    printf '%s\n' "$irux_display_pid" > "$irux_pid_file"
    for irux_attempt in {1..50}; do
        if [[ -S "/tmp/.X11-unix/X$irux_display_number" ]] && xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then break; fi
        kill -0 "$irux_display_pid" 2>/dev/null || { cat "$irux_display_root/display-$irux_display_number.log" >&2; exit 1; }
        sleep .1
    done
fi
xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 || { printf '%s\n' 'Display did not become ready.' >&2; exit 1; }
cd "$irux_project_root"
exec ./gradlew --no-daemon --max-workers=3 "-PrunDirectory=$irux_runtime_dir" runClient "$@"
