package mach;

import mach.ast.Ast;
import mach.frontend.Frontend;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageSyntaxTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("messages.ma",source,"MessageProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()) {Class<?> loadProgram(){return defineClass("MessageProgram",code,0,code.length);}}.loadProgram();
        var out=new ByteArrayOutputStream();var previous=System.out;
        try {System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void everyColonCombinationOnImplicitSend() throws Exception {
        assertEquals("7\n7\n7\n7\n",run("""
            on some: a int, com: b int -> int = a + b
            show some: 5 com: 2
            show some 5 com 2
            show some: 5 com 2
            show some 5 com: 2
            """));
    }
    @Test void colonsAndCommasOnExplicitSend() throws Exception {
        assertEquals("7\n7\n7\n",run("""
            record Math end
            impl type[Math]
                on some: a int, com: b int -> int = a + b
            end
            show (Math) some 5 com 2
            show (Math) some: 5, com: 2
            show (Math) some 5, com 2
            """));
    }
    @Test void namedArgumentsAreBindingsNotSelectors() throws Exception {
        assertEquals("7\n",run("""
            on some: a int, com: b int -> int = a + b
            let x = 5
            let y = 2
            show some x com y
            """));
    }
    @Test void declarationAfterCallIsCollected() throws Exception {
        assertEquals("7\n",run("show some 5 com 2\non some: a int, com: b int -> int = a + b"));
    }
    @Test void unaryMessageThenBooleanBlock() throws Exception {
        assertEquals("even\n",run("(42) even? then { show \"even\" }"));
    }
    @Test void thenOtherwiseDoUsesOneEnd() throws Exception {
        assertEquals("no\n",run("""
            (false) then do
                show "yes"
            otherwise do
                show "no"
            end
            """));
    }
    @Test void showAndReadAreOrdinaryCalls() {
        var program=new Frontend("messages.ma").parse("show: 42\nread \"prompt\"");
        var show=(Ast.Call)((Ast.Eval)program.body().get(0)).value();
        var read=(Ast.Call)((Ast.Eval)program.body().get(1)).value();
        assertEquals(java.util.List.of("show"),show.parts());assertEquals(java.util.List.of("read"),read.parts());
        assertNull(show.receiver());assertNull(read.receiver());
    }
    @Test void userCanDeclareShowAndRead() {
        var program=new Frontend("messages.ma").parse("on show: n int = n\non read: text str = text");
        assertEquals("show",((Ast.Method)program.body().get(0)).selector());
        assertEquals("read",((Ast.Method)program.body().get(1)).selector());
    }
    @Test void positionalParametersInOnePart() throws Exception {
        assertEquals("7\n",run("on some: [a int, b int] -> int = a + b\nshow some 5, 2"));
    }
    @Test void bareMinusIsSubtractionButSpacedNegativeIsArgument() throws Exception {
        assertEquals("3\n-2\n",run("let x = 5\nshow x-2\non identity: n int -> int = n\nshow identity -2"));
    }
    @Test void lambdaTypeHasFlattenedInputAndReturnArguments() {
        var declaration=(Ast.Bind)new Frontend("lambda.ma").parse("let fn lambda[[int, int] int] = |a int, b int| -> int { a + b }").body().getFirst();
        assertEquals("lambda[int,int,int]",declaration.type().toString());
    }
    @Test void genericTypeExpressionIsNotVectorArgument() {
        var declaration=(Ast.Bind)new Frontend("types.ma").parse("let x = (Box[str]) new: \"value\"").body().getFirst();
        var call=(Ast.Call)declaration.value();assertInstanceOf(Ast.TypeExpr.class,call.receiver());
        var vectorCall=(Ast.Call)((Ast.Eval)new Frontend("types.ma").parse("unknown [1, 2]").body().getFirst()).value();
        assertInstanceOf(Ast.Vector.class,vectorCall.args().getFirst());
    }
}
