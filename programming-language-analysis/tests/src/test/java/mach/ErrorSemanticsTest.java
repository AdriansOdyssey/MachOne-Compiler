package mach;

import mach.frontend.Diagnostic;
import mach.runtime.RuntimeSupport;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ErrorSemanticsTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("errors.ma",source,"ErrorProgram");
        Class<?> cls=new ClassLoader(getClass().getClassLoader()) {
            Class<?> program() {return defineClass("ErrorProgram",code,0,code.length);}
        }.program();
        var output=new ByteArrayOutputStream();PrintStream old=System.out;
        try {System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(old);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void maybeNilUsesFallbackButSuccessIsPreserved() throws Exception {
        assertEquals("fallback\nAdrian\n",run("let a maybe[str] = nil\nlet b maybe[str] = \"Adrian\"\nshow a ?: \"fallback\"\nshow b ?: \"wrong\""));
    }
    @Test void optionalValueCannotBeAssignedToPlainType() {
        assertThrows(Diagnostic.class,()->new Compiler().check("errors.ma","let a maybe[str] = nil\nlet b str = a"));
    }
    @Test void rescueWithNilIsIntentionalIgnoring() throws Exception {
        assertEquals("nil\n",run("let result = ((\"bad\") parse: int) rescue |f| { nil }\nshow result"));
    }
    @Test void elvisLeavesFailureForRescue() throws Exception {
        assertEquals("rescued\n",run("let value try[str] = (Fail) raise #invalid, \"bad\"\nshow (value ?: \"wrong\") rescue |f| { \"rescued\" }"));
    }
    @Test void rescueLeavesNilUntouched() throws Exception {
        assertEquals("nil\n",run("let value maybe[int] = nil\nshow value rescue |f| { 99 }"));
    }
    @Test void recoveringByReturningSameFailureStillFails() {
        var error=assertThrows(InvocationTargetException.class,()->run("let value = (Fail) raise #invalid, \"still failed\"\nvalue rescue |f| { f }"));
        assertInstanceOf(RuntimeSupport.MachFailure.class,error.getCause());
        assertEquals("still failed",error.getCause().getMessage());
    }
    @Test void recoveringOneOfTwoEqualFailuresDoesNotHideTheOther() {
        var error=assertThrows(InvocationTargetException.class,()->run("let first = (Fail) raise \"same\"\nlet second = (Fail) raise \"same\"\nfirst rescue |f| { nil }"));
        assertInstanceOf(RuntimeSupport.MachFailure.class,error.getCause());
    }
    @Test void failureReturnRemainsPendingUntilCallerHandlesIt() throws Exception {
        assertEquals("fallback\n",run("on parse: text str -> try[int]\nlet parsed = (text) parse: int\nif (parsed) fail?\nreturn parsed\nend\nparsed + 1\nend\nshow (parse: \"bad\") rescue |f| { \"fallback\" }"));
    }
    @Test void returningFailureWithoutCallerRescueAborts() {
        var error=assertThrows(InvocationTargetException.class,()->run("on parse: text str -> try[int]\nlet parsed = (text) parse: int\nif (parsed) fail?\nreturn parsed\nend\nparsed\nend\nlet ignored = parse: \"bad\""));
        assertInstanceOf(RuntimeSupport.MachFailure.class,error.getCause());
    }
    @Test void runtimeUnionDispatchPreservesWrappedArgumentTypes() throws Exception {
        assertEquals("ok\n",run("on accept: value try[int] -> str = \"ok\"\nshow accept: 42"));
    }
    @Test void failedNarrowingBranchMayConsumeErrorByInspection() throws Exception {
        assertEquals("handled\n",run("let parsed = (\"bad\") parse: int\nif (parsed) fail?\nshow \"handled\"\nend"));
    }
    @Test void rescueHandlerSeesTypedFailureKind() throws Exception {
        assertEquals("#negative\n",run("let value = (Fail) raise #negative, \"invalid\"\nshow value rescue |failure| { (failure) kind }"));
    }
}
