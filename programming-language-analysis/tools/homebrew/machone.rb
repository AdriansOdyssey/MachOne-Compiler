class Machone < Formula
  desc "MachOne language compiler and runtime"
  homepage "https://github.com/AdriansOdyssey/codex_teste"
  version "0.3.0-dev"
  url "https://example.invalid/machone-0.3.0-dev.tar.gz"
  sha256 "REPLACE_WITH_SHA256"

  depends_on "openjdk@21"

  def install
    libexec.install Dir["*"]
    bin.install_symlink libexec/'bin'/'mach'
    bin.install_symlink libexec/'bin'/'machone'
  end
end
