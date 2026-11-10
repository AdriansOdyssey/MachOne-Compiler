package mach.semantic;

import mach.ast.Ast;
import mach.ast.Ast.Type;
import java.util.*;

/** Backend-independent type algebra for strong typing, unions and fallible/optional values. */
public final class TypeSystem {
    public static final Type ANY = new Type("any");
    public static final Type NEVER = new Type("<never>");
    public static final Type NIL = new Type("nil");
    public static final Type FAIL = new Type("fail");
    private static final Set<String> NUMERIC = Set.of("byte", "ubyte", "short", "ushort", "int", "uint", "long", "ulong", "float", "double");
    private static final Set<String> COVARIANT = Set.of("vec", "obj", "type");
    private static final Map<String,String> CORE_PARENT=Map.ofEntries(
        Map.entry("byte","Signed"),Map.entry("short","Signed"),Map.entry("int","Signed"),Map.entry("long","Signed"),
        Map.entry("ubyte","Unsigned"),Map.entry("ushort","Unsigned"),Map.entry("uint","Unsigned"),Map.entry("ulong","Unsigned"),
        Map.entry("Signed","Integer"),Map.entry("Unsigned","Integer"),Map.entry("Integer","Number"),
        Map.entry("float","Real"),Map.entry("double","Real"),Map.entry("Real","Number"),
        Map.entry("vec","Sequence"),Map.entry("str","Sequence"),Map.entry("Sequence","Collection"),Map.entry("obj","Mapping"),Map.entry("Mapping","Collection"),
        Map.entry("Set","Collection"),Map.entry("SortedSet","Collection"),Map.entry("Bag","Collection"),Map.entry("PriorityQueue","Collection"),
        Map.entry("Deque","Sequence"),Map.entry("Dictionary","Mapping"),Map.entry("SortedMap","Mapping"));
    private final Map<String, Type> aliases;
    private final Map<String, Ast.Record> records;

    /** Maps may be populated by the declaration pass before these operations are used. */
    public TypeSystem(Map<String, Type> aliases, Map<String, Ast.Record> records) {
        this.aliases = Objects.requireNonNull(aliases);
        this.records = Objects.requireNonNull(records);
    }

    public Type resolve(Type type) { return resolve(type, new LinkedHashSet<>()); }
    private Type resolve(Type type, Set<String> path) {
        Objects.requireNonNull(type);
        if (aliases.containsKey(type.name())) {
            if (!path.add(type.name())) throw new IllegalArgumentException("alias cíclico: " + String.join(" -> ", path) + " -> " + type.name());
            Type result = resolve(aliases.get(type.name()), path);
            path.remove(type.name());
            return new Type(result.name(), result.args(), type.flavor(), type.bound()==null?null:resolve(type.bound(),new LinkedHashSet<>(path)));
        }
        List<Type> args = new ArrayList<>();
        for (Type arg : type.args()) args.add(resolve(arg, new LinkedHashSet<>(path)));
        Type bound=type.bound()==null?null:resolve(type.bound(),new LinkedHashSet<>(path));
        return new Type(type.name(), List.copyOf(args), type.flavor(), bound);
    }

    public boolean numeric(Type type) { return NUMERIC.contains(resolve(type).name()); }
    public boolean integer(Type type) { Type t = resolve(type); return NUMERIC.contains(t.name()) && !Set.of("float", "double").contains(t.name()); }

    public List<Type> alternatives(Type type) {
        var out = new LinkedHashSet<Type>();
        alternativesInto(resolve(type), out);
        return List.copyOf(out);
    }
    private void alternativesInto(Type type, Set<Type> out) {
        if (type.equals(NEVER)) return;
        if (type.name().equals("union")) {
            for (Type arg : type.args()) alternativesInto(arg, out);
        } else if (type.name().equals("maybe") || type.name().equals("try")) {
            requireSingle(type);
            alternativesInto(type.args().getFirst(), out);
            out.add(type.name().equals("maybe") ? NIL : FAIL);
        } else out.add(type);
    }
    private static void requireSingle(Type type) {
        if (type.args().size() != 1) throw new IllegalArgumentException(type.name() + " exige exatamente um argumento de tipo");
    }

    public boolean assignable(Type actual, Type expected) {
        actual = resolve(actual); expected = resolve(expected);
        if (expected.name().equals("Any") || expected.name().equals("any") || actual.equals(NEVER)) return true;
        if (expected.flavor() == Ast.TypeFlavor.BOUND_VARIABLE) {
            if (expected.bound() != null && !assignable(actual, expected.bound())) return false;
            return true;
        }
        if (expected.flavor() == Ast.TypeFlavor.ABSTRACT_CONSTRAINT) {
            if (expected.name().equals("Any") || expected.name().equals("any")) return true;
            return atomicAssignable(actual, new Type(expected.name(), expected.args()), new HashSet<>());
        }
        if (actual.flavor() == Ast.TypeFlavor.BOUND_VARIABLE && actual.bound() != null) return assignable(actual.bound(), expected);
        if (actual.equals(expected)) return true;
        if (isSubtype(actual, expected, new LinkedHashSet<>())) return true;
        List<Type> choices = alternatives(expected);
        for (Type member : alternatives(actual)) {
            boolean accepted = false;
            for (Type choice : choices) if (atomicAssignable(member, choice, new HashSet<>())) { accepted = true; break; }
            if (!accepted) return false;
        }
        return true;
    }

