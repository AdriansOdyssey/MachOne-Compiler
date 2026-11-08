package mach;

import mach.frontend.Diagnostic;
import mach.runtime.ExtraCollectionsLibrary;
import mach.runtime.RuntimeSupport;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtraCollectionsTest {
    private String run(String source) throws Exception {
        byte[] code=new Compiler().compile("extras.ma",source,"ExtraProgram");
        Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> program(){return defineClass("ExtraProgram",code,0,code.length);}}.program();
        var output=new ByteArrayOutputStream();PrintStream previous=System.out;
        try {System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}
        return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");
    }
    @Test void setDeduplicatesAndAddingPreservesOriginal() throws Exception {
        assertEquals("[1, 2]\n[1, 2, 3]\ntrue\n2\n",run("""
            let original = (Set[int]) new [1, 2, 1]
            let added = (original) add 3
            show (original) to_vec
            show (added) to_vec
            show (added) has? 3
            show (original) length
            """));
    }
    @Test void setOperationsPreserveValueSemantics() throws Exception {
        assertEquals("[1, 2, 3]\n[2]\n[1]\ntrue\n",run("""
            let left = (Set) new [1, 2]
            let right = (Set) new [2, 3]
            show ((left) union right) to_vec
            show ((left) intersect right) to_vec
            show ((left) difference right) to_vec
            show (left) subset? ((Set) new [1, 2, 3])
            """));
    }
    @Test void dictionaryHasNonTagKeysAndImmutableReplacement() throws Exception {
        assertEquals("one\nuno\n1\n[1]\ntrue\n",run("""
            let original = (Dictionary[int, str]) new
            let first = (original) put 1, "one"
            let updated = (first) put 1, "uno"
            show ((first) get 1) rescue |failure| { "missing" }
            show ((updated) get 1) rescue |failure| { "missing" }
            show (updated) length
            show (updated) keys
            show (original) empty?
            """));
    }
    @Test void missingDictionaryKeysDistinguishMaybeAndTry() throws Exception {
        assertEquals("fallback\n#not_found\n",run("""
            let values = (Dictionary[str, int]) new
            show ((values) maybe_get "absent") ?: "fallback"
            show ((values) get "absent") rescue |failure| { (failure) kind }
            """));
    }
    @Test void dequePushAndPopReturnNewValues() throws Exception {
        assertEquals("[1, 2]\n[0, 1, 2, 3]\n[1, 2, 3]\n0\n3\n",run("""
            let old = (Deque[int]) new [1, 2]
            let expanded = ((old) push_front 0) push_back 3
            show (old) to_vec
            show (expanded) to_vec
            show ((expanded) pop_front) to_vec
            show (expanded) front
            show (expanded) back
            """));
    }
    @Test void bagKeepsMultiplicityAndCount() throws Exception {
        assertEquals("3\n1\n[2]\n",run("""
            let values = ((Bag[int]) new [1, 1, 2]) add 1
            show (values) count 1
            show (values) count 2
            show ((values) delete 1) to_vec
            """));
    }
    @Test void priorityQueueOrdersByPriorityWithStableTies() throws Exception {
        assertEquals("urgent\nnormal\n2\n",run("""
            let queue = (PriorityQueue[str]) new
            let one = (queue) insert "normal" with 10
            let two = (one) insert "urgent" with 1
            show (two) peek
            show ((two) pop_min) peek
            show (two) length
            """));
    }
    @Test void sortedSetAndMapUseExclusiveUpperBound() throws Exception {
        assertEquals("[1, 2, 3, 4]\n[2, 3]\n[2, 3]\n",run("""
            let values = (SortedSet[int]) new [4, 2, 1, 3, 2]
            show (values) to_vec
            show ((values) range 2 to 4) to_vec
            let map = (SortedMap[int, str]) new
            let updated = (((map) put 3, "three") put 1, "one") put 2, "two"
            show ((updated) range 2 to 4) keys
            """));
    }
    @Test void genericMutationAndLookupErrorsAreStatic() {
        for(String source:List.of("let values=(Set[int]) new [1]\n(values) add \"two\"","let values=(Dictionary[int,str]) new\n(values) put \"one\", \"text\"","let values=(Dictionary[int,str]) new\n(values) get #wrong","let values=(Deque[str]) new [\"one\"]\n(values) push_back 2","let values=(PriorityQueue[str]) new\n(values) insert \"one\" with \"priority\""))
            assertThrows(Diagnostic.class,()->new Compiler().check("invalid-extra.ma",source));
    }
    @Test void extraNativeValuesKeepTheirRuntimeGenericTypes() throws Exception {
        assertEquals("true\ntrue\nfalse\n",run("""
            let values = ((Set[int]) new [1]) add 2
            show values isa Set[int]
            show values isa any
            show values isa Set[str]
            """));
    }
    @Test void extraCollectionsUseTypedEnumerableCallbacks() throws Exception {
        assertEquals("[2, 4, 6]\n[2]\nvalue:1\n",run("""
            let values = (Set[int]) new [1, 2, 3]
            show (values) collect |x| { x * 2 }
            show ((values) select |x| { (x) even? }) to_vec
            let map = ((Dictionary[str, int]) new) put "value", 1
            (map) each |key, value| { show "$key:$value" }
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("bad-extra-block.ma","let values=(Set[int]) new [1]\n(values) select |x| { x + 1 }"));
    }
    @Test void aSourceRecordCanReplaceAnExtraLibraryType() throws Exception {
        assertEquals("first\n1\n",run("""
            record Dictionary[K, V](Init, Prop)
                readable _key K
                readable _value V
            end
            let mapping = (Dictionary[str, int]) new "first", 1
            show (mapping) key
            show (mapping) value
            """));
    }
    @Test void unprovenDictionaryPairShapeIsFallibleAndChecked() throws Exception {
        assertEquals("#type\n",run("""
            let pairs vec[vec[any]] = [[1]]
            show ((Dictionary[int, str]) new pairs) rescue |failure| { (failure) kind }
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("unsafe-dictionary.ma","let pairs vec[vec[any]]=[[1]]\nlet value Dictionary[int,str]=(Dictionary[int,str]) new pairs"));
    }
    @Test void collectionContractsUseTheProgramsNominalHierarchy() throws Exception {
        assertEquals("2\n",run("""
            abstract data Animal end
            record Dog & Animal(Init) end
            let animals = (Set[Animal]) new [((Dog) new)]
            show ((animals) add ((Dog) new)) length
            """));
    }
    @Test void extraCollectionsParticipateInTheIterationProtocol() throws Exception {
        assertEquals("[2, 4, 6]\n[3, 4]\n",run("""
            let values = (Set[int]) new [1, 2, 3]
            show for value in values yield value * 2
            let mapping = ((Dictionary[tag, int]) new) put #one, 1
            let more = (mapping) put #two, 2
            show for key, value in more yield value + 2
            """));
        assertThrows(Diagnostic.class,()->new Compiler().check("invalid-extra-iteration.ma","let values=(Set[int]) new [1]\nfor a,b in values\n show a\nend"));
    }
    @Test void runtimeDynamicMutationRejectsWrongTypesWithoutChangingCollection() {
        var context=new RuntimeSupport.Context();
        Object set=ExtraCollectionsLibrary.dispatch(context,RuntimeSupport.type("Set[int]"),"new",new Object[]{List.of(RuntimeSupport.integer(1,"int"))},null);
        Object failure=ExtraCollectionsLibrary.dispatch(context,set,"add",new Object[]{"two"},null);
        assertInstanceOf(RuntimeSupport.FailureValue.class,failure);
        assertEquals("type",((RuntimeSupport.FailureValue)failure).kind());
        assertEquals(1,((ExtraCollectionsLibrary.Value)set).values().size());
    }
}
