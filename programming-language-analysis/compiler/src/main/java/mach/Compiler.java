package mach;
import mach.frontend.Frontend;
import mach.semantic.Analyzer;
import mach.backend.JvmBackend;
import mach.ir.MachIR;
public final class Compiler {
    public MachIR check(String file,String source) {return new Analyzer().analyze(StandardPrelude.parse(file,source));}
    public byte[] compile(String file,String source,String className) {return new JvmBackend().generate(check(file,source),className);}
}
