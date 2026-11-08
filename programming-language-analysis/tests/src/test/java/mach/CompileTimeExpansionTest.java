package mach;

import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import mach.semantic.CompileTimeExpander;
import mach.semantic.Analyzer;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CompileTimeExpansionTest {
    private static final Span ORIGIN=new Span("generated.ma",7,3);
    private static final Type INT=new Type("int"),STR=new Type("str"),TAG=new Type("tag");
    private static Literal integer(long value){return new Literal(value,INT,ORIGIN);}
    private static Literal text(String value){return new Literal(value,STR,ORIGIN);}
    private static Literal tag(String value){return new Literal(value,TAG,ORIGIN);}
    private static Name name(String value){return new Name(value,ORIGIN);}
    private static Get attr(String key){return new Get(name("attr"),tag(key),ORIGIN);}
    private static TemplateName generated(String prefix,Expr value){return new TemplateName(List.of(text(prefix),new Splice(value,ORIGIN)),ORIGIN);}
    private static TemplateMethod returning(Expr selector,Expr value,Type result){return new TemplateMethod(List.of(selector),List.of(),result,null,List.of(new Return(value,ORIGIN)),ORIGIN);}

    private String run(String source) throws Exception {
        byte[] bytes=new Compiler().compile("macro-source.ma",source,"MetaProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()) {Class<?> load(){return defineClass("MetaProgram",bytes,0,bytes.length);}}.load();
        var output=new ByteArrayOutputStream();PrintStream previous=System.out;
        try {System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void directivesFromSourceGenerateExecutableBytecode() throws Exception {
        assertEquals("20\n",run("""
            @for i in 0..3
                on mult_'(i) -> int
                    return '(i * 10)
                end
            @end
            show mult_2
            """));
    }
    @Test void conditionalSourceUsesPreviousConstantBinding() throws Exception {
        assertEquals("right\n",run("""
            let x = 10
            @if x == 11
                on selected = "wrong"
            @elsif x == 10
                on selected = "right"
            @else
                on selected = "fallback"
            @end
            show selected
            """));
    }
    @Test void abstractLabelTemplateFromSpecificationExecutes() throws Exception {
        assertEquals("150.0\n500.0\n50.0\n",run("""
            abstract Deposit
                @for attr in &attrs
                    @if (attr's #labels) has_value? #depositable
                        on deposit_'(attr's #name): value
                            self = self | {#'(attr's #name): self's #'(attr's #name) + value}
                        end
                        on get_'(attr's #name)
                            self's #'(attr's #name)
                        end
                    @end
                @end
            end
            record FlexWallet(Init, Deposit)
                depositable _main_balance double
                depositable _vault_balance double
                _reserve_balance double { depositable }
            end
            let wallet_inst = (FlexWallet) new: 0.0, 0.0, 0.0
            (wallet_inst) deposit_main_balance 150.0
            (wallet_inst) deposit_vault_balance 500.0
            (wallet_inst) deposit_reserve_balance 50.0
            show (wallet_inst) get_main_balance
            show (wallet_inst) get_vault_balance
            show (wallet_inst) get_reserve_balance
            """));
    }
    @Test void invalidGeneratedCodeFromSourceIsRejected() {
        var error=assertThrows(Diagnostic.class,()->new Compiler().check("typed-macro.ma","""
            @for i in 1..1
                on wrong_'(i) -> int
                    return '("wrong")
                end
            @end
            """));
        assertEquals("M2101",error.code());assertEquals("typed-macro.ma",error.span().file());
    }
    @Test void generatedConflictFromSourceIsRejected() {
        var error=assertThrows(Diagnostic.class,()->new Compiler().check("conflict.ma","""
            @for i in 1..2
                on duplicate = 1
            @end
            """));
        assertEquals("M2007",error.code());
    }

    @Test void rangeGeneratesIndependentMethodsAndValues() {
        var template=returning(generated("mult_",name("i")),new Splice(new Binary("*",name("i"),integer(10),ORIGIN),ORIGIN),INT);
        var loop=new CompileFor("i",new Range(integer(0),integer(3),integer(1),true,ORIGIN),List.of(template),ORIGIN);
        var program=new CompileTimeExpander().expand(new Ast.Program(List.of(loop)));
        assertEquals(4,program.body().size());
        for(int i=0;i<4;i++) {
            Method method=assertInstanceOf(Method.class,program.body().get(i));
            assertEquals("mult_"+i,method.selector());assertEquals(ORIGIN,method.span());
            Return ret=assertInstanceOf(Return.class,method.body().getFirst());
            assertEquals((long)i*10,assertInstanceOf(Literal.class,ret.value()).value());
        }
        assertInstanceOf(Splice.class,((Return)template.body().getFirst()).value());
    }
    @Test void conditionalUsesConstantBindingsAndSelectsElsif() {
        var bind=new Bind("let","x",null,integer(10),ORIGIN);
        var first=new Binary("==",name("x"),integer(11),ORIGIN);
        var second=new Binary("==",name("x"),integer(10),ORIGIN);
        var branch=new CompileIf(List.of(first,second),List.of(List.of(returning(text("wrong"),integer(0),INT)),List.of(returning(text("right"),integer(1),INT))),List.of(returning(text("fallback"),integer(2),INT)),ORIGIN);
        var program=new CompileTimeExpander().expand(new Ast.Program(List.of(bind,branch)));
        assertEquals("right",((Method)program.body().getLast()).selector());
    }
    @Test void rebindingConstantUsesPreviousBinding() {
        var loop=new CompileIf(List.of(new Binary("==",name("x"),integer(11),ORIGIN)),List.of(List.of(returning(text("eleven"),integer(11),INT))),List.of(),ORIGIN);
        var program=new CompileTimeExpander().expand(new Ast.Program(List.of(new Bind("let","x",null,integer(10),ORIGIN),new Bind("let","x",null,new Binary("+",name("x"),integer(1),ORIGIN),ORIGIN),loop)));
        assertEquals("eleven",((Method)program.body().getLast()).selector());
    }
    @Test void labelsGenerateOnlySelectedTypedRecordMethods() {
        var record=new Ast.Record("Wallet",List.of(),null,List.of("Init"),List.of(new Field("_balance",new Type("double"),List.of("depositable")),new Field("_note",STR,List.of())),ORIGIN);
        var read=new Get(name("self"),new DynamicTag(attr("name"),ORIGIN),ORIGIN);
        var update=new DynamicObject(List.of(new DynamicTag(attr("name"),ORIGIN)),List.of(new Binary("+",read,name("amount"),ORIGIN)),ORIGIN);
        var deposit=new TemplateMethod(List.of(generated("deposit_",attr("name"))),List.of(new Param("amount",new Type("double"))),new Type("nil"),null,List.of(new Assign("self","=",new Binary("|",name("self"),update,ORIGIN),ORIGIN)),ORIGIN);
        var predicate=new Call(attr("labels"),List.of("has_value?"),List.of(tag("depositable")),ORIGIN);
        var loop=new CompileFor("attr",name("&attrs"),List.of(new CompileIf(List.of(predicate),List.of(List.of(deposit)),List.of(),ORIGIN)),ORIGIN);
        var methods=new CompileTimeExpander().expand(List.of(loop),record,Map.of());
        assertEquals(1,methods.size());Method concrete=assertInstanceOf(Method.class,methods.getFirst());
        assertEquals("deposit_balance",concrete.selector());assertEquals(new Type("double"),concrete.params().getFirst().type());
        Assign assignment=assertInstanceOf(Assign.class,concrete.body().getFirst());
        ObjectLiteral literal=assertInstanceOf(ObjectLiteral.class,((Binary)assignment.value()).right());
        assertEquals(List.of("balance"),literal.keys());
        Get get=assertInstanceOf(Get.class,((Binary)literal.values().getFirst()).left());
        assertEquals("balance",assertInstanceOf(Literal.class,get.key()).value());
    }
    @Test void generatedCodeGetsOrdinaryTypeChecking() {
        var template=returning(text("wrong"),new Splice(text("not an int"),ORIGIN),INT);
        var expanded=new CompileTimeExpander().expand(new Ast.Program(List.of(template)));
        Diagnostic error=assertThrows(Diagnostic.class,()->new Analyzer().analyze(expanded));
        assertEquals("M2101",error.code());assertEquals(ORIGIN,error.span());
    }
    @Test void invalidGeneratedIdentifierReportsTemplateOrigin() {
        var template=returning(generated("bad_",text("with space")),integer(1),INT);
        Diagnostic error=assertThrows(Diagnostic.class,()->new CompileTimeExpander().expand(new Ast.Program(List.of(template))));
        assertEquals("M3011",error.code());assertEquals(ORIGIN,error.span());
    }
    @Test void runtimeIoCannotExecuteDuringCompilation() {
        var branch=new CompileIf(List.of(new Unary("read",text("never read"),ORIGIN)),List.of(List.of()),List.of(),ORIGIN);
        Diagnostic error=assertThrows(Diagnostic.class,()->new CompileTimeExpander().expand(new Ast.Program(List.of(branch))));
        assertEquals("M3008",error.code());
    }
    @Test void runtimeBindingDoesNotBecomeACompileTimeValue() {
        var binding=new Bind("let","x",null,new Unary("read",text("prompt"),ORIGIN),ORIGIN);
        var branch=new CompileIf(List.of(name("x")),List.of(List.of()),List.of(),ORIGIN);
        Diagnostic error=assertThrows(Diagnostic.class,()->new CompileTimeExpander().expand(new Ast.Program(List.of(binding,branch))));
        assertEquals("M3005",error.code());
    }
    @Test void zeroRangeStepIsRejected() {
        var loop=new CompileFor("i",new Range(integer(1),integer(3),integer(0),true,ORIGIN),List.of(),ORIGIN);
        Diagnostic error=assertThrows(Diagnostic.class,()->new CompileTimeExpander().expand(new Ast.Program(List.of(loop))));
        assertEquals("M3007",error.code());
    }
    @Test void negativeRangeStepWorks() {
        var template=returning(generated("n_",name("i")),new Splice(name("i"),ORIGIN),INT);
        var loop=new CompileFor("i",new Range(integer(3),integer(1),new Unary("-",integer(1),ORIGIN),true,ORIGIN),List.of(template),ORIGIN);
        var program=new CompileTimeExpander().expand(new Ast.Program(List.of(loop)));
        assertEquals(List.of("n_3","n_2","n_1"),program.body().stream().map(s->((Method)s).selector()).toList());
    }
}
