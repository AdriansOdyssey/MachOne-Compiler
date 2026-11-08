package mach;

import mach.ast.Ast;
import mach.ast.Ast.Type;
import mach.frontend.Diagnostic;
import mach.semantic.TypeSystem;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompositionDerivationTest {
    private String run(String source)throws Exception {
        byte[] bytecode=new Compiler().compile("derivations.ma",source,"DerivedProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("DerivedProgram",bytecode,0,bytecode.length);}}.load();
        var output=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    private void reject(String code,String source) {
        Diagnostic error=assertThrows(Diagnostic.class,()->new Compiler().check("derivations.ma",source));assertEquals(code,error.code(),error.getMessage());
    }
    @Test void vecDerivationPreservesAttributeOrderAndTypes()throws Exception {
        assertEquals("[4, \"Ana\"]\n",run("record Person(Init, Vec)\n_age int\n_name str\nend\nlet person = (Person) new 4, \"Ana\"\nshow (person) to_vec"));
    }
    @Test void explicitToVecOverridesDerivation()throws Exception {
        assertEquals("[9]\n",run("record Item(Init, Vec)\n_value int\nend\nimpl Item\non to_vec = [9]\nend\nshow ((Item) new 1) to_vec"));
    }
    @Test void structuralCompositionImportsFieldsMethodsAndProperties()throws Exception {
        assertEquals("Ana #7\nAna\nBia #7\n",run("""
            record Base(Init, Prop)
                accessible _name str
            end
            impl Base
                on label: code int = "$(self's #name) #$code"
            end
            record Store(Init)
                use Base as info
                id int
            end
            let store = (Store) new (info) has "Ana", 7
            show (store) label (store's #id)
            show (store) name
            (store) name "Bia"
            show (store) label (store's #id)
            """));
    }
    @Test void compositionSubstitutesParameterizedFieldsAndMessages()throws Exception {
        assertEquals("stored\n",run("""
            record Content[T](Init)
                _value T
            end
            impl Content
                on peek -> T = self's #value
            end
            record Envelope(Init)
                use Content[str] as content
            end
            let envelope = (Envelope) new (content) has "stored"
            let value str = (envelope) peek
            show value
            """));
    }
    @Test void finalMethodsOverrideComposedMessages()throws Exception {
        assertEquals("final\n",run("record Base(Init)\nend\nimpl Base\non label = \"base\"\nend\nrecord Final(Init)\nuse Base as base\nend\nimpl Final\non label = \"final\"\nend\nshow ((Final) new) label"));
    }
    @Test void duplicateFlattenedFieldsAreRejected() {reject("M2306","record Base(Init)\nname str\nend\nrecord Final(Init)\nuse Base as base\nname str\nend");}
    @Test void compositionCyclesAreRejected() {reject("M2317","record First(Init)\nuse Second as second\nend\nrecord Second(Init)\nuse First as first\nend");}
    @Test void constructorGroupsAreCheckedAgainstTheirUsedType() {
        reject("M2101","record Base(Init)\nname str\nend\nrecord Final(Init)\nuse Base as info\nend\n(Final) new (info) has 123");
        reject("M2322","record Base(Init)\na int\nb int\nend\nrecord Final(Init)\nuse Base as info\nend\n(Final) new (info) has 1");
    }
    @Test void ordUsesDeclaredFieldOrderRatherThanHashOrIdentity()throws Exception {
        assertEquals("true\nfalse\n-1\n",run("record Key(Init, Ord)\n_group str\n_number int\nend\nlet first = (Key) new \"a\", 99\nlet second = (Key) new \"b\", 1\nshow first < second\nshow first >= second\nshow (first) compare second"));
    }
    @Test void iterAndVecObserveSameDeclaredFieldOrder()throws Exception {
        assertEquals("2\n3\n2\n3\n[2, 3]\n",run("record Pair(Init, Iter, Vec)\n_first int\n_second int\nend\nlet pair = (Pair) new 2, 3\nfor item in pair\nshow item\nend\n(pair) each |item| { show item }\nshow (pair) to_vec"));
    }
    @Test void blockCallsItsUniqueLambdaFieldWithTypedArguments()throws Exception {
        assertEquals("12\n",run("record Multiplier(Init, Block)\n_code lambda[[int] int]\n_name str\nend\nlet multiplier = (Multiplier) new |value int| { value * 3 }, \"triple\"\nlet result int = (multiplier) value 4\nshow result"));
    }
    @Test void blockRejectsAmbiguousLambdaFieldSelection() {reject("M2323","record Ambiguous(Init, Block)\n_first lambda[[int] int]\n_second lambda[[int] int]\nend");}
    @Test void proxyRequiresAUniqueDefaultTarget() {
        reject("M2315","record Target(Init)\nend\nrecord ProxyTarget(Init, Proxy)\n_value Target\nend");
        reject("M2315","record Target(Init)\nend\nrecord ProxyTarget(Init, Proxy)\nproxied _first Target\nproxied _second Target\nend");
    }
    @Test void staticProxyCyclesAreRejectedBeforeDispatch() {reject("M2316","record First(Init, Proxy)\nproxied _next Second\nend\nrecord Second(Init, Proxy)\nproxied _next First\nend");}
    @Test void proxySubtypingSubstitutesGenericTarget() {
        var span=new Ast.Span("types.ma",1,1);
        var wrapper=new Ast.Record("Wrapper",List.of("T"),null,List.of("Proxy"),List.of(new Ast.Field("_target",new Type("T"),List.of("proxied"))),span);
        var types=new TypeSystem(Map.of(),Map.of("Wrapper",wrapper));
        assertTrue(types.assignable(new Type("Wrapper",List.of(new Type("str"))),new Type("str")));
        assertFalse(types.assignable(new Type("Wrapper",List.of(new Type("str"))),new Type("int")));
    }
    @Test void coreHierarchyPreservesStrongNumericTypes() {
        var types=new TypeSystem(Map.of(),Map.of());
        assertTrue(types.assignable(new Type("int"),new Type("Signed")));
        assertTrue(types.assignable(new Type("uint"),new Type("Integer")));
        assertTrue(types.assignable(new Type("float"),new Type("Number")));
        assertFalse(types.assignable(new Type("float"),new Type("Integer")));
        assertFalse(types.assignable(new Type("int"),new Type("long")));
        assertTrue(types.assignable(new Type("vec",List.of(new Type("int"))),new Type("Collection")));
    }
    @Test void importedExplicitGetterOverridesComponentPropertyDefault()throws Exception {
        assertEquals("override\n",run("""
            record Base(Init, Prop)
                readable _name str
            end
            impl Base
                on name -> str = "override"
            end
            record Final(Init)
                use Base as info
            end
            show ((Final) new (info) has "stored") name
            """));
    }
    @Test void compositionKeepsOrdinaryNamesBeginningWithInitialize()throws Exception {
        assertEquals("ready\n",run("""
            record Base(Init)
            end
            impl Base
                on initialize_status -> str = "ready"
            end
            record Final(Init)
                use Base as info
            end
            show ((Final) new) initialize_status
            """));
    }
    @Test void compileTimeAttributesIncludeComposedFieldsAndLabels()throws Exception {
        assertEquals("5\n",run("""
            record Part(Init)
                readable _value int
            end
            record Whole(Init)
                use Part as part
            end
            impl Whole
                @for attr in &attrs
                    on get_'(attr's #name)
                        self's #'(attr's #name)
                    end
                @end
            end
            show ((Whole) new (part) has 5) get_value
            """));
    }
    @Test void iterStopsAndPropagatesTheFirstFailure()throws Exception {
        assertEquals("#bad\n1\n",run("""
            record Values(Init, Iter)
                _one int
                _two int
            end
            var @count = 0
            let values = (Values) new 1, 2
            ((values) each |value int| {
                @count += 1
                (Fail) raise #bad, "stop"
            }) rescue |failure| { show (failure) kind }
            show @count
            """));
    }
    @Test void compositionAcceptsPreviouslyBoundTypeAliases()throws Exception {
        assertEquals("3\n",run("""
            record Part(Init)
                value int
            end
            final Component type = Part
            record Whole(Init)
                use Component as part
            end
            show ((Whole) new (part) has 3)'s #value
            """));
    }
    @Test void nativeLibraryHierarchyDoesNotChangeAUserRecordOfTheSameName() {
        var span=new Ast.Span("types.ma",1,1);
        var user=new Ast.Record("Dictionary",List.of("K","V"),null,List.of("Init"),List.of(new Ast.Field("key",new Type("K"),List.of()),new Ast.Field("value",new Type("V"),List.of())),span);
        var types=new TypeSystem(Map.of(),Map.of("Dictionary",user));
        assertFalse(types.assignable(new Type("Dictionary",List.of(new Type("str"),new Type("int"))),new Type("Mapping")));
        assertTrue(new TypeSystem(Map.of(),Map.of()).assignable(new Type("Dictionary",List.of(new Type("str"),new Type("int"))),new Type("Mapping")));
    }
    @Test void derivedAndNativeValuesBelongToThePublishedLibraryProtocols() {
        var span=new Ast.Span("types.ma",1,1);
        var ordered=new Ast.Record("Ordered",List.of(),null,List.of("Ord"),List.of(new Ast.Field("value",new Type("int"),List.of())),span);
        var iterable=new Ast.Record("Fields",List.of(),null,List.of("Iter"),List.of(new Ast.Field("value",new Type("int"),List.of())),span);
        var types=new TypeSystem(Map.of(),Map.of("Ordered",ordered,"Fields",iterable));
        assertTrue(types.assignable(new Type("Ordered"),new Type("Comparable")));
        assertTrue(types.assignable(new Type("int"),new Type("Comparable")));
        assertTrue(types.assignable(new Type("str"),new Type("Comparable")));
        assertTrue(types.assignable(new Type("Fields"),new Type("Iterable")));
        assertTrue(types.assignable(new Type("vec",List.of(new Type("str"))),new Type("Enumerable")));
        assertFalse(types.assignable(new Type("Fields"),new Type("Comparable")));
    }
    @Test void boundedNumericImplementationsMaterializeForEachConcreteType()throws Exception {
        assertEquals("[type \"byte\"]\n-128\n3.0\n",run("""
            impl T & Number
                on doubled -> T = self + self
            end
            let small byte = 64
            show ((small) doubled) type
            show (small) doubled
            show (1.5) doubled
            """));
    }
    @Test void nativeBoundedDefaultsSelectTheNarrowerBoundAndRespectExplicitMethods()throws Exception {
        assertEquals("explicit\ninteger\nnumber\n",run("""
            impl T & Number
                on family -> str = "number"
            end
            impl T & Integer
                on family -> str = "integer"
            end
            impl int
                on family -> str = "explicit"
            end
            let wide long = 2
            show (2) family
            show (wide) family
            show (2.0) family
            """));
    }
    @Test void nativeBoundedImplementationParametersKeepTheReceiverType() {
        reject("M2202","""
            impl T & Number
                on combine: other T -> T = self + other
            end
            (1) combine "wrong"
            """);
    }
}
