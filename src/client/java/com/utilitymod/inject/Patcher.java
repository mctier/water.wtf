package com.utilitymod.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mixin replacement for late injection: ASM edits to existing method bodies
 * (retransformation forbids new methods/fields), each calling a static method
 * in {@link Hooks}. Patch declarations live in {@link Patches}.
 *
 * <p>Hook descriptors are derived automatically: reference types erase to
 * {@code Object}, primitives stay primitive. So a hook for
 * {@code void attack(PlayerEntity, Entity)} with {@code self + args} is
 * {@code static boolean attackHead(Object self, Object player, Object target)}.
 *
 * <table>
 *   <tr><th>Kind</th><th>Mixin equivalent</th><th>Hook shape</th></tr>
 *   <tr><td>HEAD</td><td>@Inject HEAD</td><td>void (self?, args?)</td></tr>
 *   <tr><td>HEAD_CANCEL</td><td>@Inject HEAD cancellable</td>
 *       <td>void method: boolean (true = return); value method: Object (non-null = return it, unboxed)</td></tr>
 *   <tr><td>RETURN / TAIL</td><td>@Inject RETURN / TAIL</td><td>void (self?, args?)</td></tr>
 *   <tr><td>RETURN_VALUE</td><td>@Inject RETURN + setReturnValue / @ModifyReturnValue</td>
 *       <td>R (R value, self?, args?)</td></tr>
 *   <tr><td>REDIRECT</td><td>@Redirect INVOKE</td><td>R (receiver?, callArgs...)</td></tr>
 *   <tr><td>MODIFY_RESULT</td><td>@ModifyExpressionValue INVOKE</td><td>R (R value, self?)</td></tr>
 *   <tr><td>MODIFY_FIELD_STORE</td><td>@Inject after a field store / @ModifyArg on PUTFIELD</td>
 *       <td>T (T value) - value about to be written ({@code call} = field owner, name, desc)</td></tr>
 * </table>
 *
 * <p>Every patch is idempotent (skipped when the hook call already exists)
 * and failure-safe (the class stays unpatched).
 */
public final class Patcher implements ClassFileTransformer {
    static final String HOOKS = LT.L1;

    public enum Kind { HEAD, HEAD_CANCEL, RETURN, TAIL, RETURN_VALUE, REDIRECT, MODIFY_RESULT, MODIFY_FIELD_STORE }

    /**
     * @param owners      internal class names (Yarn and intermediary)
     * @param methods     method names (Yarn and intermediary)
     * @param desc        exact intermediary/Yarn descriptor(s) of the target method, or null for any
     * @param call        REDIRECT/MODIFY_RESULT: {"owner", "name", "desc"} alternatives of the invoked
     *                    method, repeated in groups of three (Yarn group, intermediary group)
     * @param ordinal     REDIRECT/MODIFY_RESULT: which matching call (0-based), -1 = all
     */
    public record Patch(String[] owners, String[] methods, String[] desc, Kind kind, String hook,
                        boolean self, boolean args, String[] call, int ordinal) {
        boolean matchesMethod(MethodNode m) {
            boolean name = false;
            for (String n : methods) name |= n.equals(m.name);
            if (!name) return false;
            if (desc == null) return true;
            for (String d : desc) if (d.equals(m.desc)) return true;
            return false;
        }

        boolean matchesField(org.objectweb.asm.tree.FieldInsnNode i) {
            for (int g = 0; g + 2 < call.length; g += 3) {
                if (call[g].equals(i.owner) && call[g + 1].equals(i.name) && call[g + 2].equals(i.desc)) return true;
            }
            return false;
        }

        boolean matchesCall(MethodInsnNode i) {
            for (int g = 0; g + 2 < call.length; g += 3) {
                if (call[g].equals(i.owner) && call[g + 1].equals(i.name) && call[g + 2].equals(i.desc)) return true;
            }
            return false;
        }
    }

    private static final Map<String, List<Patch>> BY_CLASS = new HashMap<>();
    public static volatile boolean installed;

