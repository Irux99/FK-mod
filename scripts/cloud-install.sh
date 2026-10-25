#!/usr/bin/env bash
set -euo pipefail

IRUX_PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
IRUX_TOOLCHAIN_ROOT=/workspace/toolchains
IRUX_JDK_DIR="$IRUX_TOOLCHAIN_ROOT/jdk-17.0.17+10"
IRUX_JDK_SHA=992f96e7995075ac7636bb1a8de52b0c61d71ed3137fafc979ab96b4ab78dd75
IRUX_WRAPPER_SHA=cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    printf '%s\n' 'Ce bootstrap cible Linux x86_64 ; utiliser un JDK 17 officiel adapté sur cette plateforme.' >&2
    exit 1
fi
mkdir -p "$IRUX_TOOLCHAIN_ROOT" /workspace/downloads
if [[ ! -x "$IRUX_JDK_DIR/bin/java" ]]; then
    IRUX_ARCHIVE=/workspace/downloads/jdk17.tar.gz
    if [[ ! -f "$IRUX_ARCHIVE" ]] || ! printf '%s  %s\n' "$IRUX_JDK_SHA" "$IRUX_ARCHIVE" | sha256sum --check --status; then
        IRUX_TEMP_ARCHIVE="$(mktemp /workspace/downloads/jdk17.XXXXXX.tar.gz)"
        trap 'rm -f -- "${IRUX_TEMP_ARCHIVE:-}"' EXIT
        curl --proto '=https' --tlsv1.2 --fail --show-error --location --retry 2 \
            --connect-timeout 30 --max-time 600 \
            'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz' \
            --output "$IRUX_TEMP_ARCHIVE"
        printf '%s  %s\n' "$IRUX_JDK_SHA" "$IRUX_TEMP_ARCHIVE" | sha256sum --check --status
        mv -- "$IRUX_TEMP_ARCHIVE" "$IRUX_ARCHIVE"
        trap - EXIT
    fi
    printf '%s  %s\n' "$IRUX_JDK_SHA" "$IRUX_ARCHIVE" | sha256sum --check --status
    tar -xzf "$IRUX_ARCHIVE" -C "$IRUX_TOOLCHAIN_ROOT"
fi
export IRUX_JAVA_HOME="$IRUX_JDK_DIR"
source "$IRUX_PROJECT_ROOT/scripts/cloud-env.sh"
"$JAVA_HOME/bin/java" -version
if ! "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | rg -q 'version "17\.'; then
    printf '%s\n' 'Le build exige Java 17.' >&2
    exit 1
fi
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
    "$JAVA_HOME/bin/keytool" -list -keystore /etc/ssl/certs/java/cacerts -storepass changeit >/dev/null
fi
printf '%s  %s\n' "$IRUX_WRAPPER_SHA" "$IRUX_PROJECT_ROOT/gradle/wrapper/gradle-wrapper.jar" | sha256sum --check --status
if ! rg -q '^distributionSha256Sum=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612$' "$IRUX_PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"; then
    printf '%s\n' 'Empreinte attendue Gradle 8.8 absente ; vérifier le wrapper avant installation.' >&2
    exit 1
fi
cd -- "$IRUX_PROJECT_ROOT"
IRUX_BUILD_LOG="/workspace/forge-build-$(date -u +%Y%m%dT%H%M%SZ).log"
printf 'Journal du build : %s\n' "$IRUX_BUILD_LOG"
./gradlew --no-daemon build 2>&1 | tee "$IRUX_BUILD_LOG"
