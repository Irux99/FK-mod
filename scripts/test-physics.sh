#!/usr/bin/env bash
# Real Java 17 + JUnit tests of the production, Minecraft-independent guidance maths.
# This does not compile or emulate Forge, Minecraft, rendering or SBW integration.
set -euo pipefail

irux_project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${IRUX_JAVA_HOME:-}" || -x /workspace/toolchains/jdk-17.0.17+10/bin/javac ]]; then
    # Activate the same JDK used by the reproducible cloud Gradle workflow.
    source "$irux_project_root/scripts/cloud-env.sh"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    irux_java="$JAVA_HOME/bin/java"
    irux_javac="$JAVA_HOME/bin/javac"
else
    irux_java="$(command -v java || true)"
    irux_javac="$(command -v javac || true)"
fi
if [[ ! -x "$irux_java" || ! -x "$irux_javac" ]]; then
    printf '%s\n' 'JDK 17 requis : définissez JAVA_HOME (ou IRUX_JAVA_HOME dans le cloud).' >&2
    exit 1
fi
irux_java_version="$("$irux_java" -version 2>&1)"
irux_javac_version="$("$irux_javac" -version 2>&1)"
if [[ "$irux_java_version" != *'version "17.'* || "$irux_javac_version" != 'javac 17.'* ]]; then
    printf '%s\n' 'Le compilateur et le runtime doivent tous deux être Java 17.' >&2
    exit 1
fi

irux_tools="$irux_project_root/build/test-tools"
irux_classes="$irux_project_root/build/physics-test-classes"
irux_results="$irux_project_root/build/physics-test-results"
irux_junit_version=1.10.3
irux_junit="$irux_tools/junit-platform-console-standalone-$irux_junit_version.jar"
irux_junit_url="https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/$irux_junit_version/junit-platform-console-standalone-$irux_junit_version.jar"
# SHA-512 published by Maven Central at the same artifact URL with suffix .sha512.
irux_junit_sha512=8d6c2c2ab55a637606db8f5bcb5a3086fe7075cb9afe7407a38cd78d5c0ca14ee1526ded000e9516a069766f88ac71deb33c0e9df7e6746e4a92004310d49592
mkdir -p "$irux_tools"
if [[ ! -f "$irux_junit" ]]; then
    curl --fail --silent --show-error --location --retry 2 --connect-timeout 20 --max-time 120 \
        "$irux_junit_url" --output "$irux_junit.part"
    printf '%s  %s\n' "$irux_junit_sha512" "$irux_junit.part" | sha512sum --check --status
    mv -- "$irux_junit.part" "$irux_junit"
fi
# Recheck retained dependencies too; a changed or corrupted cached jar must fail closed.
printf '%s  %s\n' "$irux_junit_sha512" "$irux_junit" | sha512sum --check --status

# These are this script's generated directories; remove stale results before the new run.
rm -rf -- "$irux_classes" "$irux_results"
mkdir -p "$irux_classes" "$irux_results"
"$irux_javac" --release 17 -encoding UTF-8 --class-path "$irux_junit" -d "$irux_classes" \
    "$irux_project_root/src/main/java/fr/irux/fk2000/physics/GuidanceMath.java" \
    "$irux_project_root/src/main/java/fr/irux/fk2000/physics/ShockwaveMath.java" \
    "$irux_project_root/src/main/java/fr/irux/fk2000/physics/RadarDisplayMath.java" \
    "$irux_project_root/src/test/java/fr/irux/fk2000/physics/GuidanceMathTest.java" \
    "$irux_project_root/src/test/java/fr/irux/fk2000/physics/ShockwaveMathTest.java" \
    "$irux_project_root/src/test/java/fr/irux/fk2000/physics/RadarDisplayMathTest.java"
# exec preserves the underlying JUnit exit status, including failed or zero-test runs.
exec "$irux_java" -jar "$irux_junit" execute --class-path "$irux_classes" \
    --select-class fr.irux.fk2000.physics.GuidanceMathTest \
    --select-class fr.irux.fk2000.physics.ShockwaveMathTest --fail-if-no-tests \
    --select-class fr.irux.fk2000.physics.RadarDisplayMathTest \
    --reports-dir "$irux_results" --details summary