    private Patcher() {}

    public static void register(Patch p) {
        for (String o : p.owners()) BY_CLASS.computeIfAbsent(o, k -> new ArrayList<>()).add(p);
    }

    public static synchronized void install(Instrumentation inst) {
        if (installed) return;
        installed = true;
        Patches.registerAll();
        inst.addTransformer(new Patcher(), true);
        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (BY_CLASS.containsKey(c.getName().replace('.', '/')) && inst.isModifiableClass(c)) targets.add(c);
        }
        for (Class<?> c : targets) {
            try {
                inst.retransformClasses(c);
            } catch (Throwable t) {
                System.err.println(LT.L2 + c.getName() + LT.L3 + t);
            }
        }
        System.out.println(LT.L4 + BY_CLASS.size() + LT.L5
                + targets.size() + LT.L6);
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        return name == null ? null : apply(name, loader, bytes);
    }

    /** Patches one class file; null = unchanged. Visible for offline verification. */
    public static byte[] apply(String name, ClassLoader loader, byte[] bytes) {
        List<Patch> patches = BY_CLASS.get(name);
        if (patches == null) return null;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            int applied = 0;
            for (MethodNode m : node.methods) {
                for (Patch p : patches) {
                    if (!p.matchesMethod(m) || callsHook(m, p.hook())) continue;
                    if (applyOne(node, m, p)) applied++;
                    else System.err.println(LT.L7 + p.hook() + LT.L8 + name + LT.L9 + m.name);
                }
            }
            if (applied == 0) return null;
            ClassWriter w = new FrameWriter(loader);
            node.accept(w);
            System.out.println(LT.L10 + name + LT.L11 + applied + LT.L12);
            return w.toByteArray();
        } catch (Throwable t) {
            System.err.println(LT.L13 + name + LT.L14 + t);
            return null;
        }
    }

    private static boolean applyOne(ClassNode owner, MethodNode m, Patch p) {
        boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
        Type[] params = Type.getArgumentTypes(m.desc);
        Type ret = Type.getReturnType(m.desc);
        switch (p.kind()) {
            case HEAD -> {
                InsnList l = loadContext(p, isStatic, params);
                l.add(hookCall(p.hook(), contextTypes(p, isStatic, params), Type.VOID_TYPE));
                m.instructions.insert(l);
                return true;
            }
            case HEAD_CANCEL -> {
                InsnList l = loadContext(p, isStatic, params);
                LabelNode go = new LabelNode();
                if (ret.getSort() == Type.VOID) {
                    l.add(hookCall(p.hook(), contextTypes(p, isStatic, params), Type.BOOLEAN_TYPE));
                    l.add(new JumpInsnNode(Opcodes.IFEQ, go));
                    l.add(new InsnNode(Opcodes.RETURN));
                    l.add(go);
                } else {
                    Type obj = Type.getType(Object.class);
                    l.add(hookCall(p.hook(), contextTypes(p, isStatic, params), obj));
                    LabelNode pop = new LabelNode();
                    l.add(new InsnNode(Opcodes.DUP));
                    l.add(new JumpInsnNode(Opcodes.IFNULL, pop));
                    l.add(unbox(ret));
                    l.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
                    l.add(pop);
                    l.add(new InsnNode(Opcodes.POP));
                }
                m.instructions.insert(l);
                return true;
            }
            case RETURN, TAIL, RETURN_VALUE -> {
                List<AbstractInsnNode> returns = new ArrayList<>();
                for (AbstractInsnNode i : m.instructions) {
                    int op = i.getOpcode();
                    if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) returns.add(i);
                }
                if (returns.isEmpty()) return false;
                if (p.kind() == Kind.TAIL) returns = List.of(returns.getLast());
                for (AbstractInsnNode r : returns) {
                    InsnList l = new InsnList();
                    if (p.kind() == Kind.RETURN_VALUE) {
                        if (ret.getSort() == Type.VOID) return false;
                        // value is on the stack: hook(value, ctx...) -> value
                        l.add(loadContext(p, isStatic, params));
                        List<Type> types = new ArrayList<>();
                        types.add(erase(ret));
                        types.addAll(contextTypes(p, isStatic, params));
                        l.add(hookCall(p.hook(), types, erase(ret)));
                        l.add(cast(ret));
                    } else {
                        l.add(loadContext(p, isStatic, params));
                        l.add(hookCall(p.hook(), contextTypes(p, isStatic, params), Type.VOID_TYPE));
                    }
                    m.instructions.insertBefore(r, l);
                }
                return true;
            }
            case MODIFY_FIELD_STORE -> {
                int seen = 0;
                boolean any = false;
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (!(i instanceof org.objectweb.asm.tree.FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD
                            || !p.matchesField(f)) continue;
                    if (p.ordinal() >= 0 && seen++ != p.ordinal()) continue;
                    Type t = Type.getType(f.desc);
                    InsnList l = new InsnList();
                    l.add(hookCall(p.hook(), List.of(erase(t)), erase(t)));
                    l.add(cast(t));
                    m.instructions.insertBefore(f, l);
                    any = true;
                }
                return any;
            }
            case REDIRECT, MODIFY_RESULT -> {
                int seen = 0;
                boolean any = false;
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (!(i instanceof MethodInsnNode call) || !p.matchesCall(call)) continue;
                    if (p.ordinal() >= 0 && seen++ != p.ordinal()) continue;
                    if (p.kind() == Kind.REDIRECT) redirect(m, call, p);
                    else modifyResult(m, call, p, isStatic);
                    any = true;
                }
                return any;
            }
        }
        return false;
    }

    /** Replace the call with INVOKESTATIC hook(receiver?, callArgs...) keeping the stack contract. */
    private static void redirect(MethodNode m, MethodInsnNode call, Patch p) {
        List<Type> types = new ArrayList<>();
        if (call.getOpcode() != Opcodes.INVOKESTATIC) types.add(Type.getType(Object.class));
        for (Type t : Type.getArgumentTypes(call.desc)) types.add(erase(t));
        Type ret = Type.getReturnType(call.desc);
        InsnList l = new InsnList();
        l.add(hookCall(p.hook(), types, erase(ret)));
        l.add(cast(ret));
        m.instructions.insertBefore(call, l);
        m.instructions.remove(call);
    }

    /** After the call: hook(value, self?) -> value. Value is spilled to a fresh local for ordering. */
    private static void modifyResult(MethodNode m, MethodInsnNode call, Patch p, boolean isStatic) {
        Type ret = Type.getReturnType(call.desc);
        if (ret.getSort() == Type.VOID) return;
        InsnList l = new InsnList();
        List<Type> types = new ArrayList<>();
        types.add(erase(ret));
        if (p.self() && !isStatic) {
            int slot = m.maxLocals;
            m.maxLocals += ret.getSize();
            l.add(new VarInsnNode(ret.getOpcode(Opcodes.ISTORE), slot));
            l.add(new VarInsnNode(ret.getOpcode(Opcodes.ILOAD), slot));
            l.add(new VarInsnNode(Opcodes.ALOAD, 0));
            types.add(Type.getType(Object.class));
        }
        l.add(hookCall(p.hook(), types, erase(ret)));
        l.add(cast(ret));
        m.instructions.insert(call, l);
    }

    private static InsnList loadContext(Patch p, boolean isStatic, Type[] params) {
        InsnList l = new InsnList();
        if (p.self() && !isStatic) l.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (p.args()) {
            int slot = isStatic ? 0 : 1;
            for (Type t : params) {
                l.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
                slot += t.getSize();
            }
        }
        return l;
    }

    private static List<Type> contextTypes(Patch p, boolean isStatic, Type[] params) {
        List<Type> out = new ArrayList<>();
        if (p.self() && !isStatic) out.add(Type.getType(Object.class));
        if (p.args()) for (Type t : params) out.add(erase(t));
        return out;
    }

    private static MethodInsnNode hookCall(String hook, List<Type> args, Type ret) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook,
                Type.getMethodDescriptor(ret, args.toArray(new Type[0])), false);
    }

    static Type erase(Type t) {
        return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY ? Type.getType(Object.class) : t;
    }

    /** CHECKCAST back from the erased Object hook result (no-op for primitives/Object). */
    private static InsnList cast(Type t) {
        InsnList l = new InsnList();
        if ((t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) && !t.getInternalName().equals(LT.L15)) {
            l.add(new TypeInsnNode(Opcodes.CHECKCAST, t.getInternalName()));
        }
        return l;
    }

    /** Object on stack -> value of type t (for HEAD_CANCEL on non-void methods). */
    private static InsnList unbox(Type t) {
        InsnList l = new InsnList();
        String box = switch (t.getSort()) {
            case Type.BOOLEAN -> LT.L16;
            case Type.CHAR -> LT.L17;
            case Type.BYTE, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> LT.L18;
            default -> null;
        };
        if (box == null) {
            l.add(cast(t));
            return l;
        }
        l.add(new TypeInsnNode(Opcodes.CHECKCAST, box));
        String m = switch (t.getSort()) {
            case Type.BOOLEAN -> LT.L19;
            case Type.CHAR -> LT.L20;
            case Type.BYTE -> LT.L21;
            case Type.SHORT -> LT.L22;
            case Type.INT -> LT.L23;
            case Type.LONG -> LT.L24;
            case Type.FLOAT -> LT.L25;
            default -> LT.L26;
        };
        l.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, box, m, LT.L27 + t.getDescriptor(), false));
        return l;
    }

    private static boolean callsHook(MethodNode m, String hook) {
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode call && HOOKS.equals(call.owner) && hook.equals(call.name)) return true;
        }
        return false;
    }

    /**
     * COMPUTE_FRAMES needs common supertypes; resolve them by reading class
     * files through the loader instead of loading classes mid-transform.
     */
    private static final class FrameWriter extends ClassWriter {
        private final ClassLoader loader;

        FrameWriter(ClassLoader loader) {
            super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            this.loader = loader != null ? loader : ClassLoader.getSystemClassLoader();
        }

        @Override
        protected String getCommonSuperClass(String a, String b) {
            List<String> chainA = chain(a);
            for (String s : chain(b)) if (chainA.contains(s)) return s;
            return LT.L15;
        }

        private List<String> chain(String type) {
            List<String> out = new ArrayList<>();
            for (String cur = type; cur != null; cur = superOf(cur)) out.add(cur);
            return out;
        }

        private String superOf(String type) {
            if (LT.L15.equals(type)) return null;
            try (InputStream in = loader.getResourceAsStream(type + LT.L28)) {
                if (in == null) return LT.L15;
                ClassReader r = new ClassReader(in);
                if ((r.getAccess() & Opcodes.ACC_INTERFACE) != 0) return LT.L15;
                return r.getSuperName();
            } catch (Throwable t) {
                return LT.L15;
            }
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe974,0x66a5,0xe2dc,0x2f97,0x7856,0xacdc,0x2995,0x1383,0x393a,0xb7ac,0x012e,0x2767,0x9519,0x9d64,0x8a36,0xadd5,0x84c9,0x070a,0xbf24,0xfb53,0xcb8c,0x5245,0xd91e,0x17e4,0xf5c8,0x6317,0x0e47}, new char[]{0x5226,0xcbe3,0xf5a5,0x7879,0x0e32,0xb7d8,0xadbc,0xe918,0xbf4d,0x956d,0xfb2a,0x0767,0x29fa,0xacb8,0x84e6,0x0147,0x178a,0x637d,0x8a53,0x9d07,0x2fe3,0x668a,0x13cb,0x2708,0xd971,0xe2b7,0x3949}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0927,0x9cef,0x5caf,0xdd30,0xe284,0x85de,0x98af,0x0267,0x7c95,0xa1f8,0x6fc8,0x01a9,0x86eb,0x347e,0x8431,0x96b5,0x83e5,0x3199,0x0926,0xa9c2,0x4502,0x6f30,0xfd4e,0x500a,0xd5bd,0xc086,0xe076,0x511a,0xf360,0x04ed,0xf406,0xda7a,0x0ba6,0x5569,0x6ac7,0x1368}, new char[]{0x04b6,0xa1ad,0x340a,0xa9ab,0x845d,0xdd59,0x869f,0x01d0,0x5157,0x85b1,0xda1e,0xe2d9,0x502a,0x133a,0x0942,0x8391,0x6f42,0x98ce,0xc0e8,0x5cdc,0xd5db,0x5506,0x0215,0xfd23,0x9ccf,0x6aa1,0xf467,0xe01f,0x094a,0x4567,0xf304,0x6fe8,0x31ff,0x7cfa,0x0bd4,0x9695}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x546e,0x8eb9}, new char[]{0x8e83,0x544e,0x48bb,0x1bd4}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa00c,0xeecd,0x4772,0x579c,0xc819,0x0bb6,0xcab4,0x8b71,0x590d,0x1d26,0x7f9a,0xdf21,0x0936,0xc7f6,0x49af,0x6b39,0x73e8,0x1ce5,0x64b8,0xfcd8,0xbef8,0x6f3f,0x7f9a,0xe5c8,0x556f,0x0173,0x6268,0x4aae,0x3117,0x2688,0x2f51,0x14e1}, new char[]{0xc7ad,0x623d,0xe5bc,0x6f56,0xc875,0x1488,0x551b,0xfca1,0x0bfb,0xbe97,0xa068,0x1cb8,0x6498,0x73b8,0x7ffb,0xcac0,0x49cc,0x6b51,0x26ed,0x1d54,0x7fba,0x095f,0xeea3,0xdf52,0x8b05,0x0112,0x2f3d,0x317b,0x57f9,0x5969,0x4748,0x4a8e}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbecc,0x9d47,0x6d7f,0xce1b,0xb2d7,0x2c5b,0x1b02,0x70a7,0xc0ce,0xc5c9,0x0fa0,0xbbbf,0x241a,0xf50b}, new char[]{0x7087,0x6d1c,0xb2bb,0xf56a,0x2c28,0x0fd3,0xbeec,0xc0a0,0x247b,0xce76,0xbbda,0x9d34,0xc5e5,0x1b22}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb3e0,0x3da0,0xce67,0x48e7,0x9df1,0xf139,0x8d45,0x55ac,0xa600,0x8ef3,0x82da,0xace5,0xb2f9,0x594c,0x5706,0xb9ea,0x31ed,0xf68b,0x8283,0xd8f1,0xd714,0x19f0,0x0010,0xdd7f,0x6134,0x67b0,0x897c,0xd17d,0xa52e,0x6674,0xbb74,0xde65,0x6de1,0x1f7b,0xa197,0xcc17,0xfa5f}, new char[]{0xf6ab,0xd11c,0x488b,0xb998,0x55c9,0xfa3e,0xa54a,0x577f,0x1f56,0x5920,0xf156,0x82bb,0xde01,0x8e96,0xce03,0xb3c0,0x1993,0x3dcc,0xd890,0x67c3,0x9d82,0xb29c,0xbb07,0xa620,0x890e,0x6151,0x0064,0xac97,0x318c,0xa1f9,0x6d92,0x82e5,0x661b,0x8d37,0xdd12,0xcc72,0xd770}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdede,0xeed4,0xd60d,0x72bb,0x2e88,0x9af4,0x1eab,0x42e7,0xe4c2,0xe440,0x33f1,0xe592,0x4f59,0xe267,0x4e5b,0xc69e,0xf568,0xe719,0x4937}, new char[]{0x9aaf,0xe74c,0xd679,0xeebd,0x72d7,0xe429,0xe5e6,0x429e,0xde93,0x1ec4,0xc6fa,0x33ac,0x4917,0xe492,0x4e3a,0x2efc,0xf50b,0xe20f,0x4f79}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x64e0,0x066d,0xf449,0x1fa3,0x38a5,0x3c9e,0xf97f,0x1602,0x469b,0x6d8b,0xa649,0x82f2,0x71a6,0x1487,0x756d,0x9700,0x8db1,0x1266,0x3fc0,0x7bde}, new char[]{0x6dab,0x750b,0x38ca,0x9775,0x14e9,0xf91b,0x7186,0x7bb0,0x0602,0xf469,0xa63d,0x8dd0,0x1fd1,0x3fa7,0x6485,0x8286,0x1246,0x46f2,0x166c,0x3cbe}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2dff}, new char[]{0x2dd1,0xfdee,0x19f8,0xefb5}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc545,0xcd6e,0x94b4,0xd85f,0x9030,0x855d,0x179c,0x4997,0xd82a,0xd596,0x19d3,0x84b4,0xc79e,0xf511,0xa1d1,0xcbe1,0x9014,0x31e9,0xb6de,0xfd7a,0xb984}, new char[]{0x17c7,0xb9d1,0xd5e2,0x84dd,0x94d8,0xf578,0xd82b,0x19aa,0x49da,0xc52a,0xa1b5,0xd877,0x9010,0xb68e,0xfd1b,0xcb95,0x9077,0x3181,0xcd0b,0xc7fa,0x857d}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc785,0x68f8}, new char[]{0xc7a5,0x68d0,0x7c52,0xbbba}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd3ec,0x91b0,0x0a8c,0x1260,0x8b5b,0xb00f,0xd7d0,0xd98c,0xcfd5}, new char[]{0x1240,0xd7b8,0x8b34,0x91df,0x0ae7,0xcffd,0xd39f,0xd9a5,0xb026}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3292,0xb71d,0x7c23,0xecee,0xc1f1,0x9aff,0x0b08,0x949e,0x42f1,0xab23,0x8a24,0x3ccc,0x991a,0xd37c,0xed20,0x8d38,0x0655,0xcc56,0x76af,0x8810,0x4b76,0x661a,0x84c9,0xb6d9,0x2af3,0xf07a,0x1fa8,0x20aa,0x7032,0xee66}, new char[]{0x20f1,0x2aa6,0x7c57,0x8d51,0xec82,0x1fc1,0x8864,0xb6a0,0x9957,0x0b67,0xd318,0x706f,0x663a,0x32c2,0x3cad,0xee12,0xf019,0xcc3e,0xb73d,0x4b10,0x76ce,0xab4a,0x8a48,0x94fb,0x4295,0x0675,0xed46,0x84a6,0x9a8d,0xc1d1}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x62b8,0x55ca,0x47fb,0x0a5f,0x3140,0xe8b9,0x8455,0x8189,0x3d9f,0xa3cc,0x4aba,0x0e50,0xbf05,0x94bc,0x442e,0xf0d1,0xd5d0,0x38e5}, new char[]{0x3db3,0x81a9,0x94d0,0x0e35,0x4adc,0xf0a5,0x440e,0x55bf,0x62d6,0x0a2f,0xbf64,0x3134,0x8436,0xd5b8,0xa3a9,0x3881,0xe883,0x47db}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d2d,0x7ba2,0x6125,0x82ae,0x9040,0x53f3,0x26ec,0x6620,0x2e62,0xe491,0x2967,0xedcc,0x08ee,0x9dd8,0xc859,0x893c}, new char[]{0x7bc8,0xe4f0,0x6d5b,0x895d,0x2948,0x2e0e,0x6144,0x539d,0x9dbf,0x08c1,0xed83,0x6642,0x902a,0xc83c,0x82cd,0x2698}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d3a,0xa294,0x988e,0x2a83,0x3ff8,0xb65c,0xb287,0x5123,0xa65d,0xb846,0x1bd2,0xfa05,0x71ab,0x270a,0x3b76,0x7d86,0xafa4}, new char[]{0xafce,0x276b,0x5155,0x3b17,0x7184,0x1bbe,0xb827,0xfa6b,0xb63b,0x5d15,0x7dc4,0xa632,0xa2fb,0xb2eb,0x2ae6,0x98ef,0x3f96}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc81a,0xfd1a,0xb11f,0x195c,0x8627,0x9243,0x3e42,0x7a58,0x721f,0xccc7,0x2312,0x27e3,0xeae3,0x826d,0x7274,0xdbab,0x8688,0xfa84,0x0c9c}, new char[]{0x2378,0xb17e,0x3e34,0xfae5,0x926c,0x7a34,0xea82,0x278d,0x7213,0x8608,0x191f,0x0cf4,0x820c,0x726d,0x86e9,0xcca4,0xdbdf,0xc87f,0xfd68}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b57,0x270e,0xcf5a,0x5c8f,0x50a2,0xcb16,0x4960,0x4e3c,0xc95d,0x57f6,0x34b1,0xcdef,0xa611,0x386b,0x8e3b,0x852b}, new char[]{0xc937,0xcf3b,0x5cf9,0x8e5a,0x3844,0x490c,0xa670,0x4e52,0x2769,0xcb39,0x34ff,0x50d7,0x5b3a,0x8549,0xcd8a,0x5784}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x350c,0x11e8,0xb4c1,0x505b,0x1373,0xa67d,0x4796,0x5941,0x1d34,0x9f19,0xb0a7,0x7960}, new char[]{0xb0c5,0x47f9,0x5034,0x1d58,0x7905,0x356d,0x1186,0x1325,0xb4a0,0xa611,0x5934,0x9f7c}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8938,0xb32d,0xba25,0xec28,0x1800,0x5a7f,0xbc92,0x4854,0x7e4d}, new char[]{0x7e2e,0xec40,0xb34c,0x1872,0x4802,0x5a1e,0xbcfe,0xba50,0x895d}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef8f,0xb6e2,0x8c99,0xf5f7,0xd6c4,0x0413,0xeeaa,0x511f,0x60bd}, new char[]{0xeec8,0x8ce0,0xeffb,0x60d8,0xb6b4,0x0472,0x5173,0xf582,0xd6a1}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6587,0x33ca,0x719a,0xf88c,0xb61d,0xe141,0xe877,0x1c03,0x6122,0x5694}, new char[]{0x1c70,0x614a,0xe12e,0xb66f,0x33be,0x71cc,0x65e6,0xf8e0,0xe802,0x56f1}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8783,0xaab9,0x1ace,0x036f,0x107b,0x5e5a,0x220a,0xe2c9}, new char[]{0x0306,0x2264,0xaacd,0x1a98,0x5e3b,0x1017,0xe2bc,0x87e6}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf792,0x1e46,0xc7bd,0x6f93,0x091b,0x4233,0xbc8b,0xa53e,0x1e43}, new char[]{0x6fff,0xf7fd,0xa550,0x1e24,0x1e10,0xc7dc,0xbce7,0x4246,0x097e}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe562,0xad4c,0xbe6d,0x83c6,0x08cc,0xf420,0xd78f,0x1a4c,0x467f,0x6c38}, new char[]{0xd7e9,0xbe01,0xad23,0x08ad,0x1a38,0xe534,0x83a7,0xf44c,0x6c4d,0x461a}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7244,0xfddf,0xa996,0xc405,0x9575,0xa103,0xe78c,0x16a7,0xb8c5,0xfce1,0x2f95}, new char[]{0x2ff1,0xa16c,0x9500,0xfdbd,0x16cb,0xe7e9,0xa9c0,0xc464,0xfc8d,0xb8b0,0x7221}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7759,0x73a9}, new char[]{0x7771,0x7380,0xb254,0x9b34}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b8d,0x9858,0x0e0a,0x9ee3,0xa703,0x687c}, new char[]{0x6852,0x983b,0x9e8f,0x0e6b,0xa770,0x0bfe}).plainString();
    }
}
