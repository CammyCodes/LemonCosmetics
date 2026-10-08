package com.smartypantsltd.lemoncosmetics;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promises, checked rather than trusted: a look is only ever chosen by the
 * player's own rules, and nothing in the mod can send anything to a server or
 * write to a real item.
 */
class SafetyTest {

    // --- which rule restyles an item -------------------------------------------------

    private static Config.ItemRule rule(String target, String name, String look) {
        Config.ItemRule r = new Config.ItemRule();
        r.target = target;
        r.nameContains = name;
        r.lookItem = look;
        r.lookCmd = 1;
        return r;
    }

    @Test
    void aRuleOnlyMatchesItsOwnItem() {
        List<Config.ItemRule> rules = List.of(rule("minecraft:netherite_pickaxe", "", "minecraft:netherite_pickaxe"));
        assertSame(rules.get(0), Rules.pick(rules, "minecraft:netherite_pickaxe", "Netherite Pickaxe"));
        assertNull(Rules.pick(rules, "minecraft:netherite_sword", "Netherite Sword"));
        assertNull(Rules.pick(rules, "minecraft:diamond_pickaxe", "Diamond Pickaxe"));
    }

    @Test
    void aNamedRuleBeatsAPlainOneAndIgnoresCase() {
        Config.ItemRule plain = rule("minecraft:netherite_pickaxe", "", "a");
        Config.ItemRule named = rule("minecraft:netherite_pickaxe", "ethan", "b");
        List<Config.ItemRule> rules = List.of(plain, named);
        assertSame(named, Rules.pick(rules, "minecraft:netherite_pickaxe", "Ethan's Pick"));
        assertSame(plain, Rules.pick(rules, "minecraft:netherite_pickaxe", "Spare pick"));
    }

    @Test
    void disabledOrUnchosenRulesDoNothing() {
        Config.ItemRule off = rule("minecraft:netherite_pickaxe", "", "a");
        off.enabled = false;
        Config.ItemRule blank = rule("minecraft:netherite_pickaxe", "", "");
        assertNull(Rules.pick(List.of(off, blank), "minecraft:netherite_pickaxe", "x"));
    }

    // --- nothing reaches the server, nothing changes a real item ----------------------

    /** Classes the mod must never reference: each is a way to tell the server something. */
    private static final List<String> FORBIDDEN_OWNERS = List.of(
            "net/minecraft/network/Connection",
            "net/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket",
            "net/minecraft/network/protocol/game/ServerboundContainerClickPacket",
            "net/minecraft/network/protocol/game/ServerboundChatPacket",
            "net/minecraft/network/protocol/game/ServerboundChatCommandPacket",
            "net/minecraft/client/multiplayer/MultiPlayerGameMode",
            "net/minecraft/client/multiplayer/ClientPacketListener");

    /** Calls that would change a real stack or the player's inventory. */
    private static final List<String> FORBIDDEN_CALLS = List.of(
            "net/minecraft/world/entity/player/Inventory.setItem",
            "net/minecraft/world/entity/LivingEntity.setItemSlot",
            "net/minecraft/world/entity/player/Player.setItemSlot",
            "net/minecraft/client/player/LocalPlayer.setItemSlot",
            "net/minecraft/world/entity/LivingEntity.setItemInHand");

    @Test
    void nothingInTheModCanTalkToTheServer() throws IOException {
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        for (Path cls : classes()) {
            scanned++;
            try (InputStream in = Files.newInputStream(cls)) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
                                if (FORBIDDEN_OWNERS.contains(owner) || FORBIDDEN_CALLS.contains(owner + "." + mname)) {
                                    problems.add(cls.getFileName() + ": " + owner + "." + mname);
                                }
                            }

                            @Override
                            public void visitTypeInsn(int op, String type) {
                                if (type.startsWith("net/minecraft/network/protocol/") && type.contains("Serverbound")) {
                                    problems.add(cls.getFileName() + ": new " + type);
                                }
                            }
                        };
                    }
                }, 0);
            }
        }
        assertTrue(scanned > 5, "found the compiled classes (" + scanned + ")");
        assertEquals(List.of(), problems);
    }

    /** A real stack is only ever copied before it is changed: every ItemStack.set is on a copy. */
    @Test
    void dressingOnlyChangesCopies() throws IOException {
        List<String> setters = new ArrayList<>();
        for (Path cls : classes()) {
            try (InputStream in = Files.newInputStream(cls)) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        String where = cls.getFileName() + "." + name;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
                                if (owner.equals("net/minecraft/world/item/ItemStack") && mname.equals("set")) {
                                    setters.add(where);
                                }
                            }
                        };
                    }
                }, 0);
            }
        }
        // The two places that change components: wear() on stack.copy(), armour() on worn.copy() / a new stack.
        assertEquals(List.of("Dresser.class.armour", "Dresser.class.wear", "Dresser.class.wear"),
                setters.stream().sorted().toList());
    }

    private static List<Path> classes() throws IOException {
        List<Path> out = new ArrayList<>();
        String dirs = System.getProperty("lemoncosmetics.classes");
        for (String d : dirs.split(java.io.File.pathSeparator)) {
            Path p = Path.of(d);
            if (!Files.isDirectory(p)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(p)) {
                s.filter(f -> f.toString().endsWith(".class")).forEach(out::add);
            }
        }
        return out;
    }
}