    public boolean isSubtype(Type actual, Type expected) {
        return isSubtype(actual, expected, new LinkedHashSet<>());
    }

    private boolean isSubtype(Type actual, Type expected, Set<String> seen) {
        final Type actualValue = resolve(actual);
        final Type expectedValue = resolve(expected);
        if (actualValue.equals(NEVER) || expectedValue.equals(ANY) || actualValue.equals(expectedValue)) return true;
        if (expectedValue.flavor() == Ast.TypeFlavor.BOUND_VARIABLE) {
            if (expectedValue.bound() == null) return true;
            return isSubtype(actualValue, expectedValue.bound(), new LinkedHashSet<>(seen));
        }
        if (expectedValue.flavor() == Ast.TypeFlavor.ABSTRACT_CONSTRAINT) {
            if (expectedValue.name().equals("Any") || expectedValue.name().equals("any")) return true;
            return isSubtype(actualValue, new Type(expectedValue.name(), expectedValue.args()), new LinkedHashSet<>(seen));
        }
        if (actualValue.flavor() == Ast.TypeFlavor.BOUND_VARIABLE) {
            if (actualValue.bound() == null) return true;
            return isSubtype(actualValue.bound(), expectedValue, new LinkedHashSet<>(seen));
        }
        if (expectedValue.name().equals("union")) {
            final Type actualRef = actualValue;
            return expectedValue.args().stream().anyMatch(arg -> isSubtype(actualRef, arg, new LinkedHashSet<>(seen)));
        }
        if (actualValue.name().equals("union")) {
            final Type expectedRef = expectedValue;
            return actualValue.args().stream().allMatch(arg -> isSubtype(arg, expectedRef, new LinkedHashSet<>(seen)));
        }
        if (actualValue.name().equals("maybe") || actualValue.name().equals("try")) {
            if (actualValue.args().size() != 1) return false;
            if (expectedValue.name().equals(actualValue.name())) {
                return isSubtype(actualValue.args().getFirst(), expectedValue.args().getFirst(), new LinkedHashSet<>(seen));
            }
            return false;
        }
        if (expectedValue.name().equals("maybe") || expectedValue.name().equals("try")) {
            if (expectedValue.args().size() != 1) return false;
            return false;
        }
        if (actualValue.name().equals(expectedValue.name())) {
            if (actualValue.args().size() != expectedValue.args().size()) return false;
            for (int i = 0; i < actualValue.args().size(); i++) {
                if (!isSubtype(actualValue.args().get(i), expectedValue.args().get(i), new LinkedHashSet<>(seen))) return false;
            }
            return true;
        }
        String parent = CORE_PARENT.get(actualValue.name());
        if (parent != null && !seen.contains(actualValue.name())) {
            seen.add(actualValue.name());
            if (isSubtype(new Type(parent), expectedValue, new LinkedHashSet<>(seen))) return true;
        }
        String expectedParent = CORE_PARENT.get(expectedValue.name());
        if (expectedParent != null && !seen.contains(expectedValue.name())) {
            seen.add(expectedValue.name());
            if (isSubtype(actualValue, new Type(expectedParent), new LinkedHashSet<>(seen))) return true;
        }
        if (records.containsKey(actualValue.name())) {
            Ast.Record record = records.get(actualValue.name());
            if (record.parent() != null && !seen.contains(record.name())) {
                seen.add(record.name());
                if (isSubtype(resolve(record.parent()), expectedValue, new LinkedHashSet<>(seen))) return true;
            }
        }
        return false;
    }
    private boolean atomicAssignable(Type actual, Type expected, Set<String> visited) {
        if(actual.flavor()==Ast.TypeFlavor.BOUND_VARIABLE&&actual.bound()!=null)return atomicAssignable(actual.bound(),expected,new HashSet<>(visited));
        if(expected.flavor()==Ast.TypeFlavor.ABSTRACT_CONSTRAINT)return atomicAssignable(actual,new Type(expected.name(),expected.args()),new HashSet<>(visited));
        if(expected.flavor()==Ast.TypeFlavor.BOUND_VARIABLE)return expected.bound()==null||atomicAssignable(actual,expected.bound(),new HashSet<>(visited));
        if (expected.equals(ANY) || actual.equals(expected)) return true;
        Ast.Record protocolRecord=records.get(actual.name());
        if(!records.containsKey(expected.name())) {
            if(expected.name().equals("Comparable")&&(protocolRecord==null&&(NUMERIC.contains(actual.name())||Set.of("Number","Integer","Signed","Unsigned","Real","str","char").contains(actual.name()))||protocolRecord!=null&&protocolRecord.derives().contains("Ord")))return true;
            if(Set.of("Iterable","Enumerable").contains(expected.name())&&(protocolRecord==null&&Set.of("Collection","Sequence","Mapping","vec","obj","str","Set","SortedSet","Dictionary","SortedMap","Deque","Bag","PriorityQueue").contains(actual.name())||protocolRecord!=null&&protocolRecord.derives().contains("Iter")))return true;
        }
        String coreParent=CORE_PARENT.get(actual.name());
        if(coreParent!=null&&!records.containsKey(actual.name())&&!records.containsKey(expected.name())&&atomicAssignable(new Type(coreParent),expected,new HashSet<>(visited)))return true;
        if (actual.name().equals(expected.name())) {
            if (expected.name().equals("type") && expected.args().isEmpty()) return true;
            if (actual.args().size() != expected.args().size()) return false;
            if (COVARIANT.contains(expected.name())) {
                for (int i = 0; i < actual.args().size(); i++) if (!assignable(actual.args().get(i), expected.args().get(i))) return false;
                return true;
            }
            if (expected.name().equals("lambda") && !actual.args().isEmpty()) {
                int last = actual.args().size() - 1;
                for (int i = 0; i < last; i++) if (!assignable(expected.args().get(i), actual.args().get(i))) return false;
                return assignable(actual.args().get(last), expected.args().get(last));
            }
        }
        Ast.Record record = records.get(actual.name());
        if (record == null || !visited.add(actual.toString())) return false;
        var bindings = new HashMap<String, Type>();
        for (int i = 0; i < Math.min(record.parameters().size(), actual.args().size()); i++) bindings.put(record.parameters().get(i), actual.args().get(i));
        if(record.parent()!=null&&atomicAssignable(resolve(substitute(record.parent(),bindings)),expected,new HashSet<>(visited)))return true;
        if(record.derives().contains("Proxy"))for(var field:record.fields())if(field.labels().contains("proxied")) {
            Type target=resolve(substitute(field.type(),bindings));
            if(atomicAssignable(target,expected,new HashSet<>(visited)))return true;
        }
        return false;
    }

