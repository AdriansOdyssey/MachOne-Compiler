package mach;

import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PrimitiveTypesTest {
    private String run(String source) throws Exception {
        byte[] code = new Compiler().compile("types.ma", source, "PrimitiveProgram");
        Class<?> program = new ClassLoader(getClass().getClassLoader()) {
            Class<?> loadProgram() { return defineClass("PrimitiveProgram", code, 0, code.length); }
        }.loadProgram();
        var output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            program.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(previous);
        }
        return output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test void allSignedAndUnsignedPrimitiveTypesHaveCheckedContextualLiterals() throws Exception {
        assertEquals("-128\n255\n-32768\n65535\n-2147483648\n4294967295\n-9223372036854775808\n18446744073709551615\n", run("""
            let signed_byte byte = -128
            let unsigned_byte ubyte = 255
            let signed_short short = -32_768
            let unsigned_short ushort = 65_535
            let signed_int int = -2_147_483_648
            let unsigned_int uint = 4_294_967_295
            let signed_long long = -9_223_372_036_854_775_808
            let unsigned_long ulong = 18_446_744_073_709_551_615
            show signed_byte
            show unsigned_byte
            show signed_short
            show unsigned_short
            show signed_int
            show unsigned_int
            show signed_long
            show unsigned_long
            """));
    }

    @Test void primitiveTypeMessagesPreserveDeclaredNumericIdentity() throws Exception {
        assertEquals("[type \"byte\"]\n[type \"ubyte\"]\n[type \"short\"]\n[type \"ushort\"]\n[type \"int\"]\n[type \"uint\"]\n[type \"long\"]\n[type \"ulong\"]\n[type \"float\"]\n[type \"double\"]\n", run("""
            let a byte = 1
            let b ubyte = 1
            let c short = 1
            let d ushort = 1
            let e int = 1
            let f uint = 1
            let g long = 1
            let h ulong = 1
            let i float = 3.14
            let j double = 3.14
            show (a) type
            show (b) type
            show (c) type
            show (d) type
            show (e) type
            show (f) type
            show (g) type
            show (h) type
            show (i) type
            show (j) type
            """));
    }

    @Test void integralLiteralsInferIntLongOrUlongByMagnitude() throws Exception {
        assertEquals("[type \"int\"]\n[type \"long\"]\n[type \"ulong\"]\n", run("""
            let small = 2_147_483_647
            let wide = 2_147_483_648
            let unsigned = 18_446_744_073_709_551_615
            show (small) type
            show (wide) type
            show (unsigned) type
            """));
    }

    @Test void integralContextRejectsOutOfRangeValues() {
        String[] declarations = {
            "let value byte = 128", "let value byte = -129",
            "let value ubyte = 256", "let value ubyte = -1",
            "let value short = 32768", "let value short = -32769",
            "let value ushort = 65536", "let value ushort = -1",
            "let value int = 2147483648", "let value int = -2147483649",
            "let value uint = 4294967296", "let value uint = -1",
            "let value long = 9223372036854775808", "let value long = -9223372036854775809",
            "let value ulong = -1"
        };
        for (String source : declarations) {
            Diagnostic error = assertThrows(Diagnostic.class, () -> new Compiler().check("range.ma", source), source);
            assertEquals("M2103", error.code(), source + ": " + error.getMessage());
        }
    }

    @Test void beyondUnsigned64IsRejectedBeforeExecution() {
        Diagnostic error = assertThrows(Diagnostic.class, () -> new Compiler().check("too-large.ma", "let value = 18446744073709551616"));
        assertEquals("M1002", error.code());
    }

    @Test void unsigned64ValuesCompareAndSubtractWithoutDoubleRounding() throws Exception {
        assertEquals("true\nfalse\n1\n", run("""
            let high ulong = 18446744073709551615
            let low ulong = 18446744073709551614
            show high > low
            show high == low
            show high - low
            """));
    }

    @Test void charIsASeparateTypeFromString() throws Exception {
        assertEquals("A\n[type \"char\"]\nfalse\n", run("""
            let letter char = `A`
            show letter
            show (letter) type
            show letter == "A"
            """));
    }

    @Test void charPreservesOneUnicodeCodePoint() throws Exception {
        assertEquals("😀\n", run("let symbol char = `😀`\nshow symbol"));
    }

    @Test void regexLiteralHasItsOwnTypeAndDoesNotInterpolate() throws Exception {
        assertEquals("[type \"regex\"]\n", run("let pattern = ~\"^\\d+$name$\"\nshow (pattern) type"));
    }

    @Test void rawStringPreservesBackslashAndDollar() throws Exception {
        assertEquals("line\\n$name\n", run("let raw = \\\"line\\n$name\"\nshow raw"));
    }

    @Test void heredocBodyIsRawTextAndIsNotExecuted() throws Exception {
        String program = "let text = begin{TEXT}\n$unknown\\n\nshow \"literal\"\nend{TEXT}\nshow text\n";
        assertEquals("$unknown\\n\nshow \"literal\"\n\n", run(program));
    }

    @Test void typeAliasesAndFirstClassTypeBindingsRemainStatic() throws Exception {
        assertEquals("7\n[type \"int\"]\n[type \"int\"]\n", run("""
            data Integer = int
            final integer type = int
            let first Integer = 7
            let second integer = 8
            show first
            show (second) type
            show integer
            """));
    }

    @Test void constrainedTypeValueRejectsAnotherType() {
        Diagnostic error = assertThrows(Diagnostic.class, () -> new Compiler().check("wrong-type-value.ma", "let text_type type[str] = int"));
        assertEquals("M2101", error.code());
    }

    @Test void unionAliasAcceptsMembersAndRejectsOtherTypes() throws Exception {
        assertEquals("42\ntext\n", run("data Id = union[int, str]\nlet a Id = 42\nlet b Id = \"text\"\nshow a\nshow b"));
        Diagnostic error = assertThrows(Diagnostic.class, () -> new Compiler().check("wrong-union.ma", "data Id = union[int, str]\nlet value Id = true"));
        assertEquals("M2101", error.code());
    }
    @Test void integralLiteralCanReceiveFloatingContext() throws Exception {
        assertEquals("1.0\n2.0\n",run("let first float = 1\nlet second double = 2\nshow first\nshow second"));
    }
}
