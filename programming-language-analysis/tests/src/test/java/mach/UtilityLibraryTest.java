package mach;

import mach.frontend.Diagnostic;
import mach.runtime.RuntimeSupport;
import mach.runtime.UtilitiesLibrary;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class UtilityLibraryTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("utility.ma",source,"UtilityProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("UtilityProgram",code,0,code.length);}}.load();
        var output=new ByteArrayOutputStream();var before=System.out;
        try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(before);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void durationUnitsAndArithmeticPreserveExactValues() throws Exception {
        assertEquals("90s\n90000\n3.0\n",run("""
            let duration Duration = (1) minutes + (30) seconds
            show duration
            show (duration) as_millis
            show (((duration) *: 2) - (177) seconds) as_seconds
            """));
    }
    @Test void monotonicClockProducesAnElapsedDuration() throws Exception {
        assertEquals("true\ntrue\n",run("""
            let started = (Time) now
            let elapsed = (started) elapsed
            show elapsed isa Duration
            show (elapsed) as_seconds >= 0.0
            """));
    }
    @Test void nonfiniteDurationIsARecoverableFailure() throws Exception {
        assertEquals("invalid\n",run("""
            let infinity = (Math) infinity
            show ((infinity) seconds) rescue |failure| { "invalid" }
            """));
    }
    @Test void bitsRespectIntegerWidthForNegativeAndUnsignedValues() throws Exception {
        assertEquals("8\n128\ntrue\n",run("""
            let negative byte = -1
            let one ubyte = 1
            show (Bits) count negative
            show (Bits) reverse one
            show (Bits) parity 7
            """));
    }
    @Test void numberParsingKeepsUnsignedMaximumPrecision() throws Exception {
        assertEquals("18446744073709551615\n3.25\ninvalid\n",run("""
            show ((Number) parse "18446744073709551615") rescue |failure| { 0 }
            show ((Number) parse "3.25") rescue |failure| { 0 }
            show ((Number) parse "12x") rescue |failure| { "invalid" }
            """));
    }
    @Test void textUtilitiesUseUnicodeCharacters() throws Exception {
        assertEquals("1\na b\nc d\n",run("""
            show (Text) levenshtein "A😀", "A😊"
            show ((Text) wrap "a b c d" width 3) rescue |failure| { "invalid" }
            """));
    }
    @Test void indentationAndThousandsFormatting() throws Exception {
        assertEquals("hello\nworld\n-1,234,567\n",run("""
            show (Text) dedent "  hello\n  world"
            show (-1234567) format_thousands
            """));
    }
    @Test void invalidUtilityArgumentsAreRejectedBeforeBytecode() {
        for(String source:new String[]{"(Bits) count 1.0","(Text) levenshtein 1, 2","(Time) now 1","let x int = (1) seconds","(Number) parse 17"})
            assertThrows(Diagnostic.class,()->new Compiler().check("utility-invalid.ma",source));
    }
    @Test void decimalDurationRepresentationHasNoFixedWidthOverflow() {
        var context=new RuntimeSupport.Context();
        Object large=RuntimeSupport.integerText("18446744073709551615","ulong");
        var duration=(UtilitiesLibrary.DurationValue)UtilitiesLibrary.dispatch(context,large,"days",new Object[0],null);
        assertEquals(new BigDecimal("1593798687968505259536000"),duration.seconds());
    }
    @Test void abstractNumericTypesKeepTheirUtilityContracts()throws Exception {
        assertEquals("2s\n1.5\n",run("""
            let whole Integer = 2
            let duration Duration = (whole) seconds
            show duration
            let numbers vec[Number] = [1, 2]
            show (numbers) average
            """));
    }
}
