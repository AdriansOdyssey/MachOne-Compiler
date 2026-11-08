package mach.runtime;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeMap;

/**
 * Persistent, copy-on-write collection snapshots.
 *
 * The runtime uses a chunked vector structure with structural sharing and a CHAMP-style
 * hash trie for maps. This keeps operations in the O(1) / O(log n) range expected by a
 * persistent collection backend while preserving snapshot stability for older versions.
 *
 * This is still a pragmatic implementation layer, not a fully formalized RRB/CHAMP
 * specification with every balance and complexity proof required by the language reference.
 */
public final class PersistentCollections {
    private PersistentCollections() {}

    public static <E> List<E> frozen(Collection<? extends E> values) {
        if (values instanceof PersistentVector<?> vector) return (List<E>) vector;
        return new PersistentVector<>(values);
    }

    public static <K, V> Map<K, V> frozen(Map<? extends K, ? extends V> values) {
        if (values instanceof PersistentMap<?, ?> map) return (Map<K, V>) map;
        return new PersistentMap<>(values);
    }

    public static final class CostCounter {
        private int nodesVisited;
        private int nodesCopied;
        private int comparisons;

        public int nodesVisited() { return nodesVisited; }
        public int nodesCopied() { return nodesCopied; }
        public int comparisons() { return comparisons; }

        private void add(CostCounter other) {
            this.nodesVisited += other.nodesVisited;
            this.nodesCopied += other.nodesCopied;
            this.comparisons += other.comparisons;
        }

        public CostCounter copy() {
            CostCounter copy = new CostCounter();
            copy.nodesVisited = this.nodesVisited;
            copy.nodesCopied = this.nodesCopied;
            copy.comparisons = this.comparisons;
            return copy;
        }
    }

    public static final class PersistentVector<E> extends AbstractList<E> implements RandomAccess {
        private static final int BRANCH_FACTOR = 32;
        private final int size;
        private final Node<E> root;
        private final Object[] tail;
        private transient CostCounter stats = new CostCounter();

        private PersistentVector(Collection<? extends E> values) {
            Objects.requireNonNull(values, "values");
            List<E> snapshot = List.copyOf(values);
            this.size = snapshot.size();
            if (snapshot.isEmpty()) {
                this.root = null;
                this.tail = new Object[0];
                return;
            }
            int tailStart = snapshot.size() - (snapshot.size() % BRANCH_FACTOR == 0 ? 0 : snapshot.size() % BRANCH_FACTOR);
            this.tail = snapshot.subList(Math.max(0, tailStart), snapshot.size()).toArray(new Object[0]);
            this.root = tailStart == 0 ? null : buildNode(snapshot, 0, tailStart);
        }

        private PersistentVector(int size, Node<E> root, Object[] tail) {
            this.size = size;
            this.root = root;
            this.tail = tail == null ? new Object[0] : tail.clone();
        }

        private static <E> Node<E> buildNode(List<E> values, int from, int to) {
            int length = to - from;
            if (length <= BRANCH_FACTOR) {
                Object[] arr = new Object[length];
                for (int i = 0; i < length; i++) arr[i] = values.get(from + i);
                return new LeafNode<>(arr);
            }
            int childCount = (length + BRANCH_FACTOR - 1) / BRANCH_FACTOR;
            Node<E>[] children = new Node[childCount];
            int[] childSizes = new int[childCount];
            for (int i = 0; i < childCount; i++) {
                int start = from + i * BRANCH_FACTOR;
                int end = Math.min(start + BRANCH_FACTOR, to);
                children[i] = buildNode(values, start, end);
                childSizes[i] = children[i].size();
            }
            return new BranchNode<>(children, childSizes);
        }

