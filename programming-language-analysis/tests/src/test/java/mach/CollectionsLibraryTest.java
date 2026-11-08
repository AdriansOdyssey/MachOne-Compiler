package mach;
import mach.frontend.Diagnostic;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CollectionsLibraryTest {
    private String run(String source)throws Exception{byte[] code=new Compiler().compile("collections.ma",source,"CollectionProgram");Class<?> program=new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass("CollectionProgram",code,0,code.length);}}.load();var output=new ByteArrayOutputStream();var previous=System.out;try{System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));program.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}finally{System.setOut(previous);}return output.toString(StandardCharsets.UTF_8).replace("\r\n","\n");}
    @Test void immutableConstructionAndDeletion()throws Exception{assertEquals("[1, 2]\n[0, 1, 2, 3, 4]\n[1]\n[2]\n",run("""
        let original = [1, 2]
        let appended = (original) push: 3, 4
        show original
        show (appended) unshift: 0
        show (original) pop
        show (original) shift
        """));}
    @Test void insertionAndReplacementReportBounds()throws Exception{assertEquals("[1, 9, 2]\n[1, 8]\n#index\n",run("""
        show (([1, 2]) insert: 9 at: 1) rescue |f| { [] }
        show (([1, 2]) put: 8 at: -1) rescue |f| { [] }
        show (([1, 2]) delete_nth: 8) rescue |f| { ((f) kind) stringify }
        """));}
    @Test void equalityAwareQueriesAndSetOperations()throws Exception{assertEquals("true\nfalse\n1\n[1, 2, 3]\n[2]\n[1]\n",run("""
        show ([1, 2]) has_all?: 1, 2
        show ([1, 2]) has_any?: 3, 4
        show (([1, 2, 2]) index_of: 2) ?: -1
        show ([1, 2]) union: [2, 3]
        show ([1, 2]) intersect: [2, 3]
        show ([1, 2]) difference: [2, 3]
        """));}
    @Test void slicesCopiesAndStableSorting()throws Exception{assertEquals("[1, 2]\n[3, 4]\n[2, 3]\n[1, 2, 3]\n[3, 1, 2]\n",run("""
        show ([1, 2, 3, 4]) take: 2
        show ([1, 2, 3, 4]) drop: 2
        show ([1, 2, 3, 4]) slice: 1 to: 3
        let original = [3, 1, 2]
        show (original) sort
        show original
        """));}
    @Test void mapAndFilterRetainStaticElementTypes()throws Exception{assertEquals("[4, 6]\n",run("""
        let doubled vec[int] = ([1, 2, 3]) map: |x int| { x * 2 }
        let selected vec[int] = (doubled) filter: |x int| { x > 2 }
        show selected
        """));}
    @Test void reduceAndPredicates()throws Exception{assertEquals("6\ntrue\ntrue\nfalse\n",run("""
        show ([1, 2, 3]) reduce: 0 with: |sum int, x int| { sum + x }
        show ([1, 2, 3]) all?: |x int| { x > 0 }
        show ([1, 2, 3]) one?: |x int| { x == 2 }
        show ([1, 2, 3]) none?: |x int| { x == 2 }
        """));}
    @Test void callbackFailuresPropagate()throws Exception{assertEquals("caught\n",run("show (([1, 2]) map: |x int| { (Fail) raise #bad, \"bad\" }) rescue |f| { \"caught\" }"));}
    @Test void objectIterationAndMapValues()throws Exception{assertEquals("{#a: 2, #b: 4}\n[#a, #b]\n[1, 2]\n",run("""
        let values obj[int] = #{a: 1, b: 2}
        show (values) map_values: |x int| { x * 2 }
        show (values) keys
        show (values) values
        """));}
    @Test void mapFilteringAndLookup()throws Exception{assertEquals("{#b: 2}\n9\n{#a: 1}\n",run("""
        let values obj[int] = #{a: 1, b: 2}
        show (values) select: |key tag, value int| { value > 1 }
        show (values) fetch: #missing default: 9
        show (values) except: [#b]
        """));}
    @Test void joinsZipAndCombinations()throws Exception{assertEquals("a:b\n[[1, 3], [2, 4]]\n[[1, 2], [1, 3], [2, 3]]\n",run("""
        show (["a", "b"]) join: ":"
        show ([1, 2]) zip: [3, 4]
        show (([1, 2, 3]) combinations: 2) rescue |f| { [] }
        """));}
    @Test void groupByAndPartition()throws Exception{assertEquals("{#odd: [1, 3], #even: [2]}\n[[2, 3], [1]]\n",run("""
        show ([1, 2, 3]) group_by: |x int| { if (x) even? yield #even else #odd }
        show ([1, 2, 3]) partition: |x int| { x > 1 }
        """));}
    @Test void arithmeticAggregationAndBinarySearch()throws Exception{assertEquals("6\n6\n2.0\n1\nmissing\n",run("""
        show ([1, 2, 3]) sum
        show ([1, 2, 3]) product
        show (([1, 2, 3]) average) ?: 0.0
        show (([1, 2, 3]) binary_search: 2) rescue |f| { -1 }
        show (([1, 2, 3]) binary_search: 9) rescue |f| { "missing" }
        """));}
    @Test void fixedAndMutableArraysAreSupported()throws Exception{assertEquals("[1, 2, 3]\n[1, 9, 3]\n9\n",run("""
        let fixed = ((Array[int]) of: [1, 2, 3])
        let mutable = ((MutableArray[int]) of: [1, 2, 3])
        show fixed
        (mutable) put: 9 at: 1
        show mutable
        show (mutable) element_at: 1
        """));}
    @Test void persistentCollectionsKeepInvariantShapeAcrossMutations(){
        var vector = (mach.runtime.PersistentCollections.PersistentVector<Integer>) mach.runtime.PersistentCollections.frozen(java.util.List.of(1, 2, 3));
        vector.checkInvariants();
        var appended = vector.appended(4).appended(5).appended(6);
        appended.checkInvariants();
        var updated = appended.replaced(1, 9);
        updated.checkInvariants();
        var sliced = updated.subList(1, 5);
        sliced.checkInvariants();

        var entries = new java.util.LinkedHashMap<String, Integer>();
        for (int i = 0; i < 128; i++) entries.put("k" + i, i);
        var map = (mach.runtime.PersistentCollections.PersistentMap<String, Integer>) mach.runtime.PersistentCollections.frozen(entries);
        map.checkInvariants();
        for (int i = 0; i < 64; i++) {
           map = map.with("key-" + i, i * 2);
           map.checkInvariants();
           map = map.without("key-" + i);
           map.checkInvariants();
           map = map.with("key-" + i, i * 2);
           map.checkInvariants();
        }
    }
    @Test void persistentCollectionsKeepBoundedCostGrowthAcrossOperations(){
        var vector = (mach.runtime.PersistentCollections.PersistentVector<Integer>) mach.runtime.PersistentCollections.frozen(java.util.List.<Integer>of());
        for (int i = 0; i < 32; i++) vector = vector.appended(i);
        var append32 = vector.appendCost(32);
        var lookup32 = vector.getCost(31);
        var slice32 = vector.sliceCost(0, 16);
        assertTrue(append32.nodesVisited() <= 64);
        assertTrue(lookup32.nodesVisited() <= 32);
        assertTrue(slice32.nodesVisited() <= 64);

        for (int i = 32; i < 1024; i++) vector = vector.appended(i);
        var append1024 = vector.appendCost(1024);
        var lookup1024 = vector.getCost(512);
        var replace1024 = vector.replaceCost(511, 909);
        assertTrue(append1024.nodesVisited() <= 128);
        assertTrue(lookup1024.nodesVisited() <= 64);
        assertTrue(replace1024.nodesVisited() <= 128);

        var entries = new java.util.LinkedHashMap<String, Integer>();
        for (int i = 0; i < 512; i++) entries.put("k" + i, i);
        var map = (mach.runtime.PersistentCollections.PersistentMap<String, Integer>) mach.runtime.PersistentCollections.frozen(entries);
        var insert = map.withCost("extra", 777);
        var remove = map.withoutCost("k200");
        var lookup = map.getCost("k256");
        assertTrue(insert.nodesVisited() <= 512);
        assertTrue(remove.nodesVisited() <= 512);
        assertTrue(lookup.nodesVisited() <= 128);
    }
    @Test void rejectsWrongElementTypeBeforeRunning(){assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","let x vec[int] = [1]\n(x) append: \"bad\""));}
    @Test void rejectsNonBooleanPredicate(){assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","([1]) select: |x int| { x + 1 }"));}
    @Test void rejectsWrongCallbackArity(){assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","([1]) map: |x int, y int| { x + y }"));}
    @Test void rejectsNonIntegerIndex(){assertThrows(Diagnostic.class,()->new Compiler().check("bad.ma","([1]) take: \"1\""));}
    @Test void accessAndMessageOperatorsUseTheFinalSyntax()throws Exception{assertEquals("3\n30\n1\n{#b: 2}\nA\nA😀\n",run("""
        let xs = [1, 2, 3]
        let matrix = [[10, 20], [30, 40]]
        show (3 nth xs) rescue |f| { 0 }
        show ([2, 1] nth matrix) rescue |f| { [] }
        let mapping obj[int] = #{a: 1, b: 2}
        show (mapping) head ?: 0
        show (mapping) tail
        show (1 nth "A😀B") rescue |f| { `?` }
        show ([1, 2] nth "A😀B") rescue |f| { "" }
        """));}
    @Test void objectDeletionRemovesValuesAsOriginalExamplesSpecify()throws Exception{assertEquals("{#age: 61}\n",run("let user = #{name: \"John\", surname: \"Wick\", age: 61}\nshow (user) delete \"John\", \"Wick\""));}
    @Test void booleanComparatorSortHandlesDuplicateValues()throws Exception{assertEquals("[1, 1, 2, 2]\n",run("show ([2, 1, 2, 1]) sort_with |a int, b int| { a < b }"));}
    @Test void objectFoldAliasesAndPredicatesShareTheirContracts()throws Exception{assertEquals("3\ntrue\nfalse\n",run("""
        let mapping obj[int] = #{a: 1, b: 2}
        show (mapping) fold: 0 with: |sum int, key tag, value int| { sum + value }
        show (mapping) one? |key tag, value int| { value == 2 }
        show (mapping) none?
        """));}
    @Test void emptyNumericAggregationsKeepTheirStaticElementType()throws Exception{assertEquals("[type \"double\"]\n[type \"double\"]\n0.0\n1.0\n[type \"byte\"]\n",run("""
        let decimals vec[double] = []
        let sum double = (decimals) sum
        let product double = (decimals) product
        show (sum) type
        show (product) type
        show sum
        show product
        let bytes vec[byte] = [1, 2]
        let empty_bytes = (bytes) select |b| { false }
        show ((empty_bytes) sum) type
        """));}
    @Test void callbacksAndImmutableUpdatesRespectSourceNominalSubtyping()throws Exception{assertEquals("[type \"Cat\"]\n1\n",run("""
        abstract data Animal end
        record Cat & Animal(Init)
            name str
        end
        let cat = (Cat) new "Mia"
        let cats vec[Cat] = [cat]
        (cats) each |animal Animal| { show (animal) type }
        let animals vec[Animal] = []
        let populated vec[Animal] = (animals) append cat
        show (populated) length
        """));}
    @Test void failableCallbackCannotMasqueradeAsPlainNil(){
        var error=assertThrows(Diagnostic.class,()->new Compiler().check("effect.ma","let ignored nil = ([1]) each |x int| { (Fail) raise #bad, \"bad\" }"));
        assertEquals("M2101",error.code());
    }
    @Test void failureOnlyMappingHasNoSuccessfulElementType()throws Exception{assertEquals("caught\n",run("""
        let values try[vec[int]] = ([1]) map |x int| { (Fail) raise #bad, "bad" }
        show values rescue |failure| { "caught" }
        """));}
}
