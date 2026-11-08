#!/usr/bin/env bash
set -eu

usage(){
  cat <<'EOF'
Uso: ./tools/install-machone.sh [--user|--global]

Instala o launcher do MachOne no PATH do usuário.
  --user   instala em ~/.local/bin (padrão)
  --global instala em /usr/local/bin (requer sudo)
EOF
}

mode="--user"
if [ $# -gt 1 ]; then usage; exit 2; fi
if [ $# -eq 1 ]; then
  case "$1" in
    --user|--global) mode="$1" ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opção desconhecida: $1" >&2; usage >&2; exit 2 ;;
  esac
fi

repo_root=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if ! command -v java >/dev/null 2>&1; then
  echo "JDK 21+ não encontrado no PATH. Instale um JDK completo antes de instalar MachOne." >&2
  exit 1
fi

java_version=$(java -version 2>&1 | head -n 1 | sed -E 's/.* version "?([0-9]+).*/\1/')
if [ "$java_version" -lt 21 ]; then
  echo "MachOne requer JDK 21 ou superior. O Java atual é: $(java -version 2>&1 | head -n 1)" >&2
  exit 1
fi

"$repo_root/gradlew" :cli:installDist --console=plain >/dev/null

case "$mode" in
  --user)
    target_dir="$HOME/.local/bin"
    install_root="$HOME/.local/share/machone"
    ;;
  --global)
    target_dir="/usr/local/bin"
    install_root="/usr/local/share/machone"
    if [ "$(id -u)" -ne 0 ]; then
      echo "Instalação global requer sudo." >&2
      exit 1
    fi
    ;;
 esac

mkdir -p "$target_dir"
rm -rf "$install_root"
cp -R "$repo_root/cli/build/install/mach" "$install_root"
chmod +x "$install_root/bin/mach"
ln -sf "$install_root/bin/mach" "$target_dir/machone"
ln -sf "$install_root/bin/mach" "$target_dir/mach"

echo "MachOne instalado em: $install_root"
echo "Use: machone --version"
echo "Use: machone run caminho/do/programa.mach"
if ! printf '%s' "$PATH" | tr ':' '\n' | grep -Fx "$target_dir" >/dev/null 2>&1; then
  echo "Adicione ao PATH: export PATH=\"$target_dir:\$PATH\""
fi
