package mach;

import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ObjectIntegrationTest {
    private String run(String source) throws Exception {
        byte[] bytecode=new Compiler().compile("objects.ma",source,"ObjectProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()) {Class<?> loadProgram(){return defineClass("ObjectProgram",bytecode,0,bytecode.length);}}.loadProgram();
        var output=new ByteArrayOutputStream();var previous=System.out;
        try {System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
        finally {System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    private void reject(String code,String source){var d=assertThrows(Diagnostic.class,()->new Compiler().check("objects.ma",source));assertEquals(code,d.code(),d.getMessage());}

    @Test void abstractEngineFromSpecificationRunsOnTwoRecords() throws Exception {
        assertEquals("Engine Started!\nEngine Started!\n",run("""
            abstract Engine
                on start
                    show "Engine Started!"
                end
            end
            record Car(Engine, Init, Prop)
                accessible _car_name str
            end
            record Cycle(Engine, Init, Prop)
                accessible _cycle_name str
            end
            let car = (Car) new: "Civic"
            let cycle = (Cycle) new: "250cc"
            (car) start
            (cycle) start
            """));
    }
    @Test void abstractDispatchIsNotAnIndependentlyCallableType(){reject("M2304","abstract Engine\non start = \"started\"\nend\n(Engine) start");}
    @Test void abstractDataCannotBeConstructed(){reject("M2304","abstract data Animal\nend\n(Animal) new");}
    @Test void abstractMethodIsRecheckedAgainstEachConcreteFields() throws Exception {
        assertEquals("8\nTEXT\n",run("""
            abstract Stored
                on content = self's #content
            end
            record Numeric(Init, Stored)
                _content int
            end
            record Textual(Init, Stored)
                _content str
            end
            let number int = ((Numeric) new: 7) content
            let text str = ((Textual) new: "text") content
            show number + 1
            show (text) upcase
            """));
    }
    @Test void invalidAbstractBodyIsRejectedAfterInstantiation(){reject("M2101","abstract Named\non name -> int = self's #name\nend\nrecord Person(Init, Named)\n_name str\nend");}
    @Test void explicitConcreteMethodOverridesAbstractDefault() throws Exception {
        assertEquals("custom\n",run("abstract Engine\non start = \"default\"\nend\nrecord Car(Engine, Init)\nend\nimpl Car\non start = \"custom\"\nend\nshow ((Car) new) start"));
    }
    @Test void conflictingAbstractDefaultsAreDiagnosed(){reject("M2007","abstract First\non sound = \"first\"\nend\nabstract Second\non sound = \"second\"\nend\nrecord Bird(Init, First, Second)\nend");}
    @Test void abstractDataProvidesPolymorphicDefaultThroughBound() throws Exception {
        assertEquals("Generic Sound!\nGeneric Sound!\n",run("""
            abstract data Animal
            end
            record Dog & Animal(Init)
            end
            record Cat & Animal(Init)
            end
            impl T & Animal
                on interact = "Generic Sound!"
            end
            show ((Dog) new) interact
            show ((Cat) new) interact
            """));
    }
    @Test void concreteImplementationOverridesBoundedDefaultAtRuntime() throws Exception {
        assertEquals("woof\ngeneric\n",run("abstract data Animal\nend\nrecord Dog & Animal(Init)\nend\nrecord Cat & Animal(Init)\nend\nimpl T & Animal\non sound = \"generic\"\nend\nimpl Dog\non sound = \"woof\"\nend\nshow ((Dog) new) sound\nshow ((Cat) new) sound"));
    }
    @Test void abstractTypedReceiverDispatchesToActualConcreteType() throws Exception {
        assertEquals("woof\nmeow\n",run("""
            abstract data Animal
            end
            record Dog & Animal(Init)
            end
            record Cat & Animal(Init)
            end
            impl Dog
                on sound -> str = "woof"
            end
            impl Cat
                on sound -> str = "meow"
            end
            on sound_of: animal Animal -> str = (animal) sound
            show sound_of: ((Dog) new)
            show sound_of: ((Cat) new)
            """));
    }
    @Test void boundedDefaultCanUseConcreteSubtypeMessages() throws Exception {
        assertEquals("9.0\n",run("abstract data Shape\nend\nrecord Square & Shape(Init)\n_side double\nend\nimpl Square\non area -> double = (self's #side) * (self's #side)\nend\nimpl T & Shape\non size -> double = (self) area\nend\nshow ((Square) new: 3.0) size"));
    }
    @Test void abstractTypeTestAndSubtypeTest() throws Exception {
        assertEquals("true\ntrue\nfalse\n",run("abstract data Animal\nend\nrecord Dog & Animal(Init)\nend\nrecord Stone(Init)\nend\nshow Dog & Animal\nshow ((Dog) new) isa Animal\nshow Stone & Animal"));
    }
}
