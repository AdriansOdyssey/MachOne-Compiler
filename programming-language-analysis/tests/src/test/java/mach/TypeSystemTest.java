package mach;

import mach.ast.Ast;
import mach.ast.Ast.Type;
import mach.semantic.TypeSystem;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TypeSystemTest {
    private final TypeSystem types = new TypeSystem(Map.of(), Map.of());
    private Type t(String name, Type... args) { return new Type(name, List.of(args)); }
    @Test void optionalAndFallibleAreDifferentUnions() {
        assertTrue(types.assignable(t("nil"), t("maybe", t("str"))));
        assertFalse(types.assignable(t("nil"), t("try", t("str"))));
        assertTrue(types.assignable(t("fail"), t("try", t("str"))));
        assertFalse(types.assignable(t("fail"), t("maybe", t("str"))));
    }
    @Test void optionalAndFallibleCannotBecomePlainValuesWithoutHandling() {
        assertFalse(types.assignable(t("maybe", t("int")), t("int")));
        assertFalse(types.assignable(t("try", t("int")), t("int")));
        assertTrue(types.assignable(t("try", t("int")), t("union", t("int"), t("fail"))));
    }
    @Test void allMembersOfActualUnionMustFit() {
        assertTrue(types.assignable(t("union", t("int"), t("str")), t("union", t("int"), t("str"), t("bool"))));
        assertFalse(types.assignable(t("union", t("int"), t("str")), t("int")));
    }
    @Test void wrapperCovarianceChecksPayload() {
        assertTrue(types.assignable(t("maybe", t("int")), t("maybe", t("Number"))));
        assertTrue(types.assignable(t("try", t("int")), t("try", t("Number"))));
        assertFalse(types.assignable(t("try", t("int")), t("try", t("str"))));
    }
    @Test void elvisDoesNotConsumeFailure() {
        Type input=t("try",t("maybe",t("str")));
        Type result=types.elvis(input,t("str"));
        assertEquals(t("try",t("str")),result);
        assertTrue(types.includesFail(result));
        assertFalse(types.includesNil(result));
    }
    @Test void rescueDoesNotConsumeNil() {
        Type result=types.rescued(t("try",t("maybe",t("int"))),t("int"));
        assertEquals(t("maybe",t("int")),result);
        assertTrue(types.includesNil(result));
        assertFalse(types.includesFail(result));
    }
    @Test void rescueMayIntentionallyReturnNil() {
        assertEquals(t("maybe",t("int")),types.rescued(t("try",t("int")),t("nil")));
        assertEquals(t("nil"),types.rescued(t("fail"),t("nil")));
    }
    @Test void genericRecordsRemainInvariantWhileImmutableVectorsAreCovariant() {
        assertFalse(types.assignable(t("Box",t("int")),t("Box",t("any"))));
        assertTrue(types.assignable(t("vec",t("int")),t("vec",t("Number"))));
    }
    @Test void aliasCyclesAreReportedInsteadOfOverflowingStack() {
        var cyclic=new TypeSystem(Map.of("A",t("B"),"B",t("A")),Map.of());
        assertThrows(IllegalArgumentException.class,()->cyclic.resolve(t("A")));
    }
    @Test void recordSubtypeUsesItsDeclaredParent() {
        var source=new Ast.Span("test.ma",1,1);
        var record=new Ast.Record("Dog",List.of(),t("Animal"),List.of(),List.of(),source);
        var hierarchy=new TypeSystem(Map.of(),Map.of("Dog",record));
        assertTrue(hierarchy.assignable(t("Dog"),t("Animal")));
        assertFalse(hierarchy.assignable(t("Animal"),t("Dog")));
    }
}
