package mach.frontend;

import mach.ast.Ast;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class LiteralDecoderTest {
    private static final Ast.Span SOURCE = new Ast.Span("literal.ma", 4, 2);

    @Test void integerStorageAndInferredTypesKeepAll64Bits() {
        var small = LiteralDecoder.integer("2_147_483_647", SOURCE);
        assertEquals(2147483647L, small.value());
        assertEquals(new Ast.Type("int"), small.type());
        var wide = LiteralDecoder.integer("2_147_483_648", SOURCE);
        assertEquals(2147483648L, wide.value());
        assertEquals(new Ast.Type("long"), wide.type());
        var unsigned = LiteralDecoder.integer("18_446_744_073_709_551_615", SOURCE);
        assertEquals(new BigInteger("18446744073709551615"), unsigned.value());
        assertEquals(new Ast.Type("ulong"), unsigned.type());
    }

    @Test void tooLargeIntegerReportsSourceDiagnostic() {
        var error = assertThrows(Diagnostic.class, () -> LiteralDecoder.integer("18446744073709551616", SOURCE));
        assertEquals("M1002", error.code());
        assertEquals(SOURCE, error.span());
    }

    @Test void charDecodesEscapesAndOneUnicodeCodePoint() {
        assertEquals("\n", LiteralDecoder.character("`\\n`", SOURCE).value());
        assertEquals("`", LiteralDecoder.character("`\\``", SOURCE).value());
        assertEquals("😀", LiteralDecoder.character("`😀`", SOURCE).value());
        assertThrows(Diagnostic.class, () -> LiteralDecoder.character("`AB`", SOURCE));
        assertThrows(Diagnostic.class, () -> LiteralDecoder.character("``", SOURCE));
    }

    @Test void rawAndRegexTextNeverDecodeEscapesOrVariables() {
        assertEquals("^\\d+$name", LiteralDecoder.regex("~\"^\\d+$name\"", SOURCE).value());
        assertEquals("line\\n$name", LiteralDecoder.rawString("\\\"line\\n$name\"", SOURCE).value());
    }

    @Test void heredocPreservesBodyAndValidatesItsMatchingTag() {
        assertEquals("$unknown\\n\nshow \"literal\"\n", LiteralDecoder.heredoc("begin{TEXT}\n$unknown\\n\nshow \"literal\"\nend{TEXT}", SOURCE).value());
        assertEquals("", LiteralDecoder.heredoc("begin{EMPTY}\nend{EMPTY}", SOURCE).value());
        assertEquals("line\r\n", LiteralDecoder.heredoc("begin{TEXT}\r\nline\r\n  end{TEXT}", SOURCE).value());
        assertThrows(Diagnostic.class, () -> LiteralDecoder.heredoc("begin{TEXT}\nline\nend{OTHER}", SOURCE));
    }
}
