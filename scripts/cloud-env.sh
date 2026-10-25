#!/usr/bin/env bash
# Source this file before Gradle commands in the prepared Linux cloud instance.
# No credentials are read or copied. Proxy URLs are reduced to hostname/port only.

export JAVA_HOME="${IRUX_JAVA_HOME:-/workspace/toolchains/jdk-17.0.17+10}"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    printf '%s\n' 'JDK 17 absent : exécuter scripts/cloud-install.sh.' >&2
    return 1 2>/dev/null || exit 1
fi
case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
esac
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle}"

# Python's shlex.quote makes eval consume assignments, never proxy URL code.
# Authentication, user info and proxy URL paths are intentionally not exported.
_irux_proxy_assignment="$(python3 - <<'PY'
import os, re, shlex
from urllib.parse import urlparse
value = next((os.environ.get(k) for k in ('HTTPS_PROXY', 'https_proxy', 'HTTP_PROXY', 'http_proxy') if os.environ.get(k)), '')
if not value:
    print('_irux_proxy_host=; _irux_proxy_port=')
else:
    parsed = urlparse(value)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or not re.fullmatch(r'[A-Za-z0-9_.:-]+', parsed.hostname):
        raise SystemExit('Unsupported cloud HTTPS proxy configuration')
    port = parsed.port or (443 if parsed.scheme == 'https' else 80)
    print('_irux_proxy_host=' + shlex.quote(parsed.hostname))
    print('_irux_proxy_port=' + shlex.quote(str(port)))
PY
)" || { return 1 2>/dev/null || exit 1; }
eval "$_irux_proxy_assignment"
_irux_java_options=''
if [[ -n "$_irux_proxy_host" ]]; then
    _irux_java_options="-Dhttp.proxyHost=$_irux_proxy_host -Dhttp.proxyPort=$_irux_proxy_port -Dhttps.proxyHost=$_irux_proxy_host -Dhttps.proxyPort=$_irux_proxy_port"
fi
# Use the system's managed CA store, retaining TLS and certificate validation.
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
    _irux_java_options="$_irux_java_options -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
if [[ -n "$_irux_java_options" && " ${GRADLE_OPTS:-} " != *" $_irux_java_options "* ]]; then
    export GRADLE_OPTS="${GRADLE_OPTS:-} $_irux_java_options"
fi
unset _irux_proxy_assignment _irux_proxy_host _irux_proxy_port _irux_java_options
