#!/usr/bin/env bash
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
DIST_DIR="$ROOT/dist"
TARGET_OS="${1:-linux}"

mkdir -p "$DIST_DIR"
"$ROOT/gradlew" :cli:installDist --console=plain >/dev/null

case "$TARGET_OS" in
  linux)
    PACK_NAME="machone-linux-x64"
    INSTALL_ROOT="$DIST_DIR/$PACK_NAME"
    rm -rf "$INSTALL_ROOT"
    cp -R "$ROOT/cli/build/install/mach" "$INSTALL_ROOT"
    cat > "$INSTALL_ROOT/bin/machone" <<'EOF'
#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/mach" "$@"
EOF
    chmod 755 "$INSTALL_ROOT/bin/machone"
    tar czf "$DIST_DIR/${PACK_NAME}.tar.gz" -C "$DIST_DIR" "$PACK_NAME"
    if command -v dpkg-deb >/dev/null 2>&1; then
      PKG_ROOT="$DIST_DIR/machone-deb-root"
      rm -rf "$PKG_ROOT"
      mkdir -p "$PKG_ROOT/DEBIAN" "$PKG_ROOT/usr/local/share/machone" "$PKG_ROOT/usr/local/bin"
      cp -R "$ROOT/cli/build/install/mach/." "$PKG_ROOT/usr/local/share/machone/"
      cat > "$PKG_ROOT/usr/local/bin/machone" <<'EOF'
#!/bin/sh
set -eu
exec /usr/local/share/machone/bin/mach "$@"
EOF
      chmod 755 "$PKG_ROOT/usr/local/bin/machone"
      cat > "$PKG_ROOT/DEBIAN/control" <<EOF
Package: machone
Version: 0.3.0-dev
Section: devel
Priority: optional
Architecture: amd64
Maintainer: Copilot <223556219+Copilot@users.noreply.github.com>
Description: MachOne language compiler and runtime
EOF
      dpkg-deb --build "$PKG_ROOT" "$DIST_DIR/machone_0.3.0-dev_amd64.deb"
    else
      echo "dpkg-deb não encontrado; .deb foi pulado."
    fi
    if command -v rpmbuild >/dev/null 2>&1; then
      RPM_TOPDIR="$DIST_DIR/rpmbuild"
      rm -rf "$RPM_TOPDIR"
      mkdir -p "$RPM_TOPDIR/SOURCES" "$RPM_TOPDIR/SPECS" "$RPM_TOPDIR/BUILD" "$RPM_TOPDIR/RPMS" "$RPM_TOPDIR/SRPMS"
      tar czf "$RPM_TOPDIR/SOURCES/machone-linux-x64.tar.gz" -C "$DIST_DIR" "$PACK_NAME"
      cat > "$RPM_TOPDIR/SPECS/machone.spec" <<EOF
Summary: MachOne language compiler and runtime
Name: machone
Version: 0.3.0
Release: 0.dev
License: MIT
Group: Development/Tools
BuildArch: x86_64
Source0: machone-linux-x64.tar.gz
%global debug_package %{nil}
%description
MachOne is a language compiler and runtime for building MachOne programs.
%prep
%setup -q -n machone-linux-x64
%install
mkdir -p %{buildroot}/usr/local/share
cp -R . %{buildroot}/usr/local/share/machone-linux-x64
mkdir -p %{buildroot}/usr/local/bin
cat > %{buildroot}/usr/local/bin/machone <<'SCRIPT'
#!/bin/sh
set -eu
exec /usr/local/share/machone-linux-x64/bin/mach "$@"
SCRIPT
chmod 755 %{buildroot}/usr/local/bin/machone
%files
/usr/local/share/machone-linux-x64
/usr/local/bin/machone
%exclude /usr/local/share/machone-linux-x64/debugsourcefiles.list
EOF
      rpmbuild -bb "$RPM_TOPDIR/SPECS/machone.spec" --define "_topdir $RPM_TOPDIR" >/dev/null || echo "rpmbuild falhou; pacote .rpm não foi produzido."
      cp "$RPM_TOPDIR/RPMS/x86_64"/*.rpm "$DIST_DIR/" 2>/dev/null || true
    else
      echo "rpmbuild não encontrado; .rpm foi pulado."
    fi
    ;;
  macos)
    PACK_NAME="machone-macos-x64"
    INSTALL_ROOT="$DIST_DIR/$PACK_NAME"
    rm -rf "$INSTALL_ROOT"
    cp -R "$ROOT/cli/build/install/mach" "$INSTALL_ROOT"
    cat > "$INSTALL_ROOT/bin/machone" <<'EOF'
#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$SCRIPT_DIR/mach" "$@"
EOF
    chmod 755 "$INSTALL_ROOT/bin/machone"
    tar czf "$DIST_DIR/${PACK_NAME}.tar.gz" -C "$DIST_DIR" "$PACK_NAME"
    ;;
  *)
    echo "Uso: $0 [linux|macos]" >&2
    exit 2
    ;;
 esac

echo "Distribuição gerada em $DIST_DIR"
