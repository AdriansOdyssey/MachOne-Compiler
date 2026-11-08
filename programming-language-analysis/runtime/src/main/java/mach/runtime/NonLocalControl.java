package mach.runtime;

/** Lexically targeted returns from do blocks; ordinary language failures are unrelated values. */
public final class NonLocalControl {
    private static final String TARGET_SLOT="\u0000machone.return-target";
    private NonLocalControl() {}
    public static final class Target {
        private final Thread owner=Thread.currentThread();
        private boolean active=true;
        private Target() {}
    }
    public static final class Signal extends RuntimeException {
        private final Target target;
        private final Object value;
        private Signal(Target target,Object value) {super(null,null,false,false);this.target=target;this.value=value;}
    }
    public static Target open(RuntimeSupport.Frame frame) {
        Target target=new Target();frame.bind(TARGET_SLOT,target);return target;
    }
    public static void close(Target target) {target.active=false;}
    public static RuntimeException raise(RuntimeSupport.Frame frame,Object value) {
        final Object target;
        try {target=frame.get(TARGET_SLOT);}
        catch(RuntimeSupport.MachFailure missing) {throw invalid("return em bloco do sem método ou lambda envolvente");}
        if(!(target instanceof Target destination))throw invalid("alvo de return não local inválido");
        if(!destination.active)throw invalid("return não local após o encerramento do método que criou o bloco");
        if(destination.owner!=Thread.currentThread())throw invalid("return não local não pode atravessar outra task");
        throw new Signal(destination,value);
    }
    public static Object receive(Target target,Signal signal) {
        if(signal.target!=target)throw signal;
        return signal.value;
    }
    private static RuntimeSupport.MachFailure invalid(String message) {return new RuntimeSupport.MachFailure("invalid_return",message);}
}
