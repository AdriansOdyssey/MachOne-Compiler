package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CoreLibraryTest {
    private String run(String source)throws Exception{byte[] code=new Compiler().compile("core.ma",source,"CoreProgram");Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("CoreProgram",code,0,code.length);}}.load();var output=new ByteArrayOutputStream();var previous=System.out;try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");}
    @Test void inclusiveUptoAndDowntoUseAnonymousTypedBlocks()throws Exception{assertEquals("1\n2\n3\n3\n2\n1\n",run("""
        (1) upto 3 => |n| { show n }
        (3) downto 1 => |n| { show n }
        """));}
    @Test void emptyNumericIterationsDoNotExecuteBlocks()throws Exception{assertEquals("done\n",run("""
        (3) upto 1 => |n| { show n }
        (1) downto 3 => |n| { show n }
        show "done"
        """));}
    @Test void numericStepAndCapturedState()throws Exception{assertEquals("22\n",run("""
        var @sum = 0
        (1) step: 10 every: 3 => |n| { @sum += n }
        show @sum
        """));}
    @Test void primitiveSuccessorsPreserveIntegerWidth()throws Exception{assertEquals("-128\n255\n128\n",run("""
        let n byte = 127
        let z ubyte = 0
        show (n) succ
        show (z) pred
        show ((-128) abs)
        """));}
    @Test void numericQueriesAndUtilities()throws Exception{assertEquals("true\ntrue\n-1\n6\n36\n[1, 2, 3]\n8\ntrue\n",run("""
        show (0) zero?
        show (-4) negative?
        show (-5) sign
        show (18) gcd: 24
        show (12) lcm: 18
        show (123) digits
        show (255) bit_length
        show (97) prime?
        """));}
    @Test void numericDomainFailuresAreRecoverable()throws Exception{assertEquals("#range\n#range\n",run("""
        show ((-1) sqrt) rescue |f| { (f) kind }
        show ((21) factorial) rescue |f| { (f) kind }
        """));}
    @Test void factorialKeepsUnsigned64Precision()throws Exception{assertEquals("2432902008176640000\n",run("show (20) factorial"));}
    @Test void numericFormattingBaseAndUnicodeConversions()throws Exception{assertEquals("ff\n3.14\nA\n",run("""
        show (255) to_str: 16
        show (3.14159) format: ".2"
        show (65) to_char
        """));}
    @Test void numericTypeBoundsAreExact()throws Exception{assertEquals("-128\n255\n18446744073709551615\n",run("show (byte) min_value\nshow (ubyte) max_value\nshow (ulong) max_value"));}
    @Test void unicodeStringLengthAndReverseUseCodePoints()throws Exception{assertEquals("3\nB😀A\n[A, 😀, B]\n",run("show (\"A😀B\") length\nshow (\"A😀B\") reverse\nshow (\"A😀B\") chars"));}
    @Test void unicodeStringIndexesAndSlicesAreConsistent()throws Exception{assertEquals("1\n😀\n",run("show ((\"A😀B\") index_of \"😀\") ?: -1\nshow ((\"A😀B\") slice 1 to 2) rescue |f| { \"invalid\" }"));}
    @Test void stringTransformsAndPredicates()throws Exception{assertEquals("Machone\na b\ntrue\ntrue\n",run("show (\"mACHONE\") capitalize\nshow (\"  a b  \") trim\nshow (\"42\") digit?\nshow (\"Ab\") alpha?"));}
    @Test void stringReplacementAndPaddingReturnNewValues()throws Exception{assertEquals("a.c\nx-x\n00abc\nabc00\nabc\n",run("""
        let original = "abc"
        show (original) replace: "b" with: "."
        show ("a-a") replace: "a" with: "x"
        show (original) pad_left: 5 with: "0"
        show (original) pad_right: 5 with: "0"
        show original
        """));}
    @Test void regexNamedGroupsAndQueries()throws Exception{assertEquals("true\n42\n[\"12\", \"34\"]\n",run("""
        show ("value42") match? ~"[0-9]+"
        let groups = (("value42") match ~"(?<digits>[0-9]+)") ?: #{}
        show groups's #digits ?: "missing"
        show (~"[0-9]+") scan: "a12b34"
        """));}
    @Test void characterMessagesAreUnicodeAware()throws Exception{assertEquals("128512\nÁ\n7\n#parse\n",run("show (`😀`) code\nshow (`á`) upcase\nshow (`7`) to_int\nshow ((`x`) to_int) rescue |f| { (f) kind }"));}
    @Test void functionalCompositionAndPartialApplication()throws Exception{assertEquals("22\n11\n",run("""
        let twice = |n int| { n * 2 }
        let next_number = |n int| { n + 1 }
        let composed = (twice) compose next_number
        show (composed) result 10
        let add = |a int, b int| { a + b }
        let add_five = (add) partial 5
        show (add_five) result 6
        """));}
    @Test void curryAndFlipPreserveArityAndTypes()throws Exception{assertEquals("7\n-7\n2\n",run("""
        let subtract = |a int, b int| { a - b }
        let curried = (subtract) curry
        let from_ten = (curried) result 10
        show (from_ten) result 3
        let flipped = (subtract) flip
        show (flipped) result 10, 3
        show (subtract) arity
        """));}
    @Test void memoizationCachesSuccessWithoutReevaluatingClosure()throws Exception{assertEquals("4\n4\n1\n",run("""
        var @count = 0
        let square = |n int| { @count += 1
            n * n }
        let memo = (square) memoize
        show (memo) result 2
        show (memo) result 2
        show @count
        """));}
    @Test void universalTapAndThenDoAreTyped()throws Exception{assertEquals("7\n7\n14\n",run("show ((7) tap |x| { show x })\nshow (7) then_do |x| { x * 2 }"));}
    @Test void mathModuleAndSeededRandomAreAvailable()throws Exception{assertEquals("3.0\ntrue\n",run("""
        show (Math) sqrt 9
        let first = (Random) seed 42
        let second = (Random) seed 42
        show ((first) int 1, 100) == ((second) int 1, 100)
        """));}
    @Test void failureCombinatorsConsumeOnlyHandledFailures()throws Exception{assertEquals("9\nnil\n",run("""
        let first = (Fail) raise #bad, "bad"
        show (first) unwrap_or 9
        let second = (Fail) raise #bad, "bad"
        show (second) maybe_value
        """));}
    @Test void corePrimitiveMethodsRemainOverridable()throws Exception{assertEquals("custom\n",run("impl str on upcase -> str = \"custom\"\nshow (\"abc\") upcase"));}
    @Test void booleanMessagesRunOnlyTheSelectedBranch()throws Exception{assertEquals("chosen\n2\n",run("""
        var @value = 0
        show (true) then { @value += 2
            "chosen" } otherwise { @value += 100
            "wrong" }
        show @value
        """));}
    @Test void timesIsAnOrdinaryMessageWithAnonymousBlockAndFailureEffect()throws Exception{assertEquals("3\ncaught\n",run("""
        var @count = 0
        (3) times => do
            @count += 1
        end
        show @count
        show ((2) times => do
            (Fail) raise #bad, "bad"
        end) rescue |f| { "caught" }
        """));}
    @Test void rejectsWrongNumericAndTextArguments(){for(String source:new String[]{"(2) clamp \"bad\", 3","(\"abc\") take \"1\"","(2) upto \"3\" => |x| { x }","(Math) sqrt true"})assertThrows(Diagnostic.class,()->new Compiler().check("invalid.ma",source));}
    @Test void rejectsFunctionalArityMismatch(){assertThrows(Diagnostic.class,()->new Compiler().check("invalid.ma","let fn=|x int| { x }\n(fn) flip"));}
    @Test void abstractNumericSupertypesRetainNativeContracts()throws Exception{assertEquals("7\ntrue\n",run("let value Number = -7\nshow (value) abs\nlet whole Integer = 8\nshow (whole) even?"));}
}