    public Type union(Type... types) { return union(Arrays.asList(types)); }
    public Type union(Collection<Type> types) {
        var members = new LinkedHashSet<Type>();
        for (Type type : types) members.addAll(alternatives(type));
        if (members.isEmpty()) return NEVER;
        if (members.size() == 1) return members.iterator().next();
        boolean nullable = members.remove(NIL), failable = members.remove(FAIL);
        Type result;
        if (members.isEmpty()) result = nullable ? NIL : FAIL;
        else if (members.size() == 1) result = members.iterator().next();
        else {
            var ordered = new ArrayList<>(members);
            ordered.sort(Comparator.comparing(Type::toString));
            result = new Type("union", List.copyOf(ordered));
        }
        if (nullable && !result.equals(NIL)) result = new Type("maybe", List.of(result));
        if (failable && !result.equals(FAIL)) result = new Type("try", List.of(result));
        return result;
    }
    public Type join(Type left, Type right) {
        if (resolve(left).equals(resolve(right))) return resolve(left);
        return union(left, right);
    }
    public Type withoutNil(Type type) { return excluding(type, NIL); }
    public Type successOfTry(Type type) { return excluding(type, FAIL); }
    public Type failurePart(Type type) { return includesFail(type) || resolve(type).equals(ANY) ? FAIL : NEVER; }
    public Type nilPart(Type type) { return includesNil(type) || resolve(type).equals(ANY) ? NIL : NEVER; }
    public boolean includesFail(Type type) { return alternatives(type).contains(FAIL); }
    public boolean includesNil(Type type) { return alternatives(type).contains(NIL); }
    private Type excluding(Type type, Type excluded) {
        return union(alternatives(type).stream().filter(t -> !t.equals(excluded)).toList());
    }
    /** Elvis consumes nil only. A Fail alternative remains present. */
    public Type elvis(Type value, Type fallback) {
        if (!includesNil(value)) return resolve(value);
        return join(withoutNil(value), fallback);
    }
    /** Rescue consumes Fail only; handlers may intentionally return nil. */
    public Type rescued(Type value, Type handlerResult) {
        if (!includesFail(value)) return resolve(value);
        return join(successOfTry(value), handlerResult);
    }
    public Type substitute(Type type, Map<String, Type> bindings) {
        Type direct = bindings.get(type.name());
        if (direct != null && type.args().isEmpty()&&type.flavor()!=Ast.TypeFlavor.ABSTRACT_CONSTRAINT) return direct;
        Type bound=type.bound()==null?null:substitute(type.bound(),bindings);
        return new Type(type.name(), type.args().stream().map(t -> substitute(t, bindings)).toList(), type.flavor(), bound);
    }
}
