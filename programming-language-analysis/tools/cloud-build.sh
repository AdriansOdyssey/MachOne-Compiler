#!/usr/bin/env bash
set -euo pipefail
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache=${MACH_TOOLCHAINS:-/workspace/toolchains}
mkdir -p "$cache"
fetch() {
    local url=$1 archive=$2 expected=$3
    if [[ ! -f "$archive" ]] || ! printf '%s  %s\n' "$expected" "$archive" | sha256sum --check --status; then
        curl --fail --location --retry 2 "$url" --output "$archive"
    fi
    printf '%s  %s\n' "$expected" "$archive" | sha256sum --check --status
}
fetch 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.9%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.9_10.tar.gz' "$cache/jdk21.tar.gz" 810d3773df7e0d6c4394e4e244b264c8b30e0b05a0acf542d065fd78a6b65c2f
fetch 'https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip' "$cache/gradle-9.1.0-bin.zip" a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806
if [[ ! -x "$cache/jdk-21.0.9+10/bin/javac" ]]; then tar -xzf "$cache/jdk21.tar.gz" -C "$cache"; fi
if [[ ! -x "$cache/gradle-9.1.0/bin/gradle" ]]; then unzip -q -o "$cache/gradle-9.1.0-bin.zip" -d "$cache"; chmod +x "$cache/gradle-9.1.0/bin/gradle"; fi
export JAVA_HOME="$cache/jdk-21.0.9+10"
args=(--no-daemon --max-workers=2)
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    read -r proxy_host proxy_port < <(python3 -c 'import os,urllib.parse;p=urllib.parse.urlparse(os.environ["HTTPS_PROXY"]);print(p.hostname,p.port or 8080)')
    args+=("-Dhttps.proxyHost=$proxy_host" "-Dhttps.proxyPort=$proxy_port")
fi
if [[ -f /etc/ssl/certs/java/cacerts ]]; then args+=(-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts); fi
cd "$root"
"$cache/gradle-9.1.0/bin/gradle" "${args[@]}" :tests:test :cli:installDist
