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
exec /usr/local/share/machone-linux-x64/bin/mach "linux"
SCRIPT
chmod 755 %{buildroot}/usr/local/bin/machone
%files
/usr/local/share/machone-linux-x64
/usr/local/bin/machone
%exclude /usr/local/share/machone-linux-x64/debugsourcefiles.list
