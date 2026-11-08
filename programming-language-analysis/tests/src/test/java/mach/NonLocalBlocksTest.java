package mach;

import mach.runtime.NonLocalControl;
import mach.runtime.RuntimeSupport;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class NonLocalBlocksTest {
    private String run(String source) throws Exception {
        byte[] bytes=new Compiler().compile("blocks.ma",source,"BlockProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()) {Class<?> load(){return defineClass("BlockProgram",bytes,0,bytes.length);}}.load();
        var output=new ByteArrayOutputStream();PrintStream previous=System.out;
        try {System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void assignedDoReturnLeavesOwningMethod() throws Exception {
        assertEquals("7\n",run("""
            on choose -> int
                let block = do
                    return 7
                end
                (block) result
                99
            end
            show choose
            """));
    }
    @Test void doImplicitResultRemainsLocalToInvocation() throws Exception {
        assertEquals("99\n",run("""
            on choose -> int
                let block = do
                    7
                end
                (block) result
                99
            end
            show choose
            """));
    }
    @Test void barLambdaReturnRemainsLocal() throws Exception {
        assertEquals("99\n",run("""
            on choose -> int
                let block = || { return 7 }
                (block) result
                99
            end
            show choose
            """));
    }
    @Test void doReturnPassesThroughAnotherLambdaInvocation() throws Exception {
        assertEquals("7\n",run("""
            on choose -> int
                let block = do
                    return 7
                end
                let invoke_block = || { (block) result }
                (invoke_block) result
                99
            end
            show choose
            """));
    }
    @Test void doInsideBarLambdaReturnsFromThatLexicalLambda() throws Exception {
        assertEquals("17\n",run("""
            on choose -> int
                let f = || {
                    let block = do
                        return 7
                    end
                    (block) result
                    99
                }
                let answer = (f) result
                answer + 10
            end
            show choose
            """));
    }
    @Test void doCapturesImmutableBindingAndMutableCell() throws Exception {
        assertEquals("9\n",run("""
            on choose -> int
                let old_value = 7
                var @count = 1
                let block = do
                    @count += 1
                    return old_value + @count
                end
                let old_value = 99
                (block) result
                0
            end
            show choose
            """));
    }
    @Test void escapedDoReturnHasExplicitRuntimeDiagnostic() {
        var frame=new RuntimeSupport.Frame(null);var target=NonLocalControl.open(frame);
        var captured=frame.snapshot();NonLocalControl.close(target);
        var error=assertThrows(RuntimeSupport.MachFailure.class,()->NonLocalControl.raise(captured,7));
        assertEquals("invalid_return",error.kind);assertTrue(error.getMessage().contains("encerramento"));
    }
    @Test void differentTargetDoesNotInterceptNonLocalReturn() {
        var outer=new RuntimeSupport.Frame(null);var outerTarget=NonLocalControl.open(outer);
        var inner=new RuntimeSupport.Frame(outer);var innerTarget=NonLocalControl.open(inner);
        var signal=assertThrows(NonLocalControl.Signal.class,()->NonLocalControl.raise(outer,7));
        NonLocalControl.close(innerTarget);
        assertSame(signal,assertThrows(NonLocalControl.Signal.class,()->NonLocalControl.receive(innerTarget,signal)));
        assertEquals(7,NonLocalControl.receive(outerTarget,signal));NonLocalControl.close(outerTarget);
    }
    @Test void missingLexicalTargetHasExplicitRuntimeDiagnostic() {
        var error=assertThrows(RuntimeSupport.MachFailure.class,()->NonLocalControl.raise(new RuntimeSupport.Frame(null),7));
        assertEquals("invalid_return",error.kind);
    }
}
