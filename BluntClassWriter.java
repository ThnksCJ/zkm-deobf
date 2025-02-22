package dev.jadyen.blunt;

import org.objectweb.asm.ClassWriter;

public class BluntClassWriter extends ClassWriter {
    public BluntClassWriter() {
        super(0);
    }

    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        return "java/lang/Object";
    }
}