        private static <E> Node<E> growTail(Node<E> root, Object[] tail) {
            if (root == null) return new LeafNode<>(tail);
            if (root instanceof LeafNode<E> leaf) {
                Object[] combined = new Object[leaf.values.length + tail.length];
                System.arraycopy(leaf.values, 0, combined, 0, leaf.values.length);
                System.arraycopy(tail, 0, combined, leaf.values.length, tail.length);
                if (combined.length <= BRANCH_FACTOR) return new LeafNode<>(combined);
                int splitCount = (combined.length + BRANCH_FACTOR - 1) / BRANCH_FACTOR;
                Node<E>[] children = new Node[splitCount];
                int[] childSizes = new int[splitCount];
                for (int i = 0; i < splitCount; i++) {
                    int start = i * BRANCH_FACTOR;
                    int end = Math.min(start + BRANCH_FACTOR, combined.length);
                    Object[] chunk = Arrays.copyOfRange(combined, start, end);
                    children[i] = new LeafNode<>(chunk);
                    childSizes[i] = chunk.length;
                }
                return new BranchNode<>(children, childSizes);
            }
            BranchNode<E> branch = (BranchNode<E>) root;
            if (branch.children.length < BRANCH_FACTOR) {
                Node<E>[] nextChildren = Arrays.copyOf(branch.children, branch.children.length + 1);
                int[] nextSizes = Arrays.copyOf(branch.childSizes, branch.childSizes.length + 1);
                nextChildren[nextChildren.length - 1] = new LeafNode<>(tail);
                nextSizes[nextSizes.length - 1] = tail.length;
                return new BranchNode<>(nextChildren, nextSizes);
            }
            return new BranchNode<>(new Node[] { branch, new LeafNode<>(tail) }, new int[] { branch.size(), tail.length });
        }

        public PersistentVector<E> appended(E value) {
            CostCounter counter = new CostCounter();
            PersistentVector<E> result = appended(value, counter);
            result.stats = counter;
            result.checkInvariants();
            return result;
        }

        private PersistentVector<E> appended(E value, CostCounter counter) {
            Object[] nextTail = Arrays.copyOf(tail, tail.length + 1);
            nextTail[tail.length] = value;
            if (tail.length < BRANCH_FACTOR) {
                counter.nodesVisited += 1;
                counter.nodesCopied += 1;
                return new PersistentVector<>(size + 1, root, nextTail);
            }
            counter.nodesVisited += root == null ? 1 : root.countNodes();
            counter.nodesCopied += root == null ? 1 : root.countNodes();
            PersistentVector<E> result = new PersistentVector<>(size + 1, growTail(root, tail), new Object[] { value });
            result.stats = counter.copy();
            return result;
        }

        public CostCounter appendCost(E value) {
            CostCounter counter = new CostCounter();
            appended(value, counter);
            return counter;
        }

        public PersistentVector<E> replaced(int index, E value) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(String.valueOf(index));
            CostCounter counter = new CostCounter();
            PersistentVector<E> result = replaced(index, value, counter);
            result.stats = counter;
            result.checkInvariants();
            return result;
        }

        private PersistentVector<E> replaced(int index, E value, CostCounter counter) {
            if (index >= size - tail.length) {
                counter.nodesVisited += 1;
                counter.nodesCopied += 1;
                Object[] nextTail = tail.clone();
                nextTail[index - (size - tail.length)] = value;
                return new PersistentVector<>(size, root, nextTail);
            }
            counter.nodesVisited += root == null ? 1 : root.countNodes();
            counter.nodesCopied += root == null ? 0 : root.countNodes();
            return new PersistentVector<>(size, replace(root, index, value), tail);
        }

        public CostCounter replaceCost(int index, E value) {
            CostCounter counter = new CostCounter();
            replaced(index, value, counter);
            return counter;
        }

        public PersistentVector<E> concat(Collection<? extends E> other) {
            PersistentVector<E> result = this;
            CostCounter counter = new CostCounter();
            for (E item : other) {
                CostCounter step = new CostCounter();
                result = result.appended(item, step);
                counter.add(step);
            }
            result.stats = counter;
            result.checkInvariants();
            return result;
        }

        public CostCounter concatCost(Collection<? extends E> other) {
            CostCounter counter = new CostCounter();
            PersistentVector<E> current = this;
            for (E item : other) {
                CostCounter step = new CostCounter();
                current = current.appended(item, step);
                counter.add(step);
            }
            return counter;
        }

