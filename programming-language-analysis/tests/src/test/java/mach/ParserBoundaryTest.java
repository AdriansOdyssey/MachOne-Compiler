package mach;
import mach.ast.Ast;
import mach.frontend.Frontend;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParserBoundaryTest {
    @Test void commaAfterUnaryMessageSeparatesVectorElements() {
        var program=new Frontend("boundaries.ma").parse("let remaining = 2\nlet values = [(1) abs, remaining]");
        var vector=(Ast.Vector)((Ast.Bind)program.body().get(1)).value();
        assertEquals(2,vector.items().size());assertEquals("abs",((Ast.Call)vector.items().getFirst()).selector());assertEquals("remaining",((Ast.Name)vector.items().getLast()).name());
    }
    @Test void commaInsideObjectValueDoesNotConsumeNextKey() {
        var binding=(Ast.Bind)new Frontend("boundaries.ma").parse("let item = #{first: (1) abs, second: 2}").body().getFirst();
        assertEquals(List.of("first","second"),((Ast.ObjectLiteral)binding.value()).keys());
    }
    @Test void namedMessagePartsStillUseTheirOwnComma() {
        var program=new Frontend("boundaries.ma").parse("on plus: a int, with: b int = a + b\nlet value = plus: 1, with: 2");
        var call=(Ast.Call)((Ast.Bind)program.body().getLast()).value();assertEquals("plus:with",call.selector());assertEquals(2,call.args().size());
    }
    @Test void trailingFilterDoesNotTurnYieldNameIntoImplicitCall() {
        var binding=(Ast.Bind)new Frontend("boundaries.ma").parse("let xs = for x in 1..5 yield x if (x) even?").body().getFirst();
        var comp=(Ast.Comprehension)binding.value();assertEquals("x",((Ast.Name)comp.value()).name());assertEquals("even?",((Ast.Call)comp.filter()).selector());
    }
    @Test void elvisAfterParenthesizedSendKeepsExpressionPrecedence() {
        var binding=(Ast.Bind)new Frontend("boundaries.ma").parse("let value = ((#{}) maybe_get #missing) ?: 5").body().getFirst();
        assertInstanceOf(Ast.Binary.class,binding.value());assertEquals("?:",((Ast.Binary)binding.value()).op());
    }
    @Test void signSelectorAndMathSqrtAreContextualMessages() {
        var program=new Frontend("boundaries.ma").parse("let signum = (-5) sign\nlet root = (Math) sqrt: 9");
        assertEquals("sign",((Ast.Call)((Ast.Bind)program.body().getFirst()).value()).selector());assertEquals(1,((Ast.Call)((Ast.Bind)program.body().getLast()).value()).args().size());
    }
    @Test void compactCollectionSyntaxKeepsAdjacentElementsSeparate() {
        var program=new Frontend("compact.ma").parse("let xs = %[1 2 3]\nlet obj = %{type: \"Object\" style: \"Without Comma\"}");
        assertEquals(3,((Ast.CompactVector)((Ast.Bind)program.body().getFirst()).value()).items().size());assertEquals(List.of("type","style"),((Ast.ObjectLiteral)((Ast.Bind)program.body().getLast()).value()).keys());
    }
    @Test void optionalColonToPartIsDistinctFromAnExplicitRangeArgument() {
        var program=new Frontend("range-parts.ma").parse("let selected = (items) range 2 to 4\nlet actual_range = (items) range (2 to 4)");
        assertEquals("range:to",((Ast.Call)((Ast.Bind)program.body().getFirst()).value()).selector());assertEquals("range",((Ast.Call)((Ast.Bind)program.body().getLast()).value()).selector());
    }
}
