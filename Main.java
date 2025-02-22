package dev.jadyen.blunt;

import dev.jadyen.insomnia.VirtualMachine;
import dev.jadyen.insomnia.internal.ClassInstance;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.File;

public class Main implements Opcodes {
    private static final Pattern ZKM_EXCEPTIONS = new Pattern(
            new Pattern.Node(INVOKESTATIC),
            new Pattern.Node(ATHROW)
    );

    private static final Pattern clinit_clean1 = new Pattern(
            new Pattern.Node(DUP_X2),
            new Pattern.Node(POP),
            new Pattern.Node(LLOAD),
            new Pattern.Node(LXOR),
            new Pattern.Node(DUP2_X1),
            new Pattern.Node(POP2),
            new Pattern.Node(POP)
    );

    private static final Pattern clinit_clean2 = new Pattern(
            new Pattern.Node(ALOAD),
            new Pattern.Node(PUTSTATIC),
            new Pattern.Node(ICONST_2),
            new Pattern.Node(ANEWARRAY),
            new Pattern.Node(PUTSTATIC)
    );

    private static final Pattern INTEGERS = new Pattern(
            new Pattern.Node(SIPUSH),
            new Pattern.Node(LDC, k -> k instanceof LdcInsnNode ldc && ldc.cst instanceof Long),
            new Pattern.Node(INVOKESTATIC, k -> k instanceof MethodInsnNode method && method.desc.equals("(IJ)I"))
    );

    private static final Pattern STRINGS = new Pattern(
            new Pattern.Node(SIPUSH),
            new Pattern.Node(SIPUSH),
            new Pattern.Node(INVOKESTATIC, k -> k instanceof MethodInsnNode method && method.desc.equals("(II)Ljava/lang/String;"))
    );

    static VirtualMachine vm;

    public static void main(String[] args) {
        File input = new File("grub/br.jar");

        try {
            vm = new VirtualMachine();

            System.out.println("Blunt | Sponsored by Jadyen and Insomnia");
            System.out.println("Lighting up...");
            System.out.println("Processing: " + input.getName());

            final JarReader jar = JarReader.read(input);

            System.out.println("Loaded: " + jar.getPool().getClasses().size() + " classes");

            System.out.println("Backrolling zkm clinit");

            for (final ClassNode node : jar.getPool()) {
                boolean hasZkmMethods = node.methods.stream()
                        .anyMatch(method -> INTEGERS.match_all(method) != null || STRINGS.match_all(method) != null);

                if (hasZkmMethods) {
                    for (final MethodNode method : node.methods) {
                        for (final TryCatchBlockNode block : method.tryCatchBlocks.toArray(new TryCatchBlockNode[0])) {
                            final LabelNode handler = block.handler;
                            if (!(handler.getNext().getNext() instanceof MethodInsnNode invoke)) continue;
                            if (invoke.getNext().getOpcode() != ATHROW) continue;
                            final String ret = Type.getReturnType(invoke.desc).getDescriptor();
                            if (!(block.type.equals(ret.substring(1, ret.length() - 1)))) continue;
                            method.tryCatchBlocks.remove(block);
                        }

                        if (!method.name.equals("<clinit>")) continue;

                        Pattern.Range range = clinit_clean1.match(method, 0);

                        if (range == null) range = clinit_clean2.match(method, 0);
                        if (range == null) continue;

                        AbstractInsnNode curr = range.start();

                        while (curr != null && curr.getOpcode() != GOTO)
                            curr = (range == clinit_clean1.match(method, 0)) ? curr.getPrevious() : curr.getNext();

                        method.instructions.set(curr, new InsnNode(RETURN));
                    }
                } else {
                    System.out.println("fallback to full clinit clean -> " + node.name);

                    for (final MethodNode method : node.methods) {
                        if (method.name.equals("<clinit>")) {
                            method.instructions.clear();
                            method.instructions.add(new InsnNode(RETURN));
                        }
                    }

                    continue;
                }

                System.out.println("Cleaned clinit -> " + node.name);
            }

            System.out.println("Cleaning zkm clinit done (hopefully)");

            jar.export("clinitCleaned.jar");

            final File cleaned = new File("clinitCleaned.jar");
            vm.initialize(cleaned);
            final JarReader jar2 = JarReader.read(cleaned);

            int globalMethodsUnwrapped = 0;

            for (final ClassNode node : jar2.getPool()) {
                System.out.println("-- Processing: " + node.name + " --");

                try {
                    final ClassInstance instance = vm.loadClass(node.name.replace("/", "."), false);

                    for (final MethodNode method : node.methods) {
                        Pattern.Range[] integers = INTEGERS.match_all(method);
                        Pattern.Range[] strings = STRINGS.match_all(method);

                        if (integers != null)
                            for (final Pattern.Range range : integers) {
                                final MethodInsnNode inv = (MethodInsnNode) range.end();
                                final int r = (int) instance.getMethod(inv.name, inv.desc).getMethod().invoke(null, ((IntInsnNode) range.start()).operand, ((LdcInsnNode) range.start().getNext()).cst);
                                method.instructions.remove(range.start().getNext());
                                method.instructions.remove(range.start());
                                method.instructions.set(range.end(), new LdcInsnNode(r));

                                globalMethodsUnwrapped++;
                            }

                        if (strings != null)
                            for (final Pattern.Range range : strings) {
                                final MethodInsnNode inv = (MethodInsnNode) range.end();
                                System.out.println("found zkm string invoke -> [" + ((IntInsnNode) range.start()).operand + ", " + ((IntInsnNode) range.start().getNext()).operand + "]");
                                final String output = (String) instance.getMethod(inv.name, inv.desc).getMethod().invoke(null, ((IntInsnNode) range.start()).operand, ((IntInsnNode) range.start().getNext()).operand);
                                System.out.println("compute -> `" + output + "`");
                                method.instructions.remove(range.start().getNext());
                                method.instructions.remove(range.start());
                                method.instructions.set(range.end(), new LdcInsnNode(output));

                                globalMethodsUnwrapped++;
                            }
                    }

                    node.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "sponsor", "Ljava/lang/String;", null, "Blunt | Sponsored by Jadyen.dev"));
                } catch (final Throwable _t) {
                    System.out.println("failed load class -> " + node.name);
                }

                System.out.println("-- Finished: " + node.name + " --");
            }

            System.out.println("Blunt finished, smoked " + globalMethodsUnwrapped + " zkm calls");

            jar2.export("out.jar");
        } catch (final Throwable _t) {
            _t.printStackTrace(System.err);
        }
    }
}
