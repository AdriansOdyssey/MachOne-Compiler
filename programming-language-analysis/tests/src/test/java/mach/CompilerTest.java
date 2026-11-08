package mach;
import mach.frontend.Diagnostic;
import mach.cli.Main;
import mach.runtime.PersistentCollections;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    @TempDir Path temp;
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("test.ma",source,"TestProgram");
        assertEquals(65,((code[6]&255)<<8)|(code[7]&255));
        Class<?> cls=new ClassLoader(getClass().getClassLoader()) {Class<?> program(){return defineClass("TestProgram",code,0,code.length);}}.program();
        var out=new ByteArrayOutputStream();PrintStream previous=System.out;
        try {System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    private String runFile(Path file) throws Exception {
        byte[] code=new Compiler().compile(file.toString(),Files.readString(file),"TestProgram");
        assertEquals(65,((code[6]&255)<<8)|(code[7]&255));
        Class<?> cls=new ClassLoader(getClass().getClassLoader()) {Class<?> program(){return defineClass("TestProgram",code,0,code.length);}}.program();
        var out=new ByteArrayOutputStream();PrintStream previous=System.out;
        try {System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    private void rejects(String code,String source) {var ex=assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma",source));assertEquals(code,ex.code(),ex.getMessage());assertTrue(ex.getMessage().startsWith("bad.ma:"));}
    @Test void helloWorld() throws Exception {assertEquals("Hello, World!\n",run("show \"Hello, World!\""));}
    @Test void commentsAndDotSeparators() throws Exception {assertEquals("3\n",run("let x = 1. let y = 2\nshow x + y ; ignore this\n"));}
    @Test void rebindingUsesPreviousValue() throws Exception {assertEquals("11\n",run("let x = 10\nlet x = x + 1\nshow x"));}
    @Test void finalCannotRebind() {rejects("M2009","final x = 1\nlet x = 2");}
    @Test void immutableCannotAssign() {rejects("M2010","let x = 1\nx = 2");}
    @Test void mutableRequiresAtName() {rejects("M2008","var x = 1");}
    @Test void strongTyping() {rejects("M2101","let x int = \"42\"");}
    @Test void unresolvedName() {rejects("M2202","show missing");}
    @Test void byteRange() {rejects("M2103","let x byte = 128");}
    @Test void contextualIntegerType() throws Exception {assertEquals("[type \"long\"]\nfalse\n",run("let x long = 42\nshow (x) type\nshow x == 42"));}
    @Test void operatorPrecedence() throws Exception {assertEquals("14\n512\n-4\ntrue\n",run("show 2 + 3 * 4\nshow 2 ** 3 ** 2\nshow -2 ** 2\nshow not 1 == 2"));}
    @Test void bitwise() throws Exception {assertEquals("1\n7\n6\n-8\n14\n3\n",run("show 3 &&& 5\nshow 3 ||| 5\nshow 3 ^^^ 5\nshow ~~~7\nshow 7 <<< 1\nshow 7 >>> 1"));}
    @Test void strongEqualityAndSimilarity() throws Exception {assertEquals("false\ntrue\ntrue\n",run("show \"42\" == 42\nshow \"42\" like 42\nshow #ok like \"ok\""));}
    @Test void conditionalAndScope() throws Exception {assertEquals("inner\nouter\n",run("let x = \"outer\"\nif true\nlet x = \"inner\"\nshow x\nelse\nshow \"wrong\"\nend\nshow x"));}
    @Test void unlessAndElsif() throws Exception {assertEquals("yes\nelse-if\n",run("unless false\nshow \"yes\"\nend\nif false\nshow \"no\"\nelsif true\nshow \"else-if\"\nelse\nshow \"no\"\nend"));}
    @Test void loopsAndMutation() throws Exception {assertEquals("55\n",run("var @sum = 0\nfor i in 1..10\n@sum += i\nend\nshow @sum"));}
    @Test void exclusiveDescendingRange() throws Exception {assertEquals("5\n3\n",run("for i in 5 to 1 by -2\nshow i\nend"));}
    @Test void whileUntilBreakAndNext() throws Exception {assertEquals("1\n3\n",run("var @x = 0\nuntil @x == 5\n@x += 1\nif @x == 2\nnext\nend\nif @x == 4\nbreak\nend\nshow @x\nend"));}
    @Test void beingLoopInitialBindingIsInScope() throws Exception {assertEquals("0\n1\n2\n3\n4\n",run("being i = 0 while i < 5\nshow i\nlet i = i + 1\nend"));}
    @Test void moduleRequireLoadsImportedDeclarations() throws Exception {
        Path demo=temp.resolve("demo");Files.createDirectories(demo);
        Files.writeString(demo.resolve("lib.ma"),"module demo/lib\non greet = \"hello\"\n");
        Files.writeString(demo.resolve("app.ma"),"module demo/app\nrequire demo/lib\nshow greet\n");
        assertEquals("hello\n",runFile(demo.resolve("app.ma")));
    }
    @Test void moduleRequireSupportsSelectedItemAliases() throws Exception {
        Path demo=temp.resolve("demo_alias");Files.createDirectories(demo);
        Files.writeString(demo.resolve("lib.ma"),"module demo_alias/lib\non greet = \"hello\"\non farewell = \"bye\"\n");
        Files.writeString(demo.resolve("app.ma"),"module demo_alias/app\nrequire demo_alias/lib/{greet as hello, farewell as bye}\nshow hello\nshow bye\n");
        assertEquals("hello\nbye\n",runFile(demo.resolve("app.ma")));
    }
    @Test void persistentVectorPreservesPreviousVersion() {
        var base = (PersistentCollections.PersistentVector<Integer>) PersistentCollections.frozen(List.of(1, 2, 3));
        var next = base.appended(4);
        assertEquals(List.of(1, 2, 3), base);
        assertEquals(List.of(1, 2, 3, 4), next);
    }
    @Test void persistentMapPreservesPreviousVersion() {
        var base = (PersistentCollections.PersistentMap<String, Integer>) PersistentCollections.frozen(Map.of("one", 1, "two", 2));
        var next = base.with("three", 3);
        assertEquals(1, base.get("one"));
        assertEquals(2, base.get("two"));
        assertNull(base.get("three"));
        assertEquals(3, next.get("three"));
        assertEquals(1, next.get("one"));
    }
    @Test void selectedImportMustBeExported() throws Exception {
        Path demo=temp.resolve("demo_missing");Files.createDirectories(demo);
        Files.writeString(demo.resolve("lib.ma"),"module demo_missing/lib\non greet = \"hello\"\n");
        Files.writeString(demo.resolve("app.ma"),"module demo_missing/app\nrequire demo_missing/lib/{missing}\n");
        var ex=assertThrows(Diagnostic.class,()->new Compiler().check(demo.resolve("app.ma").toString(),Files.readString(demo.resolve("app.ma"))));
        assertEquals("M2405",ex.code());
    }
    @Test void stdlibCollectionsModuleResolvesFromStandardLibraryPath() throws Exception {
        assertEquals("2\n",run("module demo/app\nrequire collections\nshow ((Set[int]) new [1, 2]) length\n"));
    }
    @Test void stdlibMutableCollectionsNamespaceIsSeparatedFromPersistentOnes() throws Exception {
        Path ns=temp.resolve("ns");Files.createDirectories(ns);
        Files.writeString(ns.resolve("main.ma"),"module ns/app\nrequire collections\nrequire collections/mutable\nshow true\n");
        assertEquals("true\n",runFile(ns.resolve("main.ma")));
    }
    @Test void noBreakOutsideLoop() {rejects("M2012","break");}
    @Test void conditionsMustBeBoolean() {rejects("M2101","if 1\nshow 1\nend");}
    @Test void typedMessages() throws Exception {assertEquals("60.0\n",run("on line_total: price double, quantity: qty int -> double\nprice * qty\nend\nshow line_total: 20.0 quantity: 3"));}
    @Test void typedMessageRejectsWrongArgument() {rejects("M2202","on twice: n int -> int = n * 2\nshow twice: \"2\"");}
    @Test void returnTypeChecked() {rejects("M2101","on bad -> int = \"no\"");}
    @Test void signAppliedToMethod() throws Exception {assertEquals("10\n",run("sign { int -> int }\non twice: n = n * 2\nshow twice: 5"));}
    @Test void multipleDispatchAllArguments() throws Exception {assertEquals("is\nsi\n",run("on pick: a int, with: b str -> str = \"is\"\non pick: a str, with: b int -> str = \"si\"\nshow pick: 1 with: \"x\"\nshow pick: \"x\" with: 1"));}
    @Test void guardsInOrder() throws Exception {assertEquals("small\nmedium\nlarge\n",run("on size: n int, if n < 2 = \"small\"\non size: n int, if n < 5 = \"medium\"\non size: n int = \"large\"\nshow size: 1\nshow size: 3\nshow size: 9"));}
    @Test void methodsCaptureGlobalsAndShadowLocals() throws Exception {assertEquals("local\nglobal\n",run("final x = \"global\"\non sample\nlet x = \"local\"\nshow x\nend\nsample\nshow x"));}
    @Test void vectorTyping() throws Exception {assertEquals("3\n",run("let x vec[int] = [1, 2, 3]\nshow (x) length"));rejects("M2101","let x = %[1 \"bad\"]");}
    @Test void ternary() throws Exception {assertEquals("yes\n",run("show if 5 > 2 yield \"yes\" else \"no\""));}
    @Test void syntaxErrorHasLocation() {rejects("M1001","let x =\n");}
    @Test void recordProperties() throws Exception {assertEquals("1\n7\n",run("record Point(Init, Prop)\naccessible _x int\nend\nlet p = (Point) new: 1\nshow (p) x\n(p) x: 7\nshow (p) x"));}
    @Test void readableRejectsSetter() {rejects("M2202","record Point(Init, Prop)\nreadable _x int\nend\nlet p = (Point) new: 1\n(p) x: 2");}
    @Test void abstractCopiesCompiledMethods() throws Exception {assertEquals("started\n",run("abstract Engine\non start = \"started\"\nend\nrecord Car(Init, Engine)\nend\nlet car = (Car) new\nshow (car) start"));}
    @Test void executableJarWithoutCompilerClasspath() throws Exception {
        Path source=temp.resolve("main.ma");Files.writeString(source,"show 6 * 7");assertEquals(0,Main.execute(new String[]{"build",source.toString()}));
        try(var jar=new java.util.jar.JarFile(temp.resolve("main.jar").toFile())) {
            for(var entry:jar.stream().filter(e->e.getName().endsWith(".class")).toList()) {
                try(var in=jar.getInputStream(entry)) {
                    byte[] header=in.readNBytes(8);
                    assertEquals(65,((header[6]&255)<<8)|(header[7]&255),entry.getName());
                }
            }
        }
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xverify:all","-jar",temp.resolve("main.jar").toString()).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertEquals(0,process.waitFor(),output);assertEquals("42\n",output.replace("\r\n","\n"));
    }
    @Test void compilerClassesTargetJava21() throws Exception {
        try(var in=Compiler.class.getResourceAsStream("Compiler.class")) {
            assertNotNull(in);
            byte[] header=in.readNBytes(8);
            assertEquals(65,((header[6]&255)<<8)|(header[7]&255));
        }
    }
    @Test void lambdaBytecode() throws Exception {assertEquals("42\n",run("let twice = |x int| -> int { x * 2 }\nshow (twice) result 21"));}
    @Test void closureCapturesOldImmutableBinding() throws Exception {assertEquals("10\n",run("let x = 10\nlet f = || { x }\nlet x = 20\nshow (f) result"));}
    @Test void closureSharesMutableCell() throws Exception {assertEquals("3\n3\n",run("var @x = 1\nlet f = || { @x += 2. @x }\nshow (f) result\nshow @x"));}
    @Test void curryFromSpecification() throws Exception {assertEquals("8\n",run("let add = |a| { |b| { a + b } }\nlet add_five = (add) result 5\nshow (add_five) result 3"));}
    @Test void lambdaReturnIsLocal() throws Exception {assertEquals("7\n",run("let f = |x| { return x + 2 }\nshow (f) result 5"));}
    @Test void typedLambdaRejectsArgument() {rejects("M2101","let f = |x int| -> int { x }\n(f) result \"x\"");}
    @Test void lambdaRejectsArity() {rejects("M2201","let f = |x| { x }\n(f) result 1, 2");}
    @Test void recursiveFibonacciFromSpecification() throws Exception {assertEquals("55\n",run("on fib: n, if n < 2 = n\non fib: n = (fib: (n - 1)) + (fib: (n - 2))\nshow (fib: 10)"));}
    @Test void stringInterpolation() throws Exception {assertEquals("Hello Ana, 42!\n",run("let name = \"Ana\"\nshow \"Hello $name, $(6 * 7)!\""));}
    @Test void expressionInterpolationAndFormat() throws Exception {assertEquals("MACHONE 3.14\n",run("let name = \"machone\"\nlet n = 3.14159\nshow \"$((name) upcase) $n:.2\""));}
    @Test void literalDollarAndEscape() throws Exception {assertEquals("R$ 10 $name\n",run("show \"R$ 10 \\$name\""));}
    @Test void typeReceiverDispatch() throws Exception {assertEquals("8\n",run("record Math end\nimpl type[Math]\non sum: a int, with: b int -> int = a + b\nend\nshow (Math) sum: 5 with: 3"));}
    @Test void mostSpecificDispatch() throws Exception {assertEquals("specific\n",run("on pick: a, with: b -> str = \"general\"\non pick: a int, with: b int -> str = \"specific\"\nshow pick: 1 with: 2"));}
    @Test void incomparableDispatchRejectedStatically() {rejects("M2203","on pick: a int, with: b = 1\non pick: a, with: b int = 2\npick: 1 with: 2");}
    @Test void parseMessageConversionAndRescue() throws Exception {assertEquals("42\n7\n",run("let x = (\"42\") parse: int\nshow x rescue |f| { 0 }\nshow ((\"bad\") parse: int) rescue |f| { 7 }"));}
    @Test void castOperatorReturnsDirectValue() throws Exception {assertEquals("42\n",run("show \"42\"::int"));}
    @Test void failableDivision() throws Exception {assertEquals("3\n9\n",run("show (10 // 3) rescue |f| { 0 }\nshow (10 // 0) rescue |f| { 9 }"));}
    @Test void castOperatorFailureAborts() throws Exception {var e=assertThrows(InvocationTargetException.class,()->run("let x = \"bad\"::int"));assertInstanceOf(mach.runtime.RuntimeSupport.MachFailure.class,e.getCause());}
    @Test void explicitFailureRescued() throws Exception {assertEquals("fallback\n",run("let x = (Fail) raise #negative, \"invalid\"\nshow x rescue |f| { \"fallback\" }"));}
    @Test void successfulRescueDoesNotRunHandler() throws Exception {assertEquals("42\n",run("var @calls = 0\nlet x = 42 rescue |f| { @calls += 1. 0 }\nshow x + @calls"));}
    @Test void conversionRangeFailure() throws Exception {assertEquals("7\n",run("show ((\"256\") parse: byte) rescue |f| { let x byte = 7. x }"));}
    @Test void abstractConstraintAndConcreteIdentity() throws Exception {assertEquals("[type \"int\"]\n",run("let x &Number = 42\nshow (x) type"));}
    @Test void abstractConstraintRejectsIncompatibleType() {rejects("M2101","let x &Number = \"abc\"");}
    @Test void castOperatorExpression() throws Exception {assertEquals("42\n42\n3\nA\n",run("show \"42\"::int\nshow 42::long\nshow 3.14::int\nshow 65::char"));}
    @Test void parseSupportsUnwrapAndMaybeParse() throws Exception {assertEquals("42\nnil\n42\n",run("show ((\"42\") parse: int) unwrap\nshow (\"abc\") maybe_parse: int\nshow (\"42\") maybe_parse: int"));}
    @Test void caretSyntaxIsDisabled() {rejects("M1001","show \"42\"^int");}
    @Test void plainTypedBindingDoesNotConvert() {rejects("M2101","let n int = \"13\"");}
    @Test void abstractAnyAcceptsUserType() throws Exception {assertEquals("[type \"Thing\"]\n",run("record Thing(Init)\nvalue int\nend\nlet x &Any = (Thing) new: 1\nshow (x) type"));}
    @Test void abstractVectorSupportsHeterogeneousSubtypes() throws Exception {
        assertEquals("[Dog{name: \"Rex\"}, Cat{name: \"Mimi\"}]\n",run("abstract data Animal end\nrecord Dog & Animal(Init, Prop, Repr)\naccessible _name str\nend\nrecord Cat & Animal(Init, Prop, Repr)\naccessible _name str\nend\nlet rex = (Dog) new: \"Rex\"\nlet mimi = (Cat) new: \"Mimi\"\nlet animals vec[&Animal] = [rex, mimi]\nshow animals"));
    }
    @Test void boundTypeVariableVectorRemainsHomogeneous() {
        rejects("M2101","abstract data Animal end\nrecord Dog & Animal(Init)\n_name str\nend\nrecord Cat & Animal(Init)\n_name str\nend\nlet rex = (Dog) new: \"Rex\"\nlet mimi = (Cat) new: \"Mimi\"\nlet animals vec[T & Animal] = [rex, mimi]");
    }
    @Test void parseMessageReturnsTryThatRequiresHandling() {rejects("M2101","let n int = (\"42\") parse: int");}
    @Test void inferMethodReturn() throws Exception {assertEquals("42\n",run("on answer = 42\nlet x int = answer\nshow x"));}
    @Test void bindingSign() throws Exception {assertEquals("[type \"long\"]\n",run("sign { long }\nlet x = 5\nshow (x) type"));}
    @Test void contextualFloat() throws Exception {assertEquals("[type \"float\"]\n",run("let x float = 3.14\nshow (x) type"));}
    @Test void bitwiseRejectsFloat() {rejects("M2104","show ~~~3.14");}
    @Test void untypedMessageUsesActualOperators() throws Exception {assertEquals("20 20 20\n",run("on subtotal: price, quantity: qty = price * qty\nshow subtotal: \"20\" quantity: 3"));}
    @Test void genericRepeatedTypeConstraint() throws Exception {assertEquals("true\n",run("sign { T, T -> bool }\non matches: a, against: b = a == b\nshow matches: 42 against: 42"));}
    @Test void genericRejectsConflictingTypes() {rejects("M2202","sign { T, T -> bool }\non matches: a, against: b = a == b\nmatches: \"foo\" against: 42");}
    @Test void genericReturnSubstitution() throws Exception {assertEquals("42\n",run("on identity: x T -> T = x\nlet y int = identity: 42\nshow y"));}
    @Test void privateFieldInsideImplAndSelfMutation() throws Exception {assertEquals("PhoneZ\n",run("record Device(Init)\n_model_name str\nend\nimpl Device\non set_model: value str\nself = self | {#model_name: value}\nend\non get_model -> str = self's #model_name\nend\nlet phone = (Device) new: \"PhoneX\"\n(phone) set_model: \"PhoneZ\"\nshow (phone) get_model"));}
    @Test void privateFieldRejectedOutsideImpl() {rejects("M2108","record Device(Init)\n_value int\nend\nlet x = (Device) new: 1\nshow x's #value");}
    @Test void publicField() throws Exception {assertEquals("42\n",run("record Item(Init)\nvalue int\nend\nlet x = (Item) new: 42\nshow x's #value"));}
    @Test void selfUpdateIsStaticallyChecked() {rejects("M2101","record Item(Init)\n_value int\nend\nimpl Item\non bad\nself = self | {#value: \"bad\"}\nend\nend");}
    @Test void labelIntrospection() throws Exception {assertEquals("[{#name: \"value\", #type: int, #labels: [#readable]}]\n",run("record Item(Init)\nreadable _value int\nend\nimpl Item\non metadata = &attrs\nend\nlet x = (Item) new: 42\nshow (x) metadata"));}
    @Test void metadataOutsideRecordRejected() {rejects("M2013","show &attrs");}
    @Test void readSelectorFromNormativeExample() throws Exception {assertEquals("Lendo SSD\n",run("record SSD(Init)\nend\nimpl SSD\non read = \"Lendo SSD\"\nend\nlet x = (SSD) new\nshow (x) read"));}
    @Test void bindingShadowsUnaryMessage() throws Exception {assertEquals("2\n",run("on value = 1\nlet value = 2\nshow value"));}
    @Test void originalSpecificationUnmodified() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        for(String line:Files.readAllLines(root.resolve("specification/SHA256SUMS"))) {
            var pair=line.split("  ",2);byte[] bytes=Files.readAllBytes(root.resolve("specification").resolve(pair[1]));
            assertEquals(pair[0],HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),pair[1]);
        }
    }
}
