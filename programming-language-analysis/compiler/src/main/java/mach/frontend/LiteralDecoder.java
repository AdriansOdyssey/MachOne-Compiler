package mach.frontend;

import mach.ast.Ast.Literal;
import mach.ast.Ast.Span;
import mach.ast.Ast.Type;

import java.math.BigInteger;

/** Decodes literal text into source values; it never evaluates source or executes heredoc contents. */
public final class LiteralDecoder {
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger ULONG_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

    private LiteralDecoder() {}

    /** Integer tokens are unsigned magnitudes; a preceding minus remains an AST unary expression. */
    public static Literal integer(String token, Span span) {
        if (!token.matches("[0-9]+(?:_[0-9]+)*"))
            throw error("M1002", span, "literal inteiro inválido");
        BigInteger value;
        try {
            value = new BigInteger(token.replace("_", ""));
        } catch (NumberFormatException ex) {
            throw error("M1002", span, "literal inteiro inválido");
        }
        if (value.compareTo(ULONG_MAX) > 0)
            throw error("M1002", span, "literal inteiro fora do intervalo de 64 bits");
        String type = value.compareTo(INT_MAX) <= 0 ? "int" : value.compareTo(LONG_MAX) <= 0 ? "long" : "ulong";
        Object representation = value.compareTo(LONG_MAX) <= 0 ? value.longValueExact() : value;
        return new Literal(representation, new Type(type), span);
    }

    public static Literal floating(String token, Span span) {
        try {
            double value = Double.parseDouble(token.replace("_", ""));
            if (!Double.isFinite(value)) throw error("M1002", span, "literal decimal fora do intervalo de double");
            return new Literal(value, new Type("double"), span);
        } catch (NumberFormatException ex) {
            throw error("M1002", span, "literal decimal inválido");
        }
    }

    /** The runtime receives a String to preserve one Unicode code point, including non-BMP characters. */
    public static Literal character(String token, Span span) {
        if (token.length() < 3 || token.charAt(0) != '`' || token.charAt(token.length() - 1) != '`')
            throw error("M1005", span, "literal char deve usar crases e conter um caractere");
        String text = escapes(token.substring(1, token.length() - 1), span);
        if (text.codePointCount(0, text.length()) != 1)
            throw error("M1005", span, "literal char deve conter exatamente um caractere");
        return new Literal(text, new Type("char"), span);
    }

    /** Regex strings preserve backslashes and dollar signs; no interpolation or escape decoding occurs. */
    public static Literal regex(String token, Span span) {
        return new Literal(quotedBody(token, "~\"", span), new Type("regex"), span);
    }

    public static Literal rawString(String token, Span span) {
        return new Literal(quotedBody(token, "\\\"", span), new Type("str"), span);
    }

    /** Preserves the raw body between delimiter lines, including a final body newline if present. */
    public static Literal heredoc(String token, Span span) {
        if (!token.startsWith("begin{")) throw error("M1006", span, "abertura de heredoc inválida");
        int headerEnd = token.indexOf('}');
        if (headerEnd < 0) throw error("M1006", span, "abertura de heredoc sem fechamento");
        String tag = token.substring(6, headerEnd);
        if (!tag.matches("[A-Za-z_0-9]+")) throw error("M1006", span, "tag de heredoc inválida");
        int bodyStart = headerEnd + 1;
        if (token.startsWith("\r\n", bodyStart)) bodyStart += 2;
        else if (token.startsWith("\n", bodyStart)) bodyStart++;
        else throw error("M1006", span, "abertura de heredoc exige quebra de linha");

        int closeStart = token.lastIndexOf("end{");
        if (closeStart < bodyStart || !token.substring(closeStart).equals("end{" + tag + "}"))
            throw error("M1006", span, "fechamento de heredoc deve repetir a tag " + tag);
        int closingLineStart = token.lastIndexOf('\n', closeStart - 1) + 1;
        if (closingLineStart < bodyStart || !token.substring(closingLineStart, closeStart).chars().allMatch(ch -> ch == ' ' || ch == '\t' || ch == '\r'))
            throw error("M1006", span, "fechamento de heredoc deve ocupar sua própria linha");
        return new Literal(token.substring(bodyStart, closingLineStart), new Type("str"), span);
    }

    private static String quotedBody(String token, String opening, Span span) {
        if (!token.startsWith(opening) || token.length() < opening.length() + 1 || token.charAt(token.length() - 1) != '"')
            throw error("M1006", span, "literal de texto sem fechamento");
        return token.substring(opening.length(), token.length() - 1);
    }

    private static String escapes(String text, Span span) {
        var out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                if (++i >= text.length()) throw error("M1005", span, "escape incompleto no literal char");
                ch = switch (text.charAt(i)) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> text.charAt(i);
                };
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static Diagnostic error(String code, Span span, String message) {
        return new Diagnostic(code, span, message);
    }
}
