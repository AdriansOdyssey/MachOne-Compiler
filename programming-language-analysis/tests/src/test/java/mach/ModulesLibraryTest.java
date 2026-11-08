package mach;
import mach.frontend.Diagnostic;
import mach.runtime.RuntimeSupport;
import mach.runtime.SystemLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static mach.runtime.RuntimeSupport.*;

class ModulesLibraryTest {
    @TempDir Path temp;
    private String run(String source)throws Exception{byte[] code=new Compiler().compile("modules.ma",source,"ModuleProgram");Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("ModuleProgram",code,0,code.length);}}.load();var output=new ByteArrayOutputStream();var previous=System.out;try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");}
    @Test void textDataModulesRunThroughCompiledMessages()throws Exception{assertEquals("TWFjaE9uZQ==\nMachOne\n4d616368\nMach\n",run("""
        show (Base64) encode "MachOne"
        show ((Base64) decode "TWFjaE9uZQ==") rescue |f| { "invalid" }
        show (Hex) encode "Mach"
        show ((Hex) decode "4d616368") rescue |f| { "invalid" }
        """));}
    @Test void jsonRoundTripRetainsNumbersBooleansNilAndUnicode()throws Exception{assertEquals("{\"name\":\"Ana😀\",\"values\":[1,true,null]}\n",run("""
        let values vec[any] = [1, true, nil]
        let source = #{name: "Ana😀", values: values}
        let encoded str = (Json) generate source
        let decoded = ((Json) parse encoded) rescue |f| { #{} }
        show (Json) generate decoded
        """));}
    @Test void invalidJsonFailsWithParseTag(){var c=new Context();Object value=send(c,type("Json"),"parse",new Object[]{"{\"a\":01}"});assertInstanceOf(FailureValue.class,value);assertEquals("parse",((FailureValue)value).kind());acknowledge(c,value);finish(c);}
    @Test void csvParsesQuotedCommasAndNewlinesAndGeneratesNewDocument(){var c=new Context();Object rows=send(c,type("Csv"),"parse:header",new Object[]{"name,note\nAna,\"a,b\"\nBob,\"a\nb\"\n",true});assertInstanceOf(List.class,rows);assertEquals(2,((List<?>)rows).size());assertEquals("a,b",((Map<?,?>)((List<?>)rows).getFirst()).get(tag("note")));assertEquals("a\nb",((Map<?,?>)((List<?>)rows).getLast()).get(tag("note")));assertEquals("Ana,\"a,b\"\nBob,\"a\nb\"\n",send(c,type("Csv"),"generate",new Object[]{rows}));}
    @Test void fileMessagesWriteAppendReadAndReportMissingFile()throws Exception{var c=new Context();String file=temp.resolve("notes.txt").toString();assertNull(send(c,type("File"),"write:text",new Object[]{file,"A"}));assertNull(send(c,type("File"),"append:text",new Object[]{file,"😀\n"}));assertEquals("A😀\n",send(c,type("File"),"read",new Object[]{file}));assertEquals(integer(6,"long"),send(c,type("File"),"size",new Object[]{file}));assertEquals(true,send(c,type("File"),"exists?",new Object[]{file}));Object missing=send(c,type("File"),"read",new Object[]{file+".missing"});assertInstanceOf(FailureValue.class,missing);assertEquals("io",((FailureValue)missing).kind());}
    @Test void fileHandleClosesAfterCallbackEvenWhenReturned()throws Exception{String file=temp.resolve("closed.txt").toString();Files.writeString(Path.of(file),"text");var c=new Context();Object callback=new NativeFunction("lambda[FileHandle,FileHandle]",values->values[0]);Object handle=send(c,type("File"),"open:mode",new Object[]{file,tag("read")},callback);assertEquals("FileHandle",typeName(handle));Object read=send(c,handle,"read",new Object[0]);assertInstanceOf(FailureValue.class,read);assertTrue(((FailureValue)read).message().contains("fechado"));}
    @Test void pathAndDirectoryMessagesUseActualFilesystem()throws Exception{var c=new Context();String folder=temp.resolve("sub").toString();assertNull(send(c,type("Dir"),"create",new Object[]{folder}));String file=(String)send(c,type("Path"),"join",new Object[]{folder,"a.txt"});Files.writeString(Path.of(file),"a");assertEquals("a.txt",send(c,type("Path"),"base",new Object[]{file}));assertEquals(".txt",send(c,type("Path"),"ext",new Object[]{file}));assertEquals(List.of("a.txt"),send(c,type("Dir"),"list",new Object[]{folder}));assertEquals(true,send(c,type("Path"),"file?",new Object[]{file}));}
    @Test void environmentOverridesAreIsolatedBetweenProgramContexts(){var one=new Context();var two=new Context();String key="MACHONE_TEST_VAR_93842";assertNull(send(one,type("Env"),"set",new Object[]{key,"value"}));assertEquals("value",send(one,type("Env"),"get",new Object[]{key}));assertNull(send(two,type("Env"),"get",new Object[]{key}));}
    @Test void processArgumentsBelongToTheirContext(){var c=new Context();SystemLibrary.arguments(c,new String[]{"one","two"});assertEquals(List.of("one","two"),send(c,type("Process"),"args",new Object[0]));assertInstanceOf(NumberValue.class,send(c,type("Process"),"pid",new Object[0]));}
    @Test void hashAndUuidAndUrlHaveDeterministicContracts(){var c=new Context();assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",send(c,type("Hash"),"sha256",new Object[]{"abc"}));assertEquals("a+b%2Fc",send(c,type("Url"),"encode",new Object[]{"a b/c"}));assertEquals("a b/c",send(c,type("Url"),"decode",new Object[]{"a+b%2Fc"}));String uuid=(String)send(c,type("Uuid"),"v4",new Object[0]);assertEquals(uuid,send(c,type("Uuid"),"parse",new Object[]{uuid}));}
    @Test void datesPreserveLeapYearAndCalendarArithmetic()throws Exception{assertEquals("true\n2024-03-01\n1\n",run("""
        let d = ((Date) new 2024, 2, 29) rescue |f| { (Date) today }
        show (d) leap?
        let next_day = ((d) add_days 1) rescue |f| { d }
        show (next_day) to_str
        show (d) days_until next_day
        """));}
    @Test void modulesRejectInvalidArgumentsStatically(){for(String source:List.of("(File) read 1","(Json) parse true","(Base64) decode 9","(Date) new 2024, \"2\", 1","(Path) join 1, 2"))assertThrows(Diagnostic.class,()->new Compiler().check("invalid-module.ma",source),source);}
}
