package com.smartypantsltd.lemoncosmetics.ui;

import com.smartypantsltd.lemoncosmetics.Config;
import com.smartypantsltd.lemoncosmetics.Dresser;
import com.smartypantsltd.lemoncosmetics.LemonCosmetics;
import com.smartypantsltd.lemoncosmetics.catalogue.ArmourSet;
import com.smartypantsltd.lemoncosmetics.catalogue.Catalogue;
import com.smartypantsltd.lemoncosmetics.catalogue.Cosmetic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The cosmetics menu (K). Left: your looks (one per item kind, the four armour
 * slots, your head). Right: every look the server's pack offers for the selected
 * one, searchable. A purely local screen: it opens no container and sends nothing.
 */
public final class CosmeticsScreen extends Screen {

    private static final int BG = 0xF0121826;
    private static final int EDGE = 0xFF2B3A55;
    private static final int ROW = 0xFF182235;
    private static final int SEL = 0xFF253757;
    private static final int SKY = 0xFF5AB4F0;
    private static final int LEMON = 0xFFFFD23F;
    private static final int INK = 0xFFEFF4FA;
    private static final int MUTED = 0xFF8DA0B8;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};
    private static final Item[] SLOT_ITEMS = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final int CELL = 20;
    private static final int ROW_H = 22;

    private enum Kind { ITEM, ARMOUR, HEAD }

    /** One entry in the left column. */
    private record Row(Kind kind, int index, ItemStack icon, String label, String sub) {
    }

    // Survives rebuildWidgets() (a resize, or a change of selection).
    private static Kind kind = Kind.HEAD;
    private static int index = 0;
    private static boolean showAll = false;

    private String query = "";
    private int scroll = 0;
    private String notice = "";

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int lx;
    private int lw;
    private int rx;
    private int rw;
    private int gridY;
    private int gridH;

    private final List<Row> rows = new ArrayList<>();
    private final List<Object> candidates = new ArrayList<>();
    private final Map<String, ItemStack> previews = new HashMap<>();
    private EditBox search;
    private EditBox nameFilter;

    public CosmeticsScreen() {
        super(Component.literal("LemonCosmetics"));
    }

    private static Config cfg() {
        return LemonCosmetics.config();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        Config c = cfg();
        if (kind == Kind.ITEM && index >= c.items.size()) {
            kind = Kind.HEAD;
            index = 0;
        }
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lx = px + 8;
        lw = Math.min(150, pw / 3);
        rx = lx + lw + 8;
        rw = px + pw - 8 - rx;

        // Right: search + filter + clear.
        int top = py + 26;
        int bw = 58;
        boolean filterable = kind != Kind.ARMOUR;
        int sw = rw - (filterable ? bw * 2 + 8 : bw + 4);
        search = new EditBox(font, rx, top, sw, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search looks..."));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);
        int bx = rx + sw + 4;
        if (filterable) {
            addRenderableWidget(new LemonButton(bx, top, bw, 16, showAll ? "Show: all" : "Show: best", SKY, b -> {
                showAll = !showAll;
                scroll = 0;
                rebuildWidgets();
            }));
            bx += bw + 4;
        }
        addRenderableWidget(new LemonButton(bx, top, bw, 16, "No look", SKY, b -> {
            clearLook();
            rebuildWidgets();
        }));

        // Right, bottom: options for the selected entry.
        int by = py + ph - 24;
        gridY = top + 22;
        gridH = (by - 16 - gridY) / CELL * CELL;
        if (kind == Kind.ITEM) {
            Config.ItemRule r = c.items.get(index);
            int fw = rw - 64 - 70;
            nameFilter = new EditBox(font, rx + 64, by + 2, fw, 16, Component.literal("Only items named"));
            nameFilter.setHint(Component.literal("any name"));
            nameFilter.setValue(r.nameContains);
            nameFilter.setResponder(s -> {
                r.nameContains = s;
                cfg().save();
            });
            addRenderableWidget(nameFilter);
            addRenderableWidget(new LemonButton(rx + rw - 66, by + 2, 66, 16, "Remove", 0xFFE07A6A, b -> {
                cfg().items.remove(index);
                cfg().save();
                kind = Kind.HEAD;
                index = 0;
                rebuildWidgets();
            }));
        } else if (kind == Kind.ARMOUR) {
            int hw = (rw - 4) / 2;
            addRenderableWidget(new LemonButton(rx, by + 2, hw, 16, "Even when empty: " + (c.armourWhenEmpty ? "on" : "off"), SKY, b -> {
                cfg().armourWhenEmpty = !cfg().armourWhenEmpty;
                cfg().save();
                rebuildWidgets();
            }));
            addRenderableWidget(new LemonButton(rx + hw + 4, by + 2, hw, 16, "Icons too: " + (c.armourIcons ? "on" : "off"), SKY, b -> {
                cfg().armourIcons = !cfg().armourIcons;
                cfg().save();
                rebuildWidgets();
            }));
        } else {
            addRenderableWidget(new LemonButton(rx, by + 2, rw, 16, "Hides my helmet: " + (c.headHidesHelmet ? "yes" : "no"), SKY, b -> {
                cfg().headHidesHelmet = !cfg().headHidesHelmet;
                cfg().save();
                rebuildWidgets();
            }));
        }

        // Left, bottom: the switch and done.
        int hw = (lw - 4) / 2;
        addRenderableWidget(new LemonButton(lx, by + 2, hw, 16, c.enabled ? "Looks: on" : "Looks: off", LEMON, b -> {
            cfg().enabled = !cfg().enabled;
            cfg().save();
            rebuildWidgets();
        }));
        addRenderableWidget(new LemonButton(lx + hw + 4, by + 2, hw, 16, "Done", LEMON, b -> onClose()));

        buildRows();
        refilter();
        setInitialFocus(search);
    }

    private void buildRows() {
        rows.clear();
        Config c = cfg();
        Catalogue cat = Catalogue.get();
        for (int i = 0; i < c.items.size(); i++) {
            Config.ItemRule r = c.items.get(i);
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(r.target)).orElse(Items.BARRIER);
            Cosmetic look = cat.find(r.lookItem, r.lookCmd);
            ItemStack icon = look != null ? preview(look) : new ItemStack(item);
            String label = new ItemStack(item).getHoverName().getString();
            if (!r.nameContains.isBlank()) {
                label += " \"" + r.nameContains.trim() + "\"";
            }
            String sub = r.lookItem.isBlank() ? "no look yet" : look != null ? look.name() : r.lookName + " (not loaded)";
            rows.add(new Row(Kind.ITEM, i, icon, label, sub));
        }
        rows.add(new Row(null, -1, ItemStack.EMPTY, "+ Item in my hand", "add a look for it"));
        for (int s = 0; s < 4; s++) {
            Config.ArmourChoice a = c.armour.get(SLOTS[s].name());
            ArmourSet set = a == null ? null : cat.armourSet(a.asset);
            ItemStack icon = set != null && set.icons()[s] != null ? preview(set.icons()[s]) : new ItemStack(SLOT_ITEMS[s]);
            String sub = a == null ? "as worn" : set != null ? set.name() : a.name + " (not loaded)";
            rows.add(new Row(Kind.ARMOUR, s, icon, SLOT_NAMES[s], sub));
        }
        Config.HeadChoice h = c.head;
        Cosmetic hl = h == null ? null : cat.find(h.lookItem, h.lookCmd);
        rows.add(new Row(Kind.HEAD, 0, hl != null ? preview(hl) : new ItemStack(Items.LEATHER_HELMET), "Head",
                h == null ? "nothing extra" : hl != null ? hl.name() : h.lookName + " (not loaded)"));
    }

    // ------------------------------------------------------------------ picking

    private void refilter() {
        candidates.clear();
        Catalogue cat = Catalogue.get();
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (kind == Kind.ARMOUR) {
            for (ArmourSet a : cat.armour()) {
                if (matches(words, a.name() + " " + a.asset() + " armour")) {
                    candidates.add(a);
                }
            }
            return;
        }
        String target = kind == Kind.ITEM ? cfg().items.get(index).target : null;
        List<Cosmetic> best = new ArrayList<>();
        List<Cosmetic> rest = new ArrayList<>();
        Set<String> seenModels = new HashSet<>();
        for (Cosmetic c : cat.cosmetics()) {
            if (!matches(words, c.name() + " " + c.category() + " " + c.set() + " " + c.item())) {
                continue;
            }
            boolean good = kind == Kind.ITEM ? c.item().equals(target) : wornOnHead(c);
            (good ? best : rest).add(c);
        }
        for (Cosmetic c : best) {
            if (seenModels.add(c.model())) {
                candidates.add(c);
            }
        }
        if (showAll || best.isEmpty()) {
            for (Cosmetic c : rest) {
                if (seenModels.add(c.model())) {
                    candidates.add(c);
                }
            }
        }
    }

    private static boolean wornOnHead(Cosmetic c) {
        String m = c.model().toLowerCase(Locale.ROOT);
        return c.category().equals("Hats") || c.category().equals("Backpacks")
                || m.contains("wings") || m.contains("cape") || m.contains("backpack") || m.contains("/hat/") || m.contains("halo")
                || m.contains("crown");
    }

    private static boolean matches(String[] words, String hay) {
        String h = hay.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (!w.isEmpty() && !h.contains(w)) {
                return false;
            }
        }
        return true;
    }

    private void choose(Object pick) {
        Config c = cfg();
        if (kind == Kind.ITEM && pick instanceof Cosmetic cos) {
            Config.ItemRule r = c.items.get(index);
            r.lookItem = cos.item();
            r.lookCmd = cos.cmd();
            r.lookName = cos.name();
        } else if (kind == Kind.HEAD && pick instanceof Cosmetic cos) {
            Config.HeadChoice h = new Config.HeadChoice();
            h.lookItem = cos.item();
            h.lookCmd = cos.cmd();
            h.lookName = cos.name();
            c.head = h;
        } else if (kind == Kind.ARMOUR && pick instanceof ArmourSet set) {
            Config.ArmourChoice a = new Config.ArmourChoice();
            a.asset = set.asset();
            a.name = set.name();
            c.armour.put(SLOTS[index].name(), a);
        }
        c.save();
        buildRows();
    }

    private void clearLook() {
        Config c = cfg();
        switch (kind) {
            case ITEM -> {
                Config.ItemRule r = c.items.get(index);
                r.lookItem = "";
                r.lookName = "";
            }
            case ARMOUR -> c.armour.remove(SLOTS[index].name());
            case HEAD -> c.head = null;
        }
        c.save();
    }

    private boolean isChosen(Object o) {
        Config c = cfg();
        if (o instanceof ArmourSet set) {
            Config.ArmourChoice a = kind == Kind.ARMOUR ? c.armour.get(SLOTS[index].name()) : null;
            return a != null && a.asset.equals(set.asset());
        }
        Cosmetic cos = (Cosmetic) o;
        if (kind == Kind.ITEM) {
            Config.ItemRule r = c.items.get(index);
            return r.lookItem.equals(cos.item()) && r.lookCmd == cos.cmd();
        }
        return kind == Kind.HEAD && c.head != null && c.head.lookItem.equals(cos.item()) && c.head.lookCmd == cos.cmd();
    }

    private void addHeldItem() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (held.isEmpty()) {
            notice = "Hold the item first, then press + again.";
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        List<Config.ItemRule> items = cfg().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).target.equals(id) && items.get(i).nameContains.isBlank()) {
                select(Kind.ITEM, i);
                return;
            }
        }
        Config.ItemRule r = new Config.ItemRule();
        r.target = id;
        items.add(r);
        cfg().save();
        select(Kind.ITEM, items.size() - 1);
    }

    private void select(Kind k, int i) {
        kind = k;
        index = i;
        scroll = 0;
        query = "";
        notice = "";
        rebuildWidgets();
    }

    private ItemStack preview(Cosmetic c) {
        return previews.computeIfAbsent(c.key(), k -> Dresser.preview(c));
    }

    private ItemStack icon(Object o) {
        if (o instanceof Cosmetic c) {
            return preview(c);
        }
        ArmourSet set = (ArmourSet) o;
        Cosmetic ic = set.icons()[index];
        if (ic == null) {
            for (Cosmetic other : set.icons()) {
                if (other != null) {
                    ic = other;
                    break;
                }
            }
        }
        return ic != null ? preview(ic) : new ItemStack(SLOT_ITEMS[index]);
    }

    // ------------------------------------------------------------------ input

    private int cols() {
        return Math.max(1, rw / CELL);
    }

    private int maxScroll() {
        int rowsTotal = (candidates.size() + cols() - 1) / cols();
        return Math.max(0, rowsTotal - gridH / CELL);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) {
            return true;
        }
        double mx = e.x();
        double my = e.y();
        int y = py + 26;
        for (Row r : rows) {
            if (mx >= lx && mx < lx + lw && my >= y && my < y + ROW_H - 2) {
                if (r.kind() == null) {
                    addHeldItem();
                } else {
                    select(r.kind(), r.index());
                }
                return true;
            }
            y += ROW_H;
        }
        int hit = cellAt(mx, my);
        if (hit >= 0) {
            choose(candidates.get(hit));
            return true;
        }
        return false;
    }

    private int cellAt(double mx, double my) {
        if (mx < rx || mx >= rx + cols() * CELL || my < gridY || my >= gridY + gridH) {
            return -1;
        }
        int c = (int) ((mx - rx) / CELL);
        int r = (int) ((my - gridY) / CELL) + scroll;
        int i = r * cols() + c;
        return i < candidates.size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= rx) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy) * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(px, py, px + pw, py + ph, BG);
        g.outline(px, py, pw, ph, EDGE);
        g.fill(px + 1, py + 20, px + pw - 1, py + 21, EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Dresser.bypass = true;
        try {
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            draw(g, mouseX, mouseY);
        } finally {
            Dresser.bypass = false;
        }
    }

    private void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font f = font;
        Catalogue cat = Catalogue.get();
        g.text(f, "Lemon", px + 8, py + 6, LEMON, true);
        g.text(f, "Cosmetics", px + 8 + f.width("Lemon"), py + 6, SKY, true);
        String status = cat.isEmpty() ? "No server pack loaded: join LemonCloud"
                : String.format(Locale.ROOT, "%,d looks · %d armour sets · only you see them", cat.cosmetics().size(), cat.armour().size());
        g.text(f, status, px + pw - 8 - f.width(status), py + 6, MUTED, false);

        // Left column.
        int y = py + 26;
        for (Row r : rows) {
            boolean on = r.kind() == kind && r.index() == index;
            boolean hot = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + ROW_H - 2;
            g.fill(lx, y, lx + lw, y + ROW_H - 2, on ? SEL : hot ? 0xFF1F2B42 : ROW);
            if (on) {
                g.fill(lx, y, lx + 2, y + ROW_H - 2, LEMON);
            }
            if (!r.icon().isEmpty()) {
                g.fakeItem(r.icon(), lx + 3, y + 2);
            }
            int tx = lx + (r.icon().isEmpty() ? 6 : 22);
            g.text(f, clip(f, r.label(), lx + lw - tx - 2), tx, y + 2, r.kind() == null ? LEMON : INK, false);
            g.text(f, clip(f, r.sub(), lx + lw - tx - 2), tx, y + 11, MUTED, false);
            y += ROW_H;
        }

        // Right: grid of looks.
        int cols = cols();
        if (candidates.isEmpty()) {
            String msg = cat.isEmpty() ? "Join LemonCloud and the looks appear here." : "Nothing matches.";
            g.text(f, msg, rx + (rw - f.width(msg)) / 2, gridY + gridH / 2 - 4, MUTED, false);
        } else {
            g.enableScissor(rx, gridY, rx + rw, gridY + gridH);
            int visibleRows = gridH / CELL + 1;
            Object hover = null;
            for (int r = 0; r < visibleRows; r++) {
                for (int c = 0; c < cols; c++) {
                    int i = (r + scroll) * cols + c;
                    if (i >= candidates.size()) {
                        break;
                    }
                    Object o = candidates.get(i);
                    int cx = rx + c * CELL;
                    int cy = gridY + r * CELL;
                    boolean chosen = isChosen(o);
                    boolean hot = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL
                            && mouseY < gridY + gridH;
                    g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, chosen ? 0xFF4A3E10 : hot ? SEL : ROW);
                    if (chosen) {
                        g.outline(cx + 1, cy + 1, CELL - 2, CELL - 2, LEMON);
                    }
                    g.fakeItem(icon(o), cx + 2, cy + 2);
                    if (hot) {
                        hover = o;
                    }
                }
            }
            g.disableScissor();
            if (maxScroll() > 0) {
                int track = gridH;
                int knob = Math.max(10, track * (gridH / CELL) / Math.max(1, (candidates.size() + cols - 1) / cols));
                int ky = gridY + (track - knob) * scroll / Math.max(1, maxScroll());
                g.fill(rx + rw - 2, gridY, rx + rw, gridY + track, ROW);
                g.fill(rx + rw - 2, ky, rx + rw, ky + knob, SKY);
            }
            if (hover != null) {
                g.setComponentTooltipForNextFrame(f, tooltip(hover), mouseX, mouseY);
            }
        }

        // Right, bottom label / notice.
        int by = py + ph - 24;
        if (kind == Kind.ITEM) {
            g.text(f, "Only if named", rx, by + 6, MUTED, false);
        }
        String foot = !notice.isEmpty() ? notice : footer();
        g.text(f, clip(f, foot, rw), rx, by - 11, notice.isEmpty() ? MUTED : LEMON, false);
    }

    private String footer() {
        return switch (kind) {
            case ITEM -> "Pick how this item looks to you. The server still sees the real one.";
            case ARMOUR -> "Pick an armour look for this slot.";
            case HEAD -> "Pick a hat, wings or backpack to wear.";
        };
    }

    private List<Component> tooltip(Object o) {
        List<Component> lines = new ArrayList<>();
        if (o instanceof Cosmetic c) {
            lines.add(Component.literal(c.name()).withColor(LEMON & 0xFFFFFF));
            lines.add(Component.literal(c.category() + (c.set().isEmpty() ? "" : " › " + c.set())).withColor(MUTED & 0xFFFFFF));
            lines.add(Component.literal(c.source()).withColor(SKY & 0xFFFFFF));
        } else {
            ArmourSet a = (ArmourSet) o;
            lines.add(Component.literal(a.name() + " armour").withColor(LEMON & 0xFFFFFF));
            lines.add(Component.literal(a.asset()).withColor(SKY & 0xFFFFFF));
        }
        return lines;
    }

    private static String clip(Font f, String s, int w) {
        if (f.width(s) <= w) {
            return s;
        }
        String e = "...";
        while (!s.isEmpty() && f.width(s + e) > w) {
            s = s.substring(0, s.length() - 1);
        }
        return s + e;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A flat button in the lemon / sky colours. */
    private static final class LemonButton extends Button {

        private final String text;
        private final int accent;

        LemonButton(int x, int y, int w, int h, String text, int accent, OnPress onPress) {
            super(x, y, w, h, Component.literal(text), onPress, DEFAULT_NARRATION);
            this.text = text;
            this.accent = accent;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x1 = getX();
            int y1 = getY();
            int x2 = x1 + getWidth();
            int y2 = y1 + getHeight();
            boolean hot = isHoveredOrFocused();
            g.fill(x1, y1, x2, y2, hot ? accent : ROW);
            g.outline(x1, y1, getWidth(), getHeight(), accent);
            Font font = Minecraft.getInstance().font;
            String t = clip(font, text, getWidth() - 4);
            g.text(font, t, x1 + (getWidth() - font.width(t)) / 2, y1 + (getHeight() - 8) / 2, hot ? 0xFF10151F : INK, false);
        }
    }
}
