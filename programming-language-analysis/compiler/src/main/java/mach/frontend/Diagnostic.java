package mach.frontend;
import mach.ast.Ast.Span;
public final class Diagnostic extends RuntimeException {
    private final String code;
    private final Span span;
    public Diagnostic(String code,Span span,String message) { super(span+": "+code+": "+message); this.code=code;this.span=span; }
    public String code() {return code;}
    public Span span() {return span;}
}
