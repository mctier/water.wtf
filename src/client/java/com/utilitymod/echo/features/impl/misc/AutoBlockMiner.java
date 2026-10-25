package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BlockPickerSetting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Baritone-style pathfinder + miner.
 *
 * <p>Goals (block / xz / y-level / near-ore), A* over feet positions with tick-based costs
 * (sprint/walk, diagonals, step up, falls, digging priced by real break time with your best tool,
 * bridging and pillaring with throwaway blocks, swimming), partial paths with re-planning, live
 * path validation, smooth rotations, sprinting, and drop pickup after mining.
 *
 * <p>Chat commands (prefix {@code #}): {@code #mine <block...>}, {@code #goto x y z | x z | y},
 * {@code #stop}, {@code #pause}, {@code #resume}, {@code #help}.
 */
public class AutoBlockMiner extends Feature {
    private final BlockPickerSetting blocks = new BlockPickerSetting(LT.L1,
            LT.L2, LT.L3, LT.L4,
            LT.L5, LT.L6, LT.L7, LT.L8);
    private final BoolSetting mineOnEnable = new BoolSetting(LT.L9, false);
    private final BoolSetting pauseInCombat = new BoolSetting(LT.L10, true);
    private final IntSetting radius = new IntSetting(LT.L11, 64, 8, 160);
    private final BoolSetting allowBreak = new BoolSetting(LT.L12, true);
    private final BoolSetting allowPlace = new BoolSetting(LT.L13, true);
    private final BoolSetting allowSprint = new BoolSetting(LT.L14, true);
    private final BoolSetting allowWater = new BoolSetting(LT.L15, true);
    private final IntSetting maxFall = new IntSetting(LT.L16, 3, 1, 20);
    private final BoolSetting pickupDrops = new BoolSetting(LT.L17, true);
    private final BoolSetting stopWhenFull = new BoolSetting(LT.L18, true);
    private final BoolSetting chatCommands = new BoolSetting(LT.L19, true);
    private final BoolSetting showPath = new BoolSetting(LT.L20, true);

    private static final int MAX_NODES = 60000;
    private static final double REACH = 4.4;
    private static final double COST_INF = 1e6;
    // tick costs (same idea as Baritone's ActionCosts)
    private static final double WALK = 4.633, SPRINT = 3.564, DIAG = 1.4142, JUMP = 2.5, FALL_PER_BLOCK = 1.2,
            PLACE = 8, WATER = 9.1;

    private static final Set<Item> THROWAWAY = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.NETHERRACK,
            Items.STONE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.DEEPSLATE, Items.BLACKSTONE, Items.END_STONE);

    private static AutoBlockMiner instance;

    // ---------------------------------------------------------------- goals

    private interface Goal {
        boolean isIn(BlockPos feet);
        double heuristic(BlockPos feet);
        String describe();
    }

    private record GoalBlock(BlockPos pos) implements Goal {
        public boolean isIn(BlockPos f) { return f.equals(pos); }
        public double heuristic(BlockPos f) { return dist3(f, pos); }
        public String describe() { return pos.toShortString(); }
    }

    private record GoalXZ(int x, int z) implements Goal {
        public boolean isIn(BlockPos f) { return f.getX() == x && f.getZ() == z; }
        public double heuristic(BlockPos f) { double dx = f.getX() - x, dz = f.getZ() - z; return Math.sqrt(dx * dx + dz * dz) * SPRINT; }
        public String describe() { return x + LT.L21 + z; }
    }

    private record GoalY(int y) implements Goal {
        public boolean isIn(BlockPos f) { return f.getY() == y; }
        public double heuristic(BlockPos f) { return Math.abs(f.getY() - y) * WALK; }
        public String describe() { return LT.L22 + y; }
    }

    /** Stand where the ore is within reach (mining checks line of sight itself). */
    private record GoalMine(BlockPos ore) implements Goal {
        public boolean isIn(BlockPos f) {
            if (ore.equals(f) || ore.equals(f.up())) return false;
            Vec3d eye = new Vec3d(f.getX() + 0.5, f.getY() + 1.62, f.getZ() + 0.5);
            return eye.squaredDistanceTo(Vec3d.ofCenter(ore)) <= REACH * REACH;
        }
        public double heuristic(BlockPos f) { return Math.max(0, dist3(f, ore) - REACH * SPRINT); }
        public String describe() { return ore.toShortString(); }
    }

    private static double dist3(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * SPRINT + Math.abs(dy) * (dy < 0 ? WALK + JUMP : WALK * 0.6);
    }

    // ---------------------------------------------------------------- moves

    private enum Kind { TRAVERSE, DIAGONAL, ASCEND, DESCEND, PILLAR, DIG_DOWN, SWIM_UP }

    private record Step(Kind kind, BlockPos from, BlockPos to) {}

    // ---------------------------------------------------------------- state

    private Set<Identifier> lastSel = Set.of();
    private Set<Block> mineSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockPos> blacklist = new HashSet<>();
    private final Map<BlockState, Double> breakCache = new IdentityHashMap<>();

    private Goal goal;
    private boolean mining, paused, canPlace;
    private List<Step> path = List.of();
    private int index;
    private BlockPos breaking, mineTarget;
    private ItemEntity pickup;
    private long pickupUntil;
    private int stuckTicks, oreScanCooldown, replans;
    private Vec3d lastPos, aimPoint;

    public AutoBlockMiner() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8aa,0x9631,0x82c9,0x37d8,0x0b32,0x1c97,0x226c,0xb166,0xd12a,0xbc01}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).toString(), LT.L23, Category.UTILITY));
        instance = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mineOnEnable.getValue() && goal == null) mining = true;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        cancel();
        blacklist.clear();
    }

    @Override
    public CharSequence concat() {
        if (paused) return LT.L24;
        if (goal == null) return mining ? LT.L25 : LT.L26;
        return (mining ? LT.L27 : LT.L28) + goal.describe();
    }

    private void cancel() {
        releaseKeys();
        if (breaking != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
        goal = null;
        mining = false;
        mineTarget = null;
        pickup = null;
        path = List.of();
        index = 0;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    private void say(String msg) {
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal(LT.L29 + msg));
    }

    // ---------------------------------------------------------------- chat commands

    /** ClientPlayNetworkHandler.sendChatMessage HEAD: true = handled here (never sent to the server). */
    public static boolean onChat(String msg) {
        AutoBlockMiner m = instance;
        if (m == null || msg == null || !msg.startsWith(LT.L30) || !m.chatCommands.getValue()) return false;
        try {
            m.command(msg.substring(1).trim());
        } catch (Exception e) {
            m.say(LT.L31 + e.getMessage());
        }
        return true;
    }

    private void command(String line) {
        String[] a = line.split(LT.L32);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "stop", "cancel" -> {
                cancel();
                say(LT.L34);
            }
            case "pause" -> {
                paused = true;
                releaseKeys();
                say(LT.L35);
            }
            case "resume" -> {
                paused = false;
                say(LT.L36);
            }
            case "goto" -> {
                BlockPos me = mc.player.getBlockPos();
                Goal g;
                if (a.length == 4) g = new GoalBlock(new BlockPos(coord(a[1], me.getX()), coord(a[2], me.getY()), coord(a[3], me.getZ())));
                else if (a.length == 3) g = new GoalXZ(coord(a[1], me.getX()), coord(a[2], me.getZ()));
                else if (a.length == 2) g = new GoalY(coord(a[1], me.getY()));
                else throw new IllegalArgumentException(LT.L37);
                if (!isEnabled()) toggle();
                cancel();
                goal = g;
                replans = 0;
                say(LT.L38 + g.describe());
                plan();
            }
            case "mine" -> {
                if (a.length < 2) throw new IllegalArgumentException(LT.L39);
                Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int i = 1; i < a.length; i++) {
                    String id = a[i].contains(LT.L40) ? a[i] : LT.L41 + a[i];
                    var b = Registries.BLOCK.getOptionalValue(Identifier.tryParse(id));
                    if (b.isEmpty()) throw new IllegalArgumentException(LT.L42 + a[i]);
                    set.add(b.get());
                    // "#mine diamond_ore" also takes the deepslate variant
                    if (!id.contains(LT.L43)) Registries.BLOCK.getOptionalValue(Identifier.tryParse(id.replace(LT.L41, LT.L44))).ifPresent(set::add);
                }
                if (!isEnabled()) toggle();
                cancel();
                mineSet = set;
                lastSel = Set.copyOf(blocks.getSelectedIds()); // the command wins until the picker changes
                mining = true;
                say(LT.L45 + set.size() + LT.L46);
            }
            case "help" -> say(LT.L47);
            default -> say(LT.L48);
        }
    }

    private static int coord(String s, int rel) {
        if (s.startsWith(LT.L49)) return rel + (s.length() > 1 ? Integer.parseInt(s.substring(1)) : 0);
        return (int) Math.floor(Double.parseDouble(s));
    }

    // ---------------------------------------------------------------- world model

    private boolean lava(BlockPos p) {
        return mc.world.getFluidState(p).isIn(FluidTags.LAVA);
    }

    private boolean water(BlockPos p) {
        return mc.world.getFluidState(p).isIn(FluidTags.WATER);
    }

    private static boolean avoid(BlockState s) {
        return s.isOf(Blocks.COBWEB) || s.isOf(Blocks.SWEET_BERRY_BUSH) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE)
                || s.isOf(Blocks.POWDER_SNOW) || s.isOf(Blocks.MAGMA_BLOCK) || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.WITHER_ROSE);
    }

    /** Body can be in this cell without breaking anything. */
    private boolean passable(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        if (lava(p) || avoid(s)) return false;
        if (water(p)) return allowWater.getValue();
        return s.getCollisionShape(mc.world, p).isEmpty();
    }

    /** Something you can stand on. */
    private boolean solid(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        if (avoid(s) || lava(p)) return false;
        var shape = s.getCollisionShape(mc.world, p);
        return !shape.isEmpty() && shape.getMax(Direction.Axis.Y) >= 0.5;
    }

    /** Ticks to break with the best hotbar tool, or COST_INF if we must not. */
    private double breakTicks(BlockPos p) {
        if (!allowBreak.getValue()) return COST_INF;
        BlockState s = mc.world.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return COST_INF;
        float hard = s.getHardness(mc.world, p);
        if (hard < 0) return COST_INF;
        for (Direction d : Direction.values()) {
            if (lava(p.offset(d))) return COST_INF;
            if (d != Direction.DOWN && water(p.offset(d)) && !allowWater.getValue()) return COST_INF;
        }
        if (mc.world.getBlockState(p.up()).getBlock() instanceof FallingBlock) return COST_INF; // gravel/sand avalanche
        return breakCache.computeIfAbsent(s, st -> {
            if (hard == 0) return 1.0;
            var inv = mc.player.getInventory();
            float speed = 1;
            boolean harvest = !st.isToolRequired();
            for (int i = 0; i < 9; i++) {
                ItemStack it = inv.getStack(i);
                speed = Math.max(speed, it.getMiningSpeedMultiplier(st));
                if (it.isSuitableFor(st)) harvest = true;
            }
            double delta = speed / hard / (harvest ? 30.0 : 100.0);
            return Math.ceil(1.0 / delta) + 2;
        });
    }

    /** Cost to make the cell passable: 0 if already, break ticks otherwise. */
    private double clear(BlockPos p) {
        return passable(p) ? 0 : breakTicks(p);
    }

    private boolean replaceable(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        return (s.isReplaceable() || s.isAir()) && !lava(p);
    }

    private boolean standable(BlockPos feet) {
        return solid(feet.down()) || (allowWater.getValue() && water(feet));
    }

    private boolean hasThrowaway() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (THROWAWAY.contains(inv.getStack(i).getItem())) return true;
        return false;
    }

    // ---------------------------------------------------------------- A*

    private record Node(BlockPos pos, double g, double f) {}

    private List<Step> search(BlockPos start, Goal g) {
        breakCache.clear();
        canPlace = allowPlace.getValue() && hasThrowaway();
        PriorityQueue<Node> open = new PriorityQueue<>((x, y) -> Double.compare(x.f(), y.f()));
        Map<BlockPos, Double> gs = new HashMap<>();
        Map<BlockPos, Step> came = new HashMap<>();
        open.add(new Node(start, 0, g.heuristic(start)));
        gs.put(start, 0.0);
        BlockPos best = start;
        double bestH = g.heuristic(start);
        int n = 0;
        while (!open.isEmpty() && n++ < MAX_NODES) {
            Node cur = open.poll();
            if (cur.g() > gs.getOrDefault(cur.pos(), Double.MAX_VALUE)) continue;
            if (g.isIn(cur.pos())) return rebuild(came, cur.pos());
            double h = g.heuristic(cur.pos());
            if (h < bestH) { bestH = h; best = cur.pos(); }
            for (Step s : neighbours(cur.pos())) {
                double c = cost(s);
                if (c >= COST_INF) continue;
                double ng = cur.g() + c;
                if (ng < gs.getOrDefault(s.to(), Double.MAX_VALUE)) {
                    gs.put(s.to(), ng);
                    came.put(s.to(), s);
                    open.add(new Node(s.to(), ng, ng + g.heuristic(s.to())));
                }
            }
        }
        // partial path toward the closest node; the next segment is planned when we get there
        return best.equals(start) ? List.of() : rebuild(came, best);
    }

    private static List<Step> rebuild(Map<BlockPos, Step> came, BlockPos end) {
        List<Step> out = new ArrayList<>();
        for (Step s = came.get(end); s != null && out.size() < 4000; s = came.get(s.from())) out.add(s);
        Collections.reverse(out);
        return out;
    }

    private List<Step> neighbours(BlockPos a) {
        List<Step> out = new ArrayList<>(24);
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos b = a.offset(d);
            out.add(new Step(Kind.TRAVERSE, a, b));
            out.add(new Step(Kind.ASCEND, a, b.up()));
            for (int k = 1; k <= maxFall.getValue(); k++) out.add(new Step(Kind.DESCEND, a, b.down(k)));
        }
        out.add(new Step(Kind.DIAGONAL, a, a.add(1, 0, 1)));
        out.add(new Step(Kind.DIAGONAL, a, a.add(1, 0, -1)));
        out.add(new Step(Kind.DIAGONAL, a, a.add(-1, 0, 1)));
        out.add(new Step(Kind.DIAGONAL, a, a.add(-1, 0, -1)));
        out.add(new Step(Kind.DIG_DOWN, a, a.down()));
        out.add(new Step(Kind.PILLAR, a, a.up()));
        out.add(new Step(Kind.SWIM_UP, a, a.up()));
        return out;
    }

    private double cost(Step s) {
        BlockPos a = s.from(), b = s.to();
        switch (s.kind()) {
            case TRAVERSE -> {
                double c = clear(b) + clear(b.up());
                if (c >= COST_INF) return COST_INF;
                double move = water(b) ? WATER : (c == 0 ? SPRINT : WALK);
                if (!standable(b)) { // bridge: place the floor block
                    if (!canPlace || !replaceable(b.down()) || !solid(a.down())) return COST_INF;
                    return c + move + PLACE + 2;
                }
                return c + move;
            }
            case DIAGONAL -> {
                if (!passable(b) || !passable(b.up()) || !standable(b)) return COST_INF;
                BlockPos s1 = new BlockPos(b.getX(), a.getY(), a.getZ()), s2 = new BlockPos(a.getX(), a.getY(), b.getZ());
                boolean open1 = passable(s1) && passable(s1.up()), open2 = passable(s2) && passable(s2.up());
                if (!open1 || !open2) return COST_INF; // no corner cutting
                return (water(b) ? WATER : SPRINT) * DIAG;
            }
            case ASCEND -> {
                if (!solid(b.down())) return COST_INF; // the block we step onto
                double c = clear(a.up(2)) + clear(b) + clear(b.up());
                return c >= COST_INF ? COST_INF : c + WALK + JUMP;
            }
            case DESCEND -> {
                int k = a.getY() - b.getY();
                BlockPos col = new BlockPos(b.getX(), a.getY(), b.getZ());
                double c = clear(col) + clear(col.up());
                if (c >= COST_INF) return COST_INF;
                for (int i = 1; i < k; i++) if (!passable(col.down(i))) return COST_INF; // free fall, no mid-air digging
                double last = clear(b);
                if (last >= COST_INF || (k > 1 && last > 0) || !standable(b)) return COST_INF;
                if (k > 3 && !water(b) && k > maxFall.getValue()) return COST_INF;
                return c + last + WALK + k * FALL_PER_BLOCK + (k > 3 && !water(b) ? 20 : 0);
            }
            case DIG_DOWN -> {
                if (passable(b) || !solid(b.down())) return COST_INF;
                double c = breakTicks(b);
                return c >= COST_INF ? COST_INF : c + 2;
            }
            case PILLAR -> {
                if (!canPlace || water(a) || !solid(a.down())) return COST_INF;
                double c = clear(a.up(2));
                return c >= COST_INF ? COST_INF : c + JUMP + PLACE + 2;
            }
            case SWIM_UP -> {
                if (!allowWater.getValue() || !water(a) || !passable(b) || !passable(b.up())) return COST_INF;
                return WATER;
            }
        }
        return COST_INF;
    }

    // ---------------------------------------------------------------- planning

    private void plan() {
        if (goal == null || mc.player == null) return;
        path = search(mc.player.getBlockPos(), goal);
        index = 0;
        stuckTicks = 0;
        if (path.isEmpty() && !goal.isIn(mc.player.getBlockPos())) {
            if (mining) {
                if (mineTarget != null) blacklist.add(mineTarget);
                mineTarget = null;
                goal = null;
                pickup = null;
            } else {
                say(LT.L50 + goal.describe());
                cancel();
            }
        }
    }

    private BlockPos findOre() {
        BlockPos me = mc.player.getBlockPos();
        int r = radius.getValue();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int cr = (r >> 4) + 1;
        for (int cx = (me.getX() >> 4) - cr; cx <= (me.getX() >> 4) + cr; cx++) {
            for (int cz = (me.getZ() >> 4) - cr; cz <= (me.getZ() >> 4) + cr; cz++) {
                WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                ChunkSection[] secs = ch.getSectionArray();
                int bottom = ch.getBottomSectionCoord();
                for (int i = 0; i < secs.length; i++) {
                    ChunkSection s = secs[i];
                    if (s == null || s.isEmpty() || !s.hasAny(st -> mineSet.contains(st.getBlock()))) continue;
                    int by = (bottom + i) << 4;
                    if (Math.abs(by + 8 - me.getY()) > r + 8) continue;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (!mineSet.contains(s.getBlockState(x, y, z).getBlock())) continue;
                        BlockPos p = new BlockPos((cx << 4) + x, by + y, (cz << 4) + z);
                        if (blacklist.contains(p)) continue;
                        // prefer ores near our Y (vertical travel is expensive)
                        double d = p.getSquaredDistance(me) + Math.pow(p.getY() - me.getY(), 2) * 2;
                        if (d < bestD && p.getSquaredDistance(me) <= (double) r * r) {
                            bestD = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- tick

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        if (paused || mc.currentScreen != null) {
            releaseKeys();
            return;
        }
        // never steal your aim/clicks while you're fighting: if a player is close, stand down
        if (pauseInCombat.getValue() && playerNearby()) {
            if (goal != null || mining || breaking != null) cancel();
            releaseKeys();
            return;
        }
        Set<Identifier> sel = Set.copyOf(blocks.getSelectedIds());
        if (!sel.equals(lastSel)) {
            lastSel = sel;
            Set<Block> s = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Identifier id : sel) Registries.BLOCK.getOptionalValue(id).ifPresent(s::add);
            mineSet = s;
        }

        if (mining) {
            if (stopWhenFull.getValue() && full()) {
                say(LT.L51);
                cancel();
                return;
            }
            if (tickMining()) return;
        }
        if (goal == null) return;

        BlockPos feet = mc.player.getBlockPos();
        if (goal.isIn(feet) && (mc.player.isOnGround() || mc.player.isTouchingWater())) {
            releaseKeys();
            if (!mining) {
                say(LT.L52 + goal.describe());
                cancel();
            }
            return;
        }
        if (index >= path.size()) {
            if (++replans > 300) {
                say(LT.L53);
                cancel();
                return;
            }
            plan();
            if (path.isEmpty()) return;
        }
        executeStep();
    }

    /** Mining brain. True = this tick is fully handled. */
    private boolean tickMining() {
        if (mineSet.isEmpty()) return true;

        if (pickup != null) { // walking to the drop
            if (!pickup.isAlive() || System.currentTimeMillis() > pickupUntil) {
                pickup = null;
                goal = null;
                path = List.of();
                return true;
            }
            return false;
        }

        if (mineTarget != null && !mineSet.contains(mc.world.getBlockState(mineTarget).getBlock())) { // mined
            BlockPos done = mineTarget;
            mineTarget = null;
            goal = null;
            path = List.of();
            breaking = null;
            if (pickupDrops.getValue()) armPickup(done);
            return true;
        }

        if (mineTarget == null) {
            if (--oreScanCooldown > 0) return true;
            oreScanCooldown = 10;
            mineTarget = findOre();
            if (mineTarget == null) {
                releaseKeys();
                return true;
            }
            goal = new GoalMine(mineTarget);
            replans = 0;
            plan();
            return true;
        }

        if (goal != null && goal.isIn(mc.player.getBlockPos()) && (mc.player.isOnGround() || mc.player.isTouchingWater())) {
            releaseKeys();
            mine(mineTarget);
            return true;
        }
        return false;
    }

    private void armPickup(BlockPos at) {
        ItemEntity best = null;
        double bd = 49;
        for (Entity e : mc.world.getOtherEntities(mc.player, new Box(at).expand(6))) {
            if (e instanceof ItemEntity it && it.squaredDistanceTo(Vec3d.ofCenter(at)) < bd) {
                best = it;
                bd = it.squaredDistanceTo(Vec3d.ofCenter(at));
            }
        }
        if (best == null) return;
        pickup = best;
        pickupUntil = System.currentTimeMillis() + 5000;
        goal = new GoalBlock(best.getBlockPos());
        replans = 0;
        plan();
    }

    // ---------------------------------------------------------------- execution

    private void executeStep() {
        Step s = path.get(index);

        if (arrived(s)) {
            index++;
            stuckTicks = 0;
            if (index >= path.size()) releaseKeys();
            return;
        }
        // live validation: a block got placed, lava flowed, etc.
        if (cost(s) >= COST_INF && !inProgress(s)) {
            plan();
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        if (lastPos != null && pos.squaredDistanceTo(lastPos) < 1e-4 && breaking == null) {
            if (++stuckTicks > 40) {
                stuckTicks = 0;
                plan();
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = pos;

        // 1) break what's in the way
        for (BlockPos o : obstacles(s)) {
            if (!passable(o)) {
                if (breakTicks(o) >= COST_INF) {
                    plan();
                    return;
                }
                releaseKeys();
                mine(o);
                return;
            }
        }
        if (breaking != null) {
            mc.interactionManager.cancelBlockBreaking();
            breaking = null;
        }

        // 2) place what's missing
        if (s.kind() == Kind.PILLAR) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(true);
            lookAt(Vec3d.ofCenter(s.from().down()).add(0, 0.5, 0), 90);
            if (mc.player.getY() > s.from().getY() + 0.9 && replaceable(s.from())) placeAgainst(s.from().down(), Direction.UP);
            return;
        }
        if (s.kind() == Kind.TRAVERSE && !standable(s.to()) && replaceable(s.to().down())) {
            bridge(s);
            return;
        }
        mc.options.sneakKey.setPressed(false);
        mc.options.backKey.setPressed(false);

        // 3) move
        Vec3d target = Vec3d.ofBottomCenter(s.to());
        double dx = target.x - mc.player.getX(), dz = target.z - mc.player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 0.05) lookYaw((float) Math.toDegrees(Math.atan2(-dx, dz)), 35);
        if (s.kind() == Kind.DIG_DOWN || s.kind() == Kind.SWIM_UP) {
            mc.options.forwardKey.setPressed(horiz > 0.25);
            mc.options.jumpKey.setPressed(s.kind() == Kind.SWIM_UP);
            return;
        }
        mc.player.setPitch(MathHelper.lerp(0.25f, mc.player.getPitch(), 12f));
        mc.options.forwardKey.setPressed(true);
        boolean sprint = allowSprint.getValue() && (s.kind() == Kind.TRAVERSE || s.kind() == Kind.DIAGONAL)
                && !mc.player.isTouchingWater() && mc.player.getHungerManager().getFoodLevel() > 6 && nextStraight();
        mc.options.sprintKey.setPressed(sprint);
        if (sprint && horiz > 0.6) mc.player.setSprinting(true);
        boolean jump = (s.kind() == Kind.ASCEND && mc.player.isOnGround() && horiz < 1.3)
                || (mc.player.isTouchingWater() && s.to().getY() >= s.from().getY());
        mc.options.jumpKey.setPressed(jump);
    }

    /** The step's start conditions no longer hold only because we're midway through it. */
    private boolean inProgress(Step s) {
        BlockPos feet = mc.player.getBlockPos();
        return !feet.equals(s.from());
    }

    private boolean nextStraight() {
        for (int i = index; i < Math.min(path.size(), index + 3); i++) {
            Step s = path.get(i);
            if (s.kind() != Kind.TRAVERSE && s.kind() != Kind.DIAGONAL) return false;
            for (BlockPos o : obstacles(s)) if (!passable(o)) return false;
            if (!standable(s.to())) return false;
        }
        return true;
    }

    private boolean arrived(Step s) {
        BlockPos to = s.to();
        double dx = mc.player.getX() - (to.getX() + 0.5), dz = mc.player.getZ() - (to.getZ() + 0.5);
        double tol = s.kind() == Kind.TRAVERSE || s.kind() == Kind.DIAGONAL ? 0.2 : 0.1;
        boolean xz = dx * dx + dz * dz < tol || (s.kind() == Kind.PILLAR || s.kind() == Kind.DIG_DOWN || s.kind() == Kind.SWIM_UP);
        boolean y = Math.abs(mc.player.getY() - to.getY()) < 0.5;
        return xz && y && (mc.player.isOnGround() || mc.player.isTouchingWater());
    }

    private List<BlockPos> obstacles(Step s) {
        BlockPos a = s.from(), b = s.to();
        List<BlockPos> out = new ArrayList<>(4);
        switch (s.kind()) {
            case TRAVERSE -> { out.add(b.up()); out.add(b); }
            case ASCEND -> { out.add(a.up(2)); out.add(b.up()); out.add(b); }
            case DESCEND -> {
                BlockPos col = new BlockPos(b.getX(), a.getY(), b.getZ());
                out.add(col.up());
                out.add(col);
                if (a.getY() - b.getY() == 1) out.add(b);
            }
            case DIG_DOWN -> out.add(b);
            case PILLAR -> out.add(a.up(2));
            default -> {
            }
        }
        return out;
    }

    /** Sneak-bridge: back up to the edge facing away from the gap, place on the side of our floor. */
    private void bridge(Step s) {
        Direction dir = Direction.fromVector(s.to().getX() - s.from().getX(), 0, s.to().getZ() - s.from().getZ(), null);
        if (dir == null) {
            plan();
            return;
        }
        mc.options.sneakKey.setPressed(true);
        mc.options.sprintKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.player.setSprinting(false);
        // distance still to go toward the edge (in the bridge direction)
        double toEdge = (s.from().getX() + 0.5 + dir.getOffsetX() * 0.3 - mc.player.getX()) * dir.getOffsetX()
                + (s.from().getZ() + 0.5 + dir.getOffsetZ() * 0.3 - mc.player.getZ()) * dir.getOffsetZ();
        mc.options.backKey.setPressed(toEdge > 0.05);
        // look back and down at the side face of the block we're standing on
        BlockPos against = s.from().down();
        Vec3d face = Vec3d.ofCenter(against).add(dir.getOffsetX() * 0.5, 0, dir.getOffsetZ() * 0.5);
        boolean aimed = lookAt(face, 45);
        if (aimed && toEdge <= 0.15) placeAgainst(against, dir);
    }

    /** Right-click {@code against}'s {@code side} face with a throwaway block. */
    private void placeAgainst(BlockPos against, Direction side) {
        if (!selectThrowaway()) {
            say(LT.L54);
            canPlace = false;
            plan();
            return;
        }
        Vec3d hit = Vec3d.ofCenter(against).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, side, against, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean selectThrowaway() {
        var inv = mc.player.getInventory();
        if (THROWAWAY.contains(inv.getSelectedStack().getItem())) return true;
        for (int i = 0; i < 9; i++) {
            if (THROWAWAY.contains(inv.getStack(i).getItem()) && inv.getStack(i).getItem() instanceof BlockItem) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (THROWAWAY.contains(inv.getStack(i).getItem())) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, inv.getSelectedSlot(),
                        net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- mining

    /** First block between your eyes and p, or p itself if any point of it is in sight. */
    private BlockPos lineOfSight(BlockPos p) {
        Vec3d eye = mc.player.getEyePos();
        BlockPos first = null;
        List<Vec3d> pts = new ArrayList<>(7);
        pts.add(Vec3d.ofCenter(p));
        for (Direction d : Direction.values()) pts.add(Vec3d.ofCenter(p).add(d.getOffsetX() * 0.49, d.getOffsetY() * 0.49, d.getOffsetZ() * 0.49));
        for (Vec3d pt : pts) {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, pt, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(p)) {
                aimPoint = pt;
                return p;
            }
            if (first == null) first = hit.getBlockPos();
        }
        aimPoint = null;
        return first;
    }

    /** Mines p - never through a wall: whatever blocks the view is mined first. */
    private void mine(BlockPos p) {
        BlockPos vis = lineOfSight(p);
        if (vis != null && !vis.equals(p)) {
            if (breakTicks(vis) >= COST_INF) {
                if (p.equals(mineTarget)) {
                    blacklist.add(mineTarget);
                    mineTarget = null;
                    goal = null;
                    path = List.of();
                } else {
                    plan();
                }
                return;
            }
            p = vis;
            lineOfSight(p);
        }
        Vec3d c = aimPoint != null ? aimPoint : Vec3d.ofCenter(p);
        if (!lookAt(c, 50)) return; // still turning
        bestTool(mc.world.getBlockState(p));
        Vec3d eye = mc.player.getEyePos();
        Direction side = Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        if (!p.equals(breaking)) {
            if (breaking != null) mc.interactionManager.cancelBlockBreaking();
            breaking = p;
        }
        if (mc.interactionManager.updateBlockBreakingProgress(p, side)) mc.player.swingHand(Hand.MAIN_HAND);
        if (mc.world.getBlockState(p).isAir()) breaking = null;
    }

    private void bestTool(BlockState state) {
        var inv = mc.player.getInventory();
        int best = inv.getSelectedSlot();
        float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isDamageable() && s.getMaxDamage() - s.getDamage() < 10) continue;
            float sp = s.getMiningSpeedMultiplier(state);
            if (sp > bestSpeed + 0.01f) {
                bestSpeed = sp;
                best = i;
            }
        }
        if (best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
    }

    // ---------------------------------------------------------------- smoothed rotations

    private void lookYaw(float yaw, float maxStep) {
        float d = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(d, -maxStep, maxStep));
    }

    /** Turns toward the point; true once aimed. */
    private boolean lookAt(Vec3d pt, float maxStep) {
        Vec3d eye = mc.player.getEyePos();
        double dx = pt.x - eye.x, dy = pt.y - eye.y, dz = pt.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float dyaw = MathHelper.wrapDegrees(yaw - mc.player.getYaw()), dp = pitch - mc.player.getPitch();
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(dyaw, -maxStep, maxStep));
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(dp, -maxStep, maxStep), -90, 90));
        return Math.abs(dyaw) <= maxStep && Math.abs(dp) <= maxStep;
    }

    private boolean playerNearby() {
        if (mc.world == null || mc.player == null) return false;
        for (net.minecraft.entity.player.PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (com.utilitymod.echo.utils.combat.TargetUtils.isFriend(p)) continue;
            if (p.squaredDistanceTo(mc.player) <= 8.0 * 8.0) return true;
        }
        return false;
    }

    private boolean full() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isEmpty()) return false;
        return true;
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        if (!showPath.getValue() || mc.world == null) return;
        var d = event.getDraw3D().getMinecraftTarget();
        for (int i = index; i < path.size(); i++) {
            Step s = path.get(i);
            int col = switch (s.kind()) {
                case PILLAR -> 0xFFFFB347;
                case DIG_DOWN -> 0xFFFF6060;
                default -> s.kind() == Kind.TRAVERSE && !standable(s.to()) ? 0xFFFFB347 : 0xFF60D0FF;
            };
            d.line(s.from().getX() + 0.5, s.from().getY() + 0.1, s.from().getZ() + 0.5,
                    s.to().getX() + 0.5, s.to().getY() + 0.1, s.to().getZ() + 0.5, col);
        }
        if (mineTarget != null) event.getDraw3D().getCustomTarget().box(mineTarget.getX(), mineTarget.getY(), mineTarget.getZ(), 1, 1, 1, 0x5060FF90);
        if (goal instanceof GoalBlock gb) d.box(gb.pos().getX(), gb.pos().getY(), gb.pos().getZ(), 1, 2, 1, 0xFF60FF90);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa184,0xf7a4,0xe076,0x53c7,0x3f39,0x7a11,0xec19,0x9321,0x79da,0x1781,0x8d72}, new char[]{0xa1c9,0xe01f,0x79b4,0x7a74,0x3f19,0xf7e6,0x934d,0x8d1d,0x53a4,0xec72,0x17f2}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x025e,0x15d2,0x7b17,0x57ce,0x413e,0x80d9,0xf71d,0xb0cd,0x8335,0x698a,0x8d6b,0xd33f,0x0f44,0x0050,0xba77,0xa12f,0x708a,0xcb2c,0x70bf,0xfa50,0xd0e7}, new char[]{0xb0a0,0x8d02,0x57a0,0x8350,0xfa33,0xd095,0x0031,0x0f22,0x15a6,0xcb16,0x023a,0x69e3,0x80b8,0x7b7a,0xf772,0xba19,0x70db,0x70d5,0x4151,0xa15d,0xd35a}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3588,0x3ff6,0x3462,0x578a,0xbc77,0xb160,0xf848,0x9f87,0x1ca3,0x6b78,0x086d,0x693d,0x13a9,0x190c,0xe57b,0x06a0,0x5445,0x006f,0x9877,0xd23f,0xfe67,0xcad1,0x91c5,0x62e8,0xf1f9,0x0cf9,0x5a2b,0xc96f,0x844a,0xdf39,0x93a2}, new char[]{0xbc1a,0xe512,0xc901,0xb105,0x06c3,0x93d0,0xf198,0x57ec,0x629c,0xf872,0x0c9d,0x9fe2,0x91a0,0xcaa1,0x5a58,0x6b14,0x695c,0x3416,0x842f,0xd260,0xdf5d,0x1cca,0x13c8,0x981a,0x3f99,0x0001,0x1968,0x0832,0x35e7,0xfe15,0x5420}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x344d,0xf1bd,0xd454,0x7564,0x19ba,0xd78e,0x5d1a,0x14ca,0xb4d5,0x6e88,0xa4a8,0xa853,0xf1d4,0xf683,0xd814,0xdb39,0xa2b3,0xa053,0xe470,0x859d,0x5d96,0x2cac,0xa249,0xc827}, new char[]{0x5dfb,0x3424,0x19d4,0xa4cd,0x6eeb,0xa2c1,0xf1dc,0xf1b2,0xdb4d,0x14f0,0xd7ef,0x2cc2,0x5d79,0xa83a,0xf6e6,0xa03d,0xd420,0x753b,0xb4b1,0xa22c,0xc845,0x85ef,0xe419,0xd867}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ccf,0xdc15,0xeb19,0x1438,0xdd39,0xb4d5,0x250c,0xee4c,0xe317,0x9800,0x6577,0xf16c,0x3f69,0x2e85,0xb48b,0xf84a,0xdd43,0x8169}, new char[]{0xb4e6,0x8100,0x6519,0xf109,0x3f0a,0xe365,0xeb78,0xdc73,0x2ef1,0x2536,0x1451,0xdd4b,0xdd2c,0xb4bb,0xee13,0xf825,0x9872,0x7caa}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8609,0xd228,0x2206,0xfb5f,0xa601,0xa321,0xd2a9,0x6ef3,0xf787,0x3553,0xf61e,0x2517,0x7c4f,0x3e21,0xe3d1,0x39e3,0x2ce4,0xc7bd,0x6a02,0x47bc,0xaa10,0xa335,0xb25e,0x0787,0x37fc,0xc5b5,0xbaef,0x2a37}, new char[]{0x07ea,0x3795,0xa66f,0x2c81,0x3e42,0x6a70,0xa340,0xe3b7,0x6e87,0xfb65,0x47d8,0x3536,0xf67b,0x3993,0x2564,0xc7d1,0xba8e,0xc5c1,0x866c,0x2a68,0xd241,0xaa62,0xf7e8,0xd2c7,0x7c10,0xb231,0x2274,0xa350}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1205,0x0cf3,0x84bc,0xfdf6,0xfd93,0x187c,0xdf46,0xec56,0x97f7,0xdd97,0x5011,0x522c,0xdccc,0xcc9b,0x705e,0xbe0f,0x0ddd,0x4224}, new char[]{0x1811,0x5245,0x424a,0x0db8,0xbe6c,0xcce9,0x9796,0xddf1,0xdf32,0x7064,0xfd91,0x507e,0x0c9f,0xec32,0xfdcc,0xdca3,0x84ce,0x1260}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8974,0x3ede,0x8ec8,0x0446,0x3cc3,0x9e42,0xa642,0x9e05,0x01a8,0xfb48,0x3055,0x7e7b,0xbcf6,0x7afd,0x5496,0x8499,0x4bba,0xa2d0,0x0801,0x96f8,0x202c,0x5e4f,0xfd6e,0xe956,0x8953,0x8656,0x85a3,0xadbd}, new char[]{0x84f4,0xa62b,0x7a93,0x4bdf,0x8635,0xa2a2,0x3ca2,0xe930,0x8ebc,0x8969,0x01cc,0x3ebb,0x54f3,0xbc86,0x3026,0xadd1,0x0427,0x2058,0xfd0b,0x96a7,0xfb2f,0x5e20,0x9e2e,0x0865,0x7e24,0x85cc,0x9e77,0x8911}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc40f,0xf1cb,0x8319,0xc5b6,0x2c8b,0x21ad,0xd0c7,0x2cae,0xf27a,0xf945,0x44fd,0x2e77,0x447e,0x3adc,0x3654,0x8597,0x42ce}, new char[]{0x21e0,0xd0ae,0xc5d8,0x837c,0x2c8e,0x4299,0xf1a3,0xf21f,0x4493,0x2cab,0x85d2,0x3ab2,0x3635,0x441c,0xc463,0xf920,0x2e13}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8056,0x2b9d,0x6f62,0x78df,0x33e4,0x78db,0x795f,0xcba4,0xc812,0xc2c0,0x6fda,0xfbdd,0xcef6,0x466a,0xe2c2}, new char[]{0x463a,0xe2a3,0x78aa,0x6fa9,0xfbb8,0xcb84,0xc85b,0x7931,0x6f42,0xceb5,0x338b,0x78b6,0x2bff,0x8037,0xc2b4}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a07,0x6ba8,0xb4bc,0xef1b,0xdf6d,0xdfaa,0xc2c4,0x0c4e,0x1877,0xc0b3,0x40e3,0x48e1,0xc517}, new char[]{0xef48,0xc572,0x6bc9,0xdf1f,0xc0d0,0x0c26,0xc2e4,0x1825,0xb4dd,0x4885,0xdfc3,0x8a72,0x4090}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe060,0xc006,0x6176,0xf3f5,0xec3e,0x2f93,0x082c,0x399f,0x222e,0x013b,0x6f98}, new char[]{0x2fd2,0x611a,0xf399,0x2241,0xec49,0x6fb8,0xc044,0x085e,0xe005,0x015a,0x39f4}).toString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x14ab,0xdb0c,0xcd33,0x7df5,0x4d42,0x6af0,0xbc8d,0x1862,0xafd7,0xa627,0x7505}, new char[]{0x7544,0x14c7,0x6a9c,0xafb8,0xbcfa,0x1842,0xdb5c,0x4d2e,0xcd52,0x7d96,0xa642}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc376,0xb3a2,0x0934,0x852e,0x8e1c,0xd72f,0x232c,0x750f,0xb9d5,0x319a,0x43d8,0xf9df}, new char[]{0xc337,0x2340,0x31f6,0x8e73,0xb3d5,0x0914,0xd77c,0x43a8,0xb9a7,0x8547,0xf9b1,0x757b}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7820,0xa0f0,0xd642,0xcb35,0x8ee6,0x20de,0x31ff,0xb91b,0xda1c,0x6287,0xcb85}, new char[]{0x7861,0xa09c,0xb977,0xcb5a,0xcbf2,0x31df,0x62d0,0x8e87,0xda68,0x20bb,0xd630}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d35,0x9c9f,0x7706,0x67ab,0x9609,0x06b3,0x2e8c,0x4789}, new char[]{0x774b,0x67ca,0x06cb,0x2eac,0x1d73,0x47e8,0x9665,0x9cf3}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x680a,0xe524,0x5380,0x8603,0x19c3,0xe662,0x0b28,0x9f55,0x8fba,0x63b1,0x5852,0xb580,0x5847}, new char[]{0x0b78,0xe54d,0xe601,0x9f3e,0x682a,0x8fef,0x8673,0xb5a0,0x5803,0x5820,0x63de,0x19b3,0x53f3}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf73a,0x7fe9,0xb106,0xa2df,0x63d0,0x4f6b,0xd81b,0x3f2f,0x791e,0x7b19,0xdcb0,0xc376,0x2203,0x0af9}, new char[]{0xdce3,0xc302,0xb169,0x2273,0xd83b,0x6387,0x0a91,0xa2ba,0x7970,0x3f0f,0x7faf,0x4f1e,0xf756,0x7b75}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb010,0x4219,0x0d65,0xa09c,0x0be2,0x155f,0xf782,0x425a,0xcbd7,0xce8c,0xbaa1,0x00bb,0xe0eb,0x9b9b,0xe1cd}, new char[]{0x0098,0x157f,0x0d26,0xbac9,0x423b,0x426d,0xe1ed,0x0ba1,0xa0f3,0xf7ef,0xe086,0x9bfa,0xb07e,0xcee8,0xcba4}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ed8,0xa55a,0xde30,0x5d49,0xb1cb,0x084b,0x169a,0xacff,0x3f34}, new char[]{0xacac,0x16f2,0x8eb7,0xb1bc,0x3f14,0xde60,0x5d28,0x083f,0xa532}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x900a,0xdf12}, new char[]{0xdf3e,0x902a,0x9d8f,0xaf20}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf686,0xc77c}, new char[]{0xf6ff,0xc741,0x454b,0xdca7}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xccc8,0x849b,0x2190,0x9adc,0xc906,0xd76d,0x32e9,0x0e75,0x46ae,0xe139,0xce90,0xa7f0,0x3105,0x967d,0xc128,0xbe2c,0x9318,0x5333,0xe74b,0x0cec,0x3c8d,0x0f99,0xfe26,0x9e97,0x6f55,0x3bf0,0xc68a,0xf1e5,0xb012,0x27bf,0xcfb6,0x6256,0x9cad,0x3429,0x777b,0x62ab,0xbb31,0x50b3,0x2eb3,0x0e05,0x41ce,0xbb92,0x06ed,0xef11,0x95bb,0x85b5,0xa0a6,0x8fb9,0xe273,0x8b0d,0x2f40,0x51b7,0xaeae,0x6531,0x926d,0xa7a4,0xc2c4,0x2477,0xc8d0,0xd07b,0x86fd,0x7d31,0xcc61,0xe9ac,0x124a,0x5b3b,0x6d0a,0xb2b9,0x3f1d,0x4aea,0x1ad3,0xaf69,0xb47c,0x4e2f,0x0ae9,0x22a7,0xb5e5,0x1f7a,0x06b4,0x94f3,0x9164,0xf662,0x132a,0x180b,0x0302,0x8550,0x7964,0x68d1,0x90c9,0x8a8a,0xf6b1,0xd42b,0x258e,0x530e,0x2a0f,0x607e}, new char[]{0xcc23,0xcef1,0x7916,0x9204,0x41ba,0xe9c3,0x1f14,0x46cb,0xa789,0xbbe1,0xbb45,0xc2bd,0x6f39,0x85d0,0xe103,0x965d,0xa085,0x51da,0x8fd0,0x949d,0x5b5e,0x90e9,0x3ca2,0xd74d,0x8573,0xf605,0xe724,0x9110,0x25e1,0x6287,0xfe06,0x5379,0x2a6e,0x7717,0xc96d,0x9cde,0x245b,0x32c9,0x27db,0x316c,0x6231,0x95c8,0x8b21,0xf691,0xc6e8,0xd009,0x50da,0xef75,0x06d3,0x601b,0x9aaf,0x1af3,0x2ed2,0xb412,0xc14c,0x3bd0,0x069d,0x7d58,0x0a85,0x22cb,0x9ef6,0x345b,0x0c9f,0x84bb,0x8689,0xcca0,0xc8b5,0x21e2,0x0ffc,0xf1c5,0x032a,0x2f21,0x4e43,0x936b,0xb58a,0xae8e,0x4a87,0x6558,0xd445,0x6d6f,0x3f6e,0xe253,0x123a,0xa799,0x0e16,0xaf02,0x5356,0x134e,0x182b,0xb2db,0xbe40,0x8ae5,0x0e66,0xcfdd,0xb061,0x68f8}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc47e,0xb5bc,0x531f,0x5f21,0x1a77,0x3cea}, new char[]{0x536f,0x1a16,0xb5c9,0x3c99,0xc41b,0x5f45}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad94,0xf6bb,0x537d,0x4be9,0x24b1,0xbb53,0x7d10,0xbcfc,0x2c63}, new char[]{0x24c2,0xbc99,0x2c02,0x7d62,0xbb30,0x4b81,0xadfd,0x5313,0xf6dc}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5375,0x23c5,0x4637,0x5e46}, new char[]{0x23ac,0x5311,0x465b,0x5e23}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xff8a,0x637e,0x948a,0x1357,0x8c2d}, new char[]{0x94e7,0x6317,0x1339,0x8c48,0xffaa}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0830,0x7069,0x4ad6,0xdf42,0xda05}, new char[]{0x700e,0x085f,0xdf36,0xda6a,0x4af6}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b5e,0xbdae,0x0819,0xe452,0x512c,0x3fb3,0x6d88,0x3d5f,0xcd66,0xccc6,0x45af,0xd941,0x8e91,0xc4bc,0xff82,0xe3eb,0x5057}, new char[]{0x8e36,0xc4de,0xe3b0,0xbdfe,0xffe3,0xcd12,0x6de0,0x514a,0x0870,0xe43c,0x3d3b,0x5032,0xd933,0x9b03,0x4508,0x3fc1,0xcce6}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9d00}, new char[]{0x9d23,0x9b61,0x7dbb,0xe3df}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9472,0xc243}, new char[]{0x94d5,0xc220,0xc642,0x5b06}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x066a,0xab13,0x3447}, new char[]{0x0636,0x3434,0xab38,0x5bdd}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd238,0xa8be,0x5914,0xa227,0xe8ee,0xf5ff}, new char[]{0xa244,0xa8df,0xe880,0x5977,0xd25d,0xf593}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd975,0xf375,0x27b6,0xc2bd,0x4574,0x825d,0xc548,0x8ce4}, new char[]{0x820e,0xc2c9,0xf31a,0x8c94,0x27c6,0x4511,0xd911,0xc566}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x266e,0xd8ca,0xc1e8,0x86e5,0x0049,0x09fd,0xcff1}, new char[]{0xcfa1,0xd8ab,0x261b,0x098e,0xc18d,0x8681,0x0067}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbbce,0x4d99,0x3cf8,0x7d5d,0x13fd,0xb508,0x9409,0x45f1}, new char[]{0xb55a,0x3c9d,0x4582,0x7d28,0x4df4,0x1398,0xbbaa,0x9427}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x565d,0x6d05,0xefce,0x4625,0x8b1b,0x7aea,0x96bb,0x257a,0xd231,0xc873,0x7b1e,0x1aed,0xd558,0xe457,0x5a5a,0xac9a,0x73ed,0x527d,0x15b1,0x32ff,0xfd3c,0xfd3d,0x3303,0xaeda,0x893a,0x4168,0xa99a,0xfe71}, new char[]{0xc826,0xd242,0xe436,0x5a3d,0x7a8f,0x73d7,0x567d,0x1ace,0xfd5a,0xfe1e,0x6d71,0x15de,0xaefa,0xefb6,0x525d,0xace3,0xa9ba,0x8b61,0x7b3e,0x96c7,0x891a,0xfd44,0x3323,0x2500,0x32df,0xd524,0x4605,0x4111}).toString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1920,0x0b1a,0xe272,0x14c2,0xfce1,0xd36c,0x705d,0xbbb3,0x06f7}, new char[]{0xe235,0xfc8e,0x7034,0x194e,0x0b7d,0x14e2,0xd318,0xbbdc,0x06d7}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfce4,0x1d5e,0xebad,0x8c37,0xb0d0,0x39fa,0xb7aa,0x4023,0x4460,0x986d,0xb6e7,0xf7c4,0xd43d,0x7a4f,0xda28,0xe5cf,0x535f,0x2286,0x6be0,0x4f87,0x5f0d,0xc154,0xab28,0x89d1,0xa377,0x16ef,0x8222,0x0141,0xbc3d,0x2e93,0x955f}, new char[]{0xb085,0xfc97,0xd45c,0xc133,0x533a,0x16d5,0x1d7e,0x22a5,0x89bc,0xa31e,0x012f,0x8c52,0xf7e4,0x445c,0xb685,0x404f,0x7a20,0x3999,0xab43,0xda16,0x957f,0x2ec8,0x6b82,0xe5a3,0x5f62,0xb7c9,0x8249,0x4fa9,0x9843,0xbc13,0xebf0}).toString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa0b7}, new char[]{0xa08d,0x3a4f,0xf279,0x0140}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb35,0x9a29,0x4666,0xb7dd,0x04d5,0x15b0,0x6015,0x65ae,0xe786,0x5dd1}, new char[]{0x9a44,0x65c7,0x15de,0x6070,0xeb56,0x4614,0xe7e7,0x5db7,0xb7a9,0x04ef}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2077,0x67f3,0x68e1,0x0802,0x71ee,0x65b1,0x2cac,0xa70b,0xde8a,0x1ccf,0x31a9,0x95ff,0xdd97,0xd5a4}, new char[]{0x65e4,0xa765,0xddfc,0x9591,0x1ca0,0x2000,0x7180,0x67d3,0xdee8,0x31c5,0x688e,0xd5c7,0x2cc7,0x0822}).toString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9010,0xa76a,0x499f,0x8710,0xd5f8,0xe36d,0x5025,0x814d,0x41f4}, new char[]{0x4190,0x5040,0x49fa,0xd588,0x8763,0x907c,0xa70b,0x8139,0xe308}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x611b,0xd40d,0x4c55,0x375d,0xc69b,0xddff,0x8111,0xca00,0x1811,0xdab6,0xf3b1,0xd7fa,0x9c41,0xa1de,0xa2b9,0x0e35,0xf1d2,0x3ef8,0x04a0,0xbb54}, new char[]{0x3730,0xdadf,0xc6f5,0xd79f,0xf3d2,0x04d2,0x617a,0x0e53,0xa1aa,0xddc5,0x1875,0x8174,0x3e9d,0xf1a2,0x4c26,0x9c2d,0xca61,0xa2cd,0xd468,0xbb0b}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0e1f,0xd96d,0xa8ba,0x99a1,0x6222,0x782f,0x1ffe}, new char[]{0x0e52,0xd904,0x1f90,0x624b,0xa8d4,0x7848,0x9981}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4146,0xeead,0x4532,0x1eca,0x969f,0x5e2c,0x29db,0xf368,0x1286,0x9827,0x5ebb,0xce69,0x29ca,0xaff8,0x8df3}, new char[]{0x12a6,0xce0b,0x8d9f,0x29a5,0x5ed8,0xeec6,0x1eea,0x96eb,0x454b,0x4136,0x5e49,0xafd0,0x9854,0x29f2,0xf346}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc76f,0x7779,0xd3db,0x1d53,0xea02,0x241c,0xd0f5,0x64c1,0x7623,0xce75,0xe014,0x1831,0xc47b,0xc735,0xacc3,0xb9a3,0x384d,0x822d,0xd05e,0xb3c7,0xc6e2,0xd709,0x4ab2,0x9217,0x6394,0x7a0d,0xa6e8,0x28b5,0x9a66,0xb018,0x8c4c,0xda6b,0xb7a6,0xfd4b,0x926a,0xe326,0x0d82,0xc554,0x72b5,0x58c3,0x800b,0xd5e3,0x38a6,0xe95a,0x7608,0x601e,0xf437,0x1a2a,0x6830,0xa097,0x5a5b,0x0b5e,0x829b,0x6334,0x3cdb,0x1920,0x33c2,0x6864,0x6251,0x0e11,0xb4e3,0x6698}, new char[]{0x3885,0xa0fa,0xea6b,0xd767,0x9272,0xb038,0x8211,0xda09,0x3cb7,0x63fb,0x28d6,0xf45c,0x7a23,0xce5b,0x8c72,0xb786,0x6810,0xc716,0x0b39,0xa687,0xc520,0x1d3c,0xace3,0xb9db,0x6271,0xd3a2,0xd07e,0xd08f,0xe97a,0xc69e,0x386d,0xc403,0x64e1,0xd599,0x6844,0x2460,0x1900,0x5a22,0x924a,0x33e2,0x8028,0xb3b4,0x767c,0x1a45,0x7709,0x9a46,0x82bb,0x0da1,0x58b3,0x7642,0x66ed,0x4ac1,0xc70a,0x1811,0x0e31,0x6317,0xe066,0xfd2e,0x72c6,0xb496,0x6073,0xe343}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f60,0xe100,0xf3d4,0xbbd6,0x8537,0x68c9,0x03dd,0xb585,0x5bdc,0x932e,0x672a,0x1915,0x5c6e,0x01f5,0xb9d9,0x0ec5,0x3588,0xd820,0x481e,0x6a7f,0x8d34,0x6879,0x1ba5}, new char[]{0x0e90,0xbbb8,0x5bb7,0x4f0e,0xb5ea,0x6a08,0x03b3,0x8d14,0x1976,0x6816,0x0198,0x855a,0x5c0f,0x6744,0x934a,0xd800,0x4833,0xf3f4,0x35ab,0x68a1,0x1bc0,0xe16c,0xb9a9}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c9b}, new char[]{0x4ce5,0x99de,0x6b54,0x91d4}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6c4,0x88ad,0x5fbb,0x4e9e,0xc31d,0x8896,0x9ea2,0xf5bb,0xcdb9,0x86a7,0x913f,0x4ae0,0x9b37}, new char[]{0xf51c,0x9ec1,0x4ed0,0x9150,0x888d,0x88e6,0x5fda,0x4a94,0x86cf,0x9b17,0xa6b0,0xc372,0xcd99}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56c2,0x04e7,0x122c,0x5fba,0x7960,0xa6e0,0x686c,0xf90b,0x498d,0xb963,0x169a,0xd94b,0x480e,0xc671,0x4ad7,0x7636,0x79a6,0x4109,0x0001,0x261b,0xcae3,0x0ac9,0x04bc,0xac97,0x2455}, new char[]{0x7929,0x79c8,0x4aa1,0x56a7,0x7658,0x2421,0x0488,0x417b,0xacee,0xf92b,0xca85,0xb916,0xc61d,0x16f6,0x482e,0x2636,0x684c,0xa693,0x0abd,0x04d3,0x0071,0x125c,0x5fdf,0xd92f,0x49a3}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe3cd,0xa225,0x02ce,0x8edf,0xe974,0x00c0,0x1747,0x04e5,0x8fec,0x03a7,0x1ec9}, new char[]{0xe38c,0x8ead,0x03d5,0x172e,0x1ebf,0x8f89,0x02aa,0x00e0,0xe915,0xa251,0x04c5}).plainString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4c89,0x60e3,0x3200,0x9478,0x4ca5,0x8344,0xf184,0x3b76,0xebcd,0x35a1,0xb403,0x8a3e}, new char[]{0x8a99,0xb460,0x3247,0x832d,0x4cff,0x608a,0x3b18,0xebaa,0x9458,0x35d4,0x4cd5,0xf1aa}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xecd9,0x5842,0xe642,0xa2b4,0xa8bd,0x2bc5,0x837d,0xed74,0x1106,0xdb45,0x2ee0,0x39ff,0x4a9c,0x608b,0xa6e7,0xd51e,0x63b7,0xf2df,0xae2b,0xc724,0x595b,0x7169,0xd356,0xf8cc,0x936a,0x311e}, new char[]{0x31b9,0x60e8,0x5914,0x398a,0xae5f,0xa89d,0x2e8f,0x1160,0x2be5,0x8309,0xecb1,0xf8be,0x7106,0xa2c3,0xdb24,0x4aeb,0xed15,0xf2a6,0x5862,0x9308,0xc748,0xd339,0xd57d,0x63dc,0xe631,0xa6c9}).plainString();
    }
}
