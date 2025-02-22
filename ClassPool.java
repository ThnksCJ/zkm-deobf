package dev.jadyen.blunt;

import lombok.Getter;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Iterator;

@Getter
public class ClassPool implements Iterable<ClassNode> {
    private final ArrayList<ClassNode> classes = new ArrayList<>();

    public void add(final ClassNode klass) {
        classes.add(klass);
    }

    @Override
    public Iterator<ClassNode> iterator() {
        return classes.iterator();
    }

    @Override
    public String toString() {
        return classes.toString();
    }
}