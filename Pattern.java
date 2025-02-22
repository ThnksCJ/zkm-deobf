package dev.jadyen.blunt;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class Pattern {
    private final Node[] opcodes;

    public Pattern(Node... opcodes) {
        this.opcodes = opcodes;
    }

    public record Node(int opcode, Predicate<AbstractInsnNode> predicate) {
        public Node(int opcode) {
            this(opcode, o -> true);
        }
    }

    public static int WILDCARD = -0x12345678;

    public static class Range {
        private final AbstractInsnNode start;
        private final AbstractInsnNode end;
        private final int s_index;
        private final int e_index;

        public Range(AbstractInsnNode start, AbstractInsnNode end, int s_index, int e_index) {
            this.start = start;
            this.end = end;
            this.s_index = s_index;
            this.e_index = e_index;
        }

        public AbstractInsnNode start() {
            return start;
        }

        public AbstractInsnNode end() {
            return end;
        }

        public int s_index() {
            return s_index;
        }

        public int e_index() {
            return e_index;
        }
    }

    public Range match(final MethodNode method, final int offset) {
        final AbstractInsnNode[] instructions = method.instructions.toArray();
        final int pattern_size = opcodes.length;
        if (pattern_size == 0 || pattern_size > instructions.length) return null;
        for (int i = offset; i <= instructions.length - pattern_size; i++)
            if (verify(instructions[i], opcodes[0])) {
                boolean match = true;

                for (int j = 1; j < pattern_size; j++)
                    if (!verify(instructions[i + j], opcodes[j])) {
                        match = false;
                        break;
                    }

                if (match) return new Range(instructions[i], instructions[i + pattern_size - 1], i, i + pattern_size - 1);
            }
        return null;
    }

    public Range[] match_all(final MethodNode method) {
        final AbstractInsnNode[] instructions = method.instructions.toArray();
        final int pattern_size = opcodes.length;
        if (pattern_size == 0 || pattern_size > instructions.length) return null;
        Range[] ranges = new Range[2];

        int top = 0, offset = 0;

        Range current;

        while ((current = match(method, offset)) != null) {
            if (top == ranges.length) ranges = Arrays.copyOf(ranges, top * 2);
            ranges[top++] = current;
            offset = current.e_index + 1;
        }

        if (top == 0) return null;

        return top == ranges.length ? ranges : Arrays.copyOf(ranges, top);
    }

    private boolean verify(AbstractInsnNode instruction, Node node) {
        int opcode = instruction.getOpcode();
        return opcode == WILDCARD || (opcode == node.opcode() || node.opcode() == WILDCARD) && node.predicate().test(instruction);
    }
}