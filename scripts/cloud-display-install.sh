#!/usr/bin/env bash
# Install optional headless display tools locally, preserving Debian signatures and hashes.
set -euo pipefail
irux_display_root="${IRUX_DISPLAY_ROOT:-/workspace/toolchains/xvfb}"
for irux_tool in python3 curl sqv dpkg-deb; do
    command -v "$irux_tool" >/dev/null || { printf 'Missing display prerequisite: %s\n' "$irux_tool" >&2; exit 1; }
done
[[ -r /usr/share/keyrings/debian-archive-keyring.pgp ]] || { printf '%s\n' 'Debian archive keyring is required.' >&2; exit 1; }
mkdir -p "$irux_display_root/downloads"
irux_metadata_dir="$(mktemp -d "$irux_display_root/downloads/metadata.XXXXXX")"
trap 'rm -rf "$irux_metadata_dir"' EXIT
curl --fail --location --silent --show-error --retry 2 --connect-timeout 20 \
    https://deb.debian.org/debian/dists/trixie/InRelease -o "$irux_metadata_dir/InRelease"
sqv --keyring /usr/share/keyrings/debian-archive-keyring.pgp --cleartext \
    --output "$irux_metadata_dir/Release" "$irux_metadata_dir/InRelease"
curl --fail --location --silent --show-error --retry 2 --connect-timeout 20 \
    https://deb.debian.org/debian/dists/trixie/main/binary-amd64/Packages.xz -o "$irux_metadata_dir/Packages.xz"
python3 - "$irux_display_root" "$irux_metadata_dir" <<'PY'
import hashlib, json, lzma, pathlib, subprocess, sys, urllib.request
root, metadata = map(pathlib.Path, sys.argv[1:])
release = (metadata / 'Release').read_text()
if 'Origin: Debian\n' not in release or 'Codename: trixie\n' not in release:
    raise SystemExit('Unexpected Debian release identity')
sha_entries, active = {}, False
for line in release.splitlines():
    if line == 'SHA256:':
        active = True
        continue
    if active:
        if not line.startswith(' '):
            break
        digest, size, name = line.split()
        sha_entries[name] = (digest, int(size))
expected_hash, expected_size = sha_entries['main/binary-amd64/Packages.xz']
compressed = (metadata / 'Packages.xz').read_bytes()
if len(compressed) != expected_size or hashlib.sha256(compressed).hexdigest() != expected_hash:
    raise SystemExit('Debian package index checksum mismatch')
index = lzma.decompress(compressed).decode()
receipt = []
for name in ('xvfb', 'xdotool', 'libxdo3'):
    paragraph = next(p for p in index.split('\n\n') if p.startswith('Package: ' + name + '\n'))
    fields = dict(line.split(': ', 1) for line in paragraph.splitlines() if ': ' in line and not line.startswith(' '))
    filename = fields['Filename']
    if not filename.startswith('pool/') or '..' in filename.split('/'):
        raise SystemExit('Unexpected Debian artifact path')
    archive = root / 'downloads' / pathlib.PurePosixPath(filename).name
    expected = fields['SHA256']
    if not archive.is_file() or hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
        urllib.request.urlretrieve('https://deb.debian.org/debian/' + filename, archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
        raise SystemExit('Checksum mismatch: ' + name)
    subprocess.run(['dpkg-deb', '-x', str(archive), str(root)], check=True)
    receipt.append({'package': name, 'version': fields['Version'], 'sha256': expected})
    print(name + ' ' + fields['Version'] + ': verified and extracted')
(root / 'package-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
PY
if ldd "$irux_display_root/usr/bin/Xvfb" | rg 'not found'; then
    printf '%s\n' 'Missing Xvfb shared libraries; install the reported Debian prerequisites.' >&2
    exit 1
fi
printf 'Headless display tools ready in %s\n' "$irux_display_root"
