package mach;

import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.Diagnostic;
import mach.semantic.ObjectModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectModelTest {
    private static final Span S=new Span("object-model.ma",1,1);
    private static Type t(String name){return new Type(name);}
    private static Method method(String name,Expr expression){return new Method(null,List.of(name),List.of(),null,null,List.of(new Eval(expression,S)),S);}
    private static Literal text(String value){return new Literal(value,t("str"),S);}
    private static Ast.Record record(String name,Type parent,String... derives){return new Ast.Record(name,List.of(),parent,List.of(derives),List.of(),S);}
    private static Abstract dispatch(String name,Method... methods){return new Abstract(name,false,List.of(methods),S);}
    private static Abstract data(String name){return new Abstract(name,true,List.of(),S);}
    private static ObjectModel model(Stmt... statements){return new ObjectModel(new Ast.Program(List.of(statements)));}
    private static void rejects(String code,Stmt... statements){var e=assertThrows(Diagnostic.class,()->model(statements));assertEquals(code,e.code(),e.getMessage());}

    @Test void abstractMethodsBecomeIndependentAstForEachRecord(){
        Method original=method("start",text("Engine Started!"));
        var m=model(dispatch("Engine",original),record("Car",null,"Init","Engine"),record("Cycle",null,"Init","Engine"));
        assertEquals(2,m.derivedImplementations().size());
        Method car=(Method)m.derivedImplementations().get(0).body().getFirst();
        Method cycle=(Method)m.derivedImplementations().get(1).body().getFirst();
        assertEquals(t("Car"),car.receiver());assertEquals(t("Cycle"),cycle.receiver());
        assertNotSame(original,car);assertNotSame(car.body().getFirst(),cycle.body().getFirst());
        assertNotSame(((Eval)car.body().getFirst()).value(),((Eval)cycle.body().getFirst()).value());
    }
    @Test void abstractDataIsHierarchyRatherThanMethodBundle(){
        var m=model(data("Animal"),record("Dog",t("Animal"),"Init"),record("Cat",t("Animal"),"Init"));
        assertTrue(m.isSubtype(t("Dog"),t("Animal")));assertTrue(m.isSubtype(t("Cat"),t("Animal")));
        assertFalse(m.isSubtype(t("Animal"),t("Dog")));assertTrue(m.derivedImplementations().isEmpty());
    }
    @Test void concreteImplementationOverridesAnAbstractDefault(){
        var m=model(dispatch("Engine",method("start",text("default"))),record("Car",null,"Init","Engine"),new Impl(t("Car"),List.of(method("start",text("custom"))),S));
        assertTrue(m.derivedImplementations().isEmpty());
    }
    @Test void boundImplSpecializesEverySubtype(){
        var m=model(data("Animal"),record("Dog",t("Animal"),"Init"),record("Cat",t("Animal"),"Init"),record("Other",null,"Init"),new BoundedImpl("T",t("Animal"),List.of(method("sound",text("generic"))),S));
        assertEquals(List.of(t("Dog"),t("Cat")),m.derivedImplementations().stream().map(Impl::receiver).toList());
    }
    @Test void boundTypeVariableIsSubstitutedInSignaturesAndBody(){
        Method method=new Method(null,List.of("echo"),List.of(new Param("x",t("T"))),t("T"),null,List.of(new Bind("let","value",t("T"),new Name("x",S),S),new Eval(new Name("value",S),S)),S);
        var m=model(data("Animal"),record("Dog",t("Animal"),"Init"),new BoundedImpl("T",t("Animal"),List.of(method),S));
        Method expanded=(Method)m.derivedImplementations().getFirst().body().getFirst();
        assertEquals(t("Dog"),expanded.params().getFirst().type());assertEquals(t("Dog"),expanded.result());assertEquals(t("Dog"),((Bind)expanded.body().getFirst()).type());
    }
    @Test void concreteOverridesBoundedDefault(){
        var m=model(data("Animal"),record("Dog",t("Animal"),"Init"),new BoundedImpl("T",t("Animal"),List.of(method("sound",text("generic"))),S),new Impl(t("Dog"),List.of(method("sound",text("woof"))),S));
        assertTrue(m.derivedImplementations().isEmpty());
    }
    @Test void abstractCompositionOverridesBoundedDefault(){
        var m=model(data("Animal"),dispatch("Speaking",method("sound",text("composed"))),record("Dog",t("Animal"),"Init","Speaking"),new BoundedImpl("T",t("Animal"),List.of(method("sound",text("generic"))),S));
        assertEquals(1,m.derivedImplementations().size());assertEquals(1,m.derivedImplementations().getFirst().body().size());
        var result=(Literal)((Eval)((Method)m.derivedImplementations().getFirst().body().getFirst()).body().getFirst()).value();assertEquals("composed",result.value());
    }
    @Test void mostSpecificBoundWins(){
        var m=model(data("Animal"),record("Mammal",t("Animal"),"Init"),record("Dog",t("Mammal"),"Init"),new BoundedImpl("T",t("Animal"),List.of(method("sound",text("animal"))),S),new BoundedImpl("T",t("Mammal"),List.of(method("sound",text("mammal"))),S));
        Impl dog=m.derivedImplementations().stream().filter(i->i.receiver().equals(t("Dog"))).findFirst().orElseThrow();
        assertEquals(1,dog.body().size());var result=(Literal)((Eval)((Method)dog.body().getFirst()).body().getFirst()).value();assertEquals("mammal",result.value());
    }
    @Test void signIsAppliedWhenCopyingDefault(){
        var sign=new Sign(List.of(t("int")),t("str"),S);var method=new Method(null,List.of("describe"),List.of(new Param("n",null)),null,null,List.of(new Eval(text("ok"),S)),S);
        var m=model(new Abstract("Described",false,List.of(sign,method),S),record("Point",null,"Described"));
        Method result=(Method)m.derivedImplementations().getFirst().body().getFirst();assertEquals(t("int"),result.params().getFirst().type());assertEquals(t("str"),result.result());
    }
    @Test void guardsAndFallbackOrderArePreserved(){
        var guarded=new Method(null,List.of("check"),List.of(new Param("n",t("int"))),t("str"),new Literal(true,t("bool"),S),List.of(new Eval(text("first"),S)),S);
        var fallback=new Method(null,List.of("check"),List.of(new Param("n",t("int"))),t("str"),null,List.of(new Eval(text("fallback"),S)),S);
        var m=model(dispatch("Checkable",guarded,fallback),record("Value",null,"Checkable"));var body=m.derivedImplementations().getFirst().body();assertNotNull(((Method)body.get(0)).guard());assertNull(((Method)body.get(1)).guard());
    }
    @Test void unknownParentRejected(){rejects("M2311",record("Dog",t("Missing"),"Init"));}
    @Test void unknownDerivationRejected(){rejects("M2309",record("Dog",null,"Missing"));}
    @Test void duplicateDerivationRejected(){rejects("M2308",dispatch("Engine",method("start",text("ok"))),record("Car",null,"Engine","Engine"));}
    @Test void abstractDataCannotBeAComposition(){rejects("M2309",data("Animal"),record("Dog",null,"Animal"));}
    @Test void abstractDispatchCannotBeAParent(){rejects("M2311",dispatch("Engine",method("start",text("ok"))),record("Car",t("Engine")));}
    @Test void typeNamespaceIsShared(){rejects("M2301",data("Animal"),record("Animal",null,"Init"));}
    @Test void cyclicTypeAliasesRejectedWithDiagnostic(){rejects("M2302",new Alias("A",t("B"),S),new Alias("B",t("A"),S));}
    @Test void cyclicHierarchyRejected(){rejects("M2313",record("A",t("B")),record("B",t("A")));}
    @Test void privateAndPublicSlotNamesCannotCollide(){rejects("M2306",new Ast.Record("Person",List.of(),null,List.of("Init"),List.of(new Field("name",t("str"),List.of()),new Field("_name",t("str"),List.of())),S));}
    @Test void unknownFieldTypeRejected(){rejects("M2311",new Ast.Record("Person",List.of(),null,List.of("Init"),List.of(new Field("name",t("Unknown"),List.of())),S));}
    @Test void duplicateTypeParametersRejected(){rejects("M2305",new Ast.Record("Box",List.of("T","T"),null,List.of("Init"),List.of(),S));}
    @Test void unknownImplReceiverRejected(){rejects("M2311",new Impl(t("Unknown"),List.of(method("name",text("x"))),S));}
    @Test void abstractBundleCannotReceiveInstanceImpl(){rejects("M2311",dispatch("Engine",method("start",text("ok"))),new Impl(t("Engine"),List.of(method("name",text("x"))),S));}
    @Test void immutableBindingOfTypeObjectDefinesAnAlias(){
        var binding=new Bind("final","integer",new Type("type"),new Name("int",S),S);
        var value=new Ast.Record("Value",List.of(),null,List.of("Init"),List.of(new Field("number",t("integer"),List.of())),S);
        var m=model(binding,value);assertEquals(t("int"),m.aliases().get("integer"));
    }
    @Test void inferredBindingTypeAliasAcceptsParameterizedRecordType(){
        var box=new Ast.Record("Box",List.of("T"),null,List.of("Init"),List.of(new Field("_content",t("T"),List.of())),S);
        var binding=new Bind("let","text_box",null,new TypeExpr(new Type("Box",List.of(t("str"))),S),S);
        var value=new Ast.Record("Value",List.of(),null,List.of("Init"),List.of(new Field("box",t("text_box"),List.of())),S);
        var m=model(box,binding,value);assertEquals(new Type("Box",List.of(t("str"))),m.aliases().get("text_box"));
    }
    @Test void ordinaryValueBindingIsNotATypeAlias(){
        rejects("M2311",new Bind("let","integer",null,new Literal(42L,t("int"),S),S),new Ast.Record("Value",List.of(),null,List.of("Init"),List.of(new Field("number",t("integer"),List.of())),S));
    }
    @Test void regexIsAKnownFieldType(){
        assertEquals(1,model(new Ast.Record("Pattern",List.of(),null,List.of("Init"),List.of(new Field("regex",t("regex"),List.of())),S)).records().size());
    }
    @Test void recordGenericFieldTypesAreBound(){var m=model(new Ast.Record("Box",List.of("Value"),null,List.of("Init"),List.of(new Field("_content",t("Value"),List.of())),S));assertEquals(1,m.records().size());}
}
