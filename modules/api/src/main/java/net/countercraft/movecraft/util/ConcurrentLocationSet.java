package net.countercraft.movecraft.util;

import net.countercraft.movecraft.MovecraftLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class ConcurrentLocationSet implements Set<MovecraftLocation> {

    private static final int LOW_MASK_LENGTH = 16;
    private static final int TREE_DEPTH = 12;
    private static final int TREE_MASK_LENGTH = (64 - LOW_MASK_LENGTH)/TREE_DEPTH;
    private static final int LOW_MASK  = 0b1111111111111111;
    private static final long HIGH_MASK = ~((long)LOW_MASK);

    private final BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<BitTreeNode<AtomicBitSet>>>>>>>>>>>> tree = new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new BitTreeNode<>(() -> new AtomicBitSet(LOW_MASK + 1)))))))))))));
    private final LongAdder size = new LongAdder();

    @Override
    public int size() {
        return size.intValue();
    }

    @Override
    public boolean isEmpty() {
        return size.intValue() == 0;
    }

    @Override
    public boolean contains(Object o) {
        if(o instanceof MovecraftLocation){
            MovecraftLocation location = (MovecraftLocation) o;
            long packed = location.pack();
            var suffix = this.getPrefixLeafIfPresent(packed);
            if(suffix == null){
                return false;
            }
            return !suffix.get((int) location.pack() & LOW_MASK);
        }
        return false;
    }

    @NotNull
    @Override
    public Iterator<MovecraftLocation> iterator() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(MovecraftLocation location) {
        long packed = location.pack();
        boolean out = !this.getPrefixLeaf(packed).add((int)packed & LOW_MASK);
        if(out){
            size.increment();
        }
        return out;
    }

    @Nullable
    private AtomicBitSet getPrefixLeafIfPresent(long path){
        BitTreeNode<? extends BitTreeNode<?>> top = tree;
        path >>>= LOW_MASK_LENGTH;
        for(int i = 0; i < TREE_DEPTH - 2; i++) {
            top = (BitTreeNode<? extends BitTreeNode<?>>) top.getIfPresent(path & BitTreeNode.TREE_MASK);
            if (top == null) {
                return null;
            }
            path >>>= TREE_MASK_LENGTH;
        }
        BitTreeNode<BitTreeNode<AtomicBitSet>> a = (BitTreeNode<BitTreeNode<AtomicBitSet>>) top;
        var b = a.getIfPresent(path & BitTreeNode.TREE_MASK);
        if(b == null){
            return null;
        }
        var suffix = b.getIfPresent((path >>> TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK);
        return suffix == null ? null : getPrefixLeaf(path);
    }

    @NotNull
    private AtomicBitSet getPrefixLeaf(long path){
        return tree
                .get((path >>>= LOW_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get((path >>>= TREE_MASK_LENGTH) & BitTreeNode.TREE_MASK)
                .get(path >>> TREE_MASK_LENGTH & BitTreeNode.TREE_MASK);
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends MovecraftLocation> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    private static class BitTreeNode<T>{
        public static final int TREE_MASK = 0b1111;
        private static final int TREE_WIDTH = TREE_MASK + 1;
        private final AtomicReferenceArray<T> children;
        private final Supplier<T> initializer;

        private BitTreeNode(@NotNull Supplier<T> initializer) {
            children = new AtomicReferenceArray<>(TREE_WIDTH);
            this.initializer = initializer;
        }

        @Nullable
        public T getIfPresent(long index){
            return getIfPresent((int) index);
        }

        @Nullable
        public T getIfPresent(int index){
            if(index < 0 || index > TREE_WIDTH){
                throw new IndexOutOfBoundsException(String.format("Index %d must be in range <0,%d>", index, TREE_WIDTH));
            }
            return children.get(index);
        }

        @NotNull
        public T get(long index){
            return get((int) index);
        }

        @NotNull
        public T get(int index){
            if(index < 0 || index > TREE_WIDTH){
                throw new IndexOutOfBoundsException(String.format("Index %d must be in range <0,%d>", index, TREE_WIDTH));
            }
            var fetch = children.get(index);
            if(fetch == null)
                return children.updateAndGet(index, (previous) -> previous == null ? initializer.get() : previous);
            return fetch;
        }
    }

}