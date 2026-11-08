package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ObjectAndBlockContractsTest {
    private String run(String source)throws Exception{
        byte[] bytes=new Compiler().compile("contracts.ma",source,"ContractsProgram");
        Class<?> cls=new ClassLoader(getClass().getClassLoader()){Class<?> loadProgram(){return defineClass("ContractsProgram",bytes,0,bytes.length);}}.loadProgram();
        var out=new ByteArrayOutputStream();var previous=System.out;
        try{System.setOut(new PrintStream(out,true,StandardCharsets.UTF_8));cls.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return out.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void whenInsideRescueReceivesFailureKind()throws Exception{assertEquals("130\n",run("""
        let result = ((Fail) raise #too_old, "age") rescue |failure|
            when (failure) kind
                is #negative => 0
                is #too_old => 130
                default => 18
            end
        end
        show result
        """));}
    @Test void whenMatchesRangesAndGuardBindings()throws Exception{assertEquals("weekday\n",run("""
        let day = 3
        let label = when day
            is 1, 7 => "weekend"
            is 2..6 => "weekday"
            is n, if n > 7 => "invalid"
            default => "unknown"
        end
        show label
        """));}
    @Test void ifDoProducesValue()throws Exception{assertEquals("B\n",run("""
        let grade = 85
        let result = if grade >= 90 do
            "A"
        elsif grade >= 70 do
            "B"
        else do
            "C"
        end
        show result
        """));}
    @Test void operatorsCanReadAnotherInstancePrivateFieldsInSameImpl()throws Exception{assertEquals("Vec2{x: 4.0, y: 6.0}\n",run("""
        record Vec2(Init, Repr)
            _x double
            _y double
        end
        impl Vec2
            on op +: other Vec2 -> Vec2
                (Vec2) new (self's #x + other's #x), (self's #y + other's #y)
            end
        end
        let first = (Vec2) new 1.0, 2.0
        let second = (Vec2) new 3.0, 4.0
        show first + second
        """));}
    @Test void customStringifyIsUsedByShow()throws Exception{assertEquals("Person(Adrian)\n",run("""
        record Person(Init, Repr)
            _name str
        end
        impl Person
            on stringify -> str = "Person($(self's #name))"
        end
        show (Person) new "Adrian"
        """));}
    @Test void derivedEqualityAndDefaultIdentityStayDistinct()throws Exception{assertEquals("true\nfalse\n",run("""
        record Value(Init, Eq)
            number int
        end
        record Reference(Init)
            number int
        end
        let first = (Value) new 1
        let second = (Value) new 1
        let third = (Reference) new 1
        let fourth = (Reference) new 1
        show first == second
        show third == fourth
        """));}
    @Test void initializeCanReturnFailureAndSuccessIsRefined()throws Exception{assertEquals("25\nrejected\n",run("""
        record Percent(Prop)
            readable _value int
        end
        impl Percent
            on initialize: value int
                (value < 0) then { return (Fail) raise #range, "negative" }
                self = self | {#value: value}
            end
        end
        let result = (Percent) new 25
        (result) ok? then { show (result) value }
        show ((Percent) new -1) rescue |failure| { "rejected" }
        """));}
    @Test void invalidGenericRecordArgumentIsRejected(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("generic.ma","""
        record Box[T](Init)
            _content T
        end
        impl Box
            on put: value T -> nil
                self = self | {#content: value}
            end
        end
        let box Box[str] = (Box) new "text"
        (box) put 42
        """));assertEquals("M2202",error.code());}
    @Test void showCanBeOverloadedAsAnOrdinaryMessage()throws Exception{assertEquals("[42]\n",run("on show: value int -> str = \"[$value]\"\nlet formatted = show: 42\nshow formatted"));}
    @Test void comparisonMethodMustReturnBool(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("eq.ma","record Item(Init) end\nimpl Item\non eq: other = 42\nend"));assertEquals("M2101",error.code());}
    @Test void customSafeAccessCanComputeFields()throws Exception{assertEquals("Adrian Silva\nnil\n",run("""
        record Person(Init)
            _first str
            _last str
        end
        impl Person
            on op 's: key tag -> maybe[any]
                when key
                    is #full_name => "$((self) slot #first) $((self) slot #last)"
                    default => (self) maybe_slot key
                end
            end
        end
        let person = (Person) new "Adrian", "Silva"
        show person's #full_name
        show person's #missing
        """));}
    @Test void declaredUnionDispatchChecksEveryAlternative()throws Exception{assertEquals("integer\nstring\n",run("""
        on identify: value int -> str = "integer"
        on identify: value str -> str = "string"
        let first union[int, str] = 1
        let second union[int, str] = "text"
        show identify first
        show identify second
        """));}
    @Test void abstractDispatchChecksArgumentTypes(){assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","""
        abstract data Animal end
        record Dog & Animal(Init) end
        impl Dog
            on accept: value int -> int = value
        end
        on wrong: animal Animal -> int = (animal) accept "bad"
        """));}
    @Test void constructorMustInitializeRequiredFields(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("init.ma","record Point(Prop)\n_x int\nend\nimpl Point\non initialize = nil\nend"));assertEquals("M2315",error.code());}
    @Test void constructorCannotReadRequiredFieldBeforeAssignment(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("init.ma","record Point(Prop)\n_x int\nend\nimpl Point\non initialize\nself = self | {#x: self's #x + 1}\nend\nend"));assertEquals("M2316",error.code());}
    @Test void initializerWithNamedPartsSupportsOptionalColons()throws Exception{assertEquals("6\n",run("record Product(Prop)\nreadable _value int\nend\nimpl Product\non initialize: a int, with: b int\nself = self | {#value: a * b}\nend\nend\nlet product = (Product) new 2 with 3\nshow (product) value"));}
    @Test void contextualGenericConstructorPreservesUnionParameter()throws Exception{assertEquals("7\ntext\n",run("""
        data Id = union[int, str]
        record Box[T](Init, Prop)
            accessible _content T
        end
        let box Box[Id] = (Box) new 7
        show (box) content
        (box) content "text"
        show (box) content
        """));}
    @Test void constructorLiteralsUseDeclaredFieldWidths()throws Exception{assertEquals("[type \"byte\"]\n[type \"long\"]\n",run("record Values(Init)\nsmall byte\nlarge long\nend\nlet v = (Values) new 1, 777\nshow (v's #small) type\nshow (v's #large) type"));}
    @Test void patternAlternativesCannotLeaveBindingsUndefined(){var error=assertThrows(Diagnostic.class,()->new Compiler().check("patterns.ma","let result = when 1\nis 1, other => other\nend"));assertEquals("M2110",error.code());}
    @Test void primitiveOperatorOverrideHasItsDeclaredStaticReturnType()throws Exception{assertEquals("sum\n",run("impl int\non op +: other int -> str = \"sum\"\nend\nlet result str = 1 + 2\nshow result"));}
}