        @Override public E get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(String.valueOf(index));
            int tailOffset = size - tail.length;
            if (index >= tailOffset) return (E) tail[index - tailOffset];
            return getAt(root, index);
        }

        public CostCounter getCost(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException(String.valueOf(index));
            CostCounter counter = new CostCounter();
            int tailOffset = size - tail.length;
            if (index >= tailOffset) {
                counter.nodesVisited += 1;
                counter.comparisons += 1;
                return counter;
            }
            getAt(root, index, counter);
            return counter;
        }

        private static <E> E getAt(Node<E> node, int index) {
            if (node == null) throw new IndexOutOfBoundsException(String.valueOf(index));
            if (node instanceof LeafNode<E> leaf) {
                return (E) leaf.values[index];
            }
            BranchNode<E> branch = (BranchNode<E>) node;
            int cursor = 0;
            for (int i = 0; i < branch.children.length; i++) {
                int next = cursor + branch.childSizes[i];
                if (index < next) return getAt(branch.children[i], index - cursor);
                cursor = next;
            }
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        private static <E> void getAt(Node<E> node, int index, CostCounter counter) {
            if (node == null) throw new IndexOutOfBoundsException(String.valueOf(index));
            counter.nodesVisited += 1;
            if (node instanceof LeafNode<E> leaf) {
                counter.comparisons += 1;
                return;
            }
            BranchNode<E> branch = (BranchNode<E>) node;
            int cursor = 0;
            for (int i = 0; i < branch.children.length; i++) {
                int next = cursor + branch.childSizes[i];
                counter.comparisons += 1;
                if (index < next) {
                    getAt(branch.children[i], index - cursor, counter);
                    return;
                }
                cursor = next;
            }
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        private static <E> Node<E> replace(Node<E> root, int index, E value) {
            if (root instanceof LeafNode<E> leaf) {
                Object[] next = leaf.values.clone();
                next[index] = value;
                return new LeafNode<>(next);
            }
            BranchNode<E> branch = (BranchNode<E>) root;
            int cursor = 0;
            for (int i = 0; i < branch.children.length; i++) {
                int nextCursor = cursor + branch.childSizes[i];
                if (index < nextCursor) {
                    Node<E>[] updatedChildren = branch.children.clone();
                    int[] updatedSizes = branch.childSizes.clone();
                    updatedChildren[i] = replace(branch.children[i], index - cursor, value);
                    updatedSizes[i] = updatedChildren[i].size();
                    return new BranchNode<>(updatedChildren, updatedSizes);
                }
                cursor = nextCursor;
            }
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        public void checkInvariants() {
            if (tail.length > BRANCH_FACTOR) throw new IllegalStateException("tail excedeu o limite de 32 elementos");
            if (size < 0) throw new IllegalStateException("tamanho negativo no vetor");
            if (root == null) {
                if (size != tail.length) throw new IllegalStateException("vetor vazio com tail não vazio");
                return;
            }
            int rootSize = root.size();
            if (rootSize + tail.length != size) {
                throw new IllegalStateException("tamanho inconsistente: root=" + rootSize + ", tail=" + tail.length + ", total=" + size);
            }
            if (rootSize > 0 && rootSize < BRANCH_FACTOR && tail.length == 0) {
                throw new IllegalStateException("estrutura de vetor inválida: root pequeno e tail vazio");
            }
            root.checkInvariants(0);
            int depth = root.depth();
            if (depth > 32) throw new IllegalStateException("profundidade de árvore excedeu o limite");
        }

        @Override public int size() {
            return size;
        }

        @Override public PersistentVector<E> subList(int fromIndex, int toIndex) {
            if (fromIndex < 0 || toIndex < fromIndex || toIndex > size) {
                throw new IndexOutOfBoundsException();
            }
            List<E> out = new ArrayList<>(toIndex - fromIndex);
            for (int i = fromIndex; i < toIndex; i++) out.add(get(i));
            return new PersistentVector<>(out);
        }

        public CostCounter sliceCost(int fromIndex, int toIndex) {
            if (fromIndex < 0 || toIndex < fromIndex || toIndex > size) {
                throw new IndexOutOfBoundsException();
            }
            CostCounter counter = new CostCounter();
            for (int i = fromIndex; i < toIndex; i++) {
                CostCounter step = getCost(i);
                counter.add(step);
            }
            return counter;
        }

        @Override public String toString() {
            List<E> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) out.add(get(i));
            return out.toString();
        }

        private abstract static class Node<E> {
            abstract int size();
            abstract int depth();
            abstract int countNodes();
            abstract void checkInvariants(int depth);
        }

        private static final class LeafNode<E> extends Node<E> {
            private final Object[] values;

            private LeafNode(Object[] values) {
                this.values = values;
            }

            @Override int size() {
                return values.length;
            }

            @Override int depth() {
                return 1;
            }

            @Override int countNodes() {
                return 1;
            }

            @Override void checkInvariants(int depth) {
                if (depth > 32) throw new IllegalStateException("profundidade da árvore excedeu o limite");
                if (values.length > BRANCH_FACTOR) throw new IllegalStateException("nó de folha excedeu o tamanho máximo");
            }
        }

        private static final class BranchNode<E> extends Node<E> {
            private final Node<E>[] children;
            private final int[] childSizes;

            private BranchNode(Node<E>[] children, int[] childSizes) {
                this.children = children;
                this.childSizes = childSizes;
            }

            @Override int size() {
                int total = 0;
                for (int size : childSizes) total += size;
                return total;
            }

            @Override int depth() {
                int maxDepth = 0;
                for (Node<E> child : children) maxDepth = Math.max(maxDepth, child.depth());
                return maxDepth + 1;
            }

            @Override int countNodes() {
                int total = 1;
                for (Node<E> child : children) total += child.countNodes();
                return total;
            }

            @Override void checkInvariants(int depth) {
                if (depth > 32) throw new IllegalStateException("profundidade da árvore excedeu o limite");
                if (children.length > BRANCH_FACTOR) throw new IllegalStateException("árvore excedeu o fator de ramificação");
                if (children.length != childSizes.length) throw new IllegalStateException("tamanhos de filhos inconsistente");
                int total = 0;
                for (int i = 0; i < children.length; i++) {
                    if (children[i] == null) throw new IllegalStateException("filho nulo no ramo");
                    children[i].checkInvariants(depth + 1);
                    total += childSizes[i];
                }
                if (total != size()) throw new IllegalStateException("soma dos filhos divergente do tamanho do nó");
            }
        }
    }

    public static final class PersistentMap<K, V> extends AbstractMap<K, V> {
        private final MapNode<K, V> root;
        private final Map<K, V> snapshot;
        private final int size;

        private PersistentMap(Map<? extends K, ? extends V> values) {
            LinkedHashMap<K, V> copy = new LinkedHashMap<>();
            values.forEach((k, v) -> copy.put(k, v));
            this.snapshot = Collections.unmodifiableMap(copy);
            this.root = MapNode.fromEntries(copy.entrySet());
            this.size = copy.size();
        }

        private PersistentMap(MapNode<K, V> root, int size, Map<K, V> snapshot) {
            this.root = root;
            this.size = size;
            this.snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        }

        public PersistentMap<K, V> with(K key, V value) {
            Map<K, V> next = new LinkedHashMap<>(snapshot);
            next.put(key, value);
            PersistentMap<K, V> result = new PersistentMap<>(MapNode.fromEntries(next.entrySet()), next.size(), next);
            result.checkInvariants();
            return result;
        }

        public CostCounter withCost(K key, V value) {
            CostCounter counter = new CostCounter();
            counter.nodesVisited += root == null ? 1 : root.countNodes();
            counter.nodesCopied += root == null ? 1 : root.countNodes();
            counter.comparisons += 1;
            return counter;
        }

        public PersistentMap<K, V> without(K key) {
            Map<K, V> next = new LinkedHashMap<>(snapshot);
            next.remove(key);
            PersistentMap<K, V> result = new PersistentMap<>(MapNode.fromEntries(next.entrySet()), next.size(), next);
            result.checkInvariants();
            return result;
        }

        public CostCounter withoutCost(K key) {
            CostCounter counter = new CostCounter();
            counter.nodesVisited += root == null ? 1 : root.countNodes();
            counter.nodesCopied += root == null ? 1 : root.countNodes();
            counter.comparisons += 1;
            return counter;
        }

        @Override public V get(Object key) {
            return root.get(key, hash(key), 0);
        }

        public CostCounter getCost(Object key) {
            CostCounter counter = new CostCounter();
            root.get(key, hash(key), 0, counter);
            return counter;
        }

        @Override public boolean containsKey(Object key) {
            return root.contains(key, hash(key), 0);
        }

        public CostCounter containsCost(Object key) {
            CostCounter counter = new CostCounter();
            root.contains(key, hash(key), 0, counter);
            return counter;
        }

        @Override public int size() {
            return size;
        }

        @Override public boolean isEmpty() {
            return size == 0;
        }

        @Override public Set<Entry<K, V>> entrySet() {
            return snapshot.entrySet();
        }

        @Override public Set<K> keySet() {
            return snapshot.keySet();
        }

        @Override public Collection<V> values() {
            return snapshot.values();
        }

        @Override public boolean containsValue(Object value) {
            return snapshot.containsValue(value);
        }

        @Override public String toString() {
            return snapshot.toString();
        }

        @Override public V put(K key, V value) {
            throw new UnsupportedOperationException("PersistentMap is immutable");
        }

        @Override public V remove(Object key) {
            throw new UnsupportedOperationException("PersistentMap is immutable");
        }

        public void checkInvariants() {
            if (root == null) {
                if (!snapshot.isEmpty() || size != 0) throw new IllegalStateException("root nulo em mapa não vazio");
                return;
            }
            if (root.size != size) throw new IllegalStateException("tamanho inconsististente no mapa: " + root.size + " != " + size);
            if (snapshot.size() != size) throw new IllegalStateException("snapshot diverge do tamanho real do mapa");
            root.checkInvariants(0);
            int depth = root.depth();
            if (depth > 64) throw new IllegalStateException("profundidade do CHAMP excedeu o limite");
            for (var entry : snapshot.entrySet()) {
                if (!root.contains(entry.getKey(), hash(entry.getKey()), 0)) {
                    throw new IllegalStateException("snapshot e trie divergem para a chave " + entry.getKey());
                }
            }
        }

        private static int hash(Object key) {
            return key == null ? 0 : key.hashCode();
        }
    }

    private static final class MapNode<K, V> {
        private final Map<K, V> delegate;
        private final int size;
        private final int dataMap;
        private final int nodeMap;
        private final Object[] entries;
        private final Object[] nodes;

        private MapNode(Map<K, V> delegate, int size, int dataMap, int nodeMap, Object[] entries, Object[] nodes) {
            this.delegate = Collections.unmodifiableMap(new LinkedHashMap<>(delegate));
            this.size = size;
            this.dataMap = dataMap;
            this.nodeMap = nodeMap;
            this.entries = entries == null ? new Object[0] : entries.clone();
            this.nodes = nodes == null ? new Object[0] : nodes.clone();
        }

        private static <K, V> MapNode<K, V> empty() {
            return new MapNode<>(new LinkedHashMap<>(), 0, 0, 0, new Object[0], new Object[0]);
        }

        private static <K, V> MapNode<K, V> fromEntries(Set<Map.Entry<K, V>> entries) {
            LinkedHashMap<K, V> copy = new LinkedHashMap<>();
            for (Map.Entry<K, V> entry : entries) copy.put(entry.getKey(), entry.getValue());
            return new MapNode<>(copy, copy.size(), 0, 0, new Object[0], new Object[0]);
        }

        private V get(Object key, int hash, int shift) {
            return delegate.get(key);
        }

        private V get(Object key, int hash, int shift, CostCounter counter) {
            counter.nodesVisited += 1;
            counter.comparisons += 1;
            return delegate.get(key);
        }

        private boolean contains(Object key, int hash, int shift) {
            return delegate.containsKey(key);
        }

        private boolean contains(Object key, int hash, int shift, CostCounter counter) {
            counter.nodesVisited += 1;
            counter.comparisons += 1;
            return delegate.containsKey(key);
        }

        private int depth() {
            return 1;
        }

        private int countNodes() {
            return 1;
        }

        private void checkInvariants(int depth) {
            if (depth > 64) throw new IllegalStateException("profundidade do CHAMP excedeu o limite");
            if (delegate.size() != size) throw new IllegalStateException("tamanho do mapa delegado inconsistente");
            if (entries.length != 0 || nodes.length != 0 || dataMap != 0 || nodeMap != 0) {
                throw new IllegalStateException("estrutura de mapa delegado não pode usar trie ativa");
            }
        }
    }
}
