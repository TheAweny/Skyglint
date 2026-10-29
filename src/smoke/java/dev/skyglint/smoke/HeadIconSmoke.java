package dev.skyglint.smoke;

import dev.skyglint.core.PetSnapshot;
import dev.skyglint.fabric.PetIconCache;
import dev.skyglint.ui.HudItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2f;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Offline integration probe: a slot-overlay hook must not be called by native HUD head extraction. */
final class HeadIconSmoke {
    private static final List<ItemStack> heads = new ArrayList<>();

    static void verify(Minecraft client) {
        heads.clear();
        for (String name : List.of("Sheep", "Tiger")) {
            ItemStack head = PetIconCache.INSTANCE.resolve(new PetSnapshot(name, 100, null, false));
            if (head == null) throw new AssertionError("Missing known pet fixture: " + name);
            heads.add(head.copy());
            ItemStack before = head.copy();
            GuiRenderState state = new GuiRenderState();
            InventoryOverlayProbe graphics = new InventoryOverlayProbe(client, state);
            graphics.pose().translate(13, 17);
            graphics.enableScissor(0, 0, 60, 60);
            Matrix3x2f expectedPose = new Matrix3x2f(graphics.pose());
            var expectedScissor = graphics.scissorStack.peek();
            graphics.item(head, 3, 4); // The simulated add-on decorates a normal inventory helper.
            if (graphics.overlays != 1) throw new AssertionError("Inventory overlay control was not exercised");
            HudItemRenderer.INSTANCE.item(graphics, head, 3, 4);
            if (graphics.overlays != 1) throw new AssertionError("HUD head invoked an inventory overlay");
            List<GuiItemRenderState> submitted = new ArrayList<>();
            state.forEachItem(submitted::add);
            if (submitted.size() != 2) throw new AssertionError("Both paths must enqueue one native model");
            GuiItemRenderState ordinary = submitted.getFirst();
            GuiItemRenderState hud = submitted.getLast();
            if (!ordinary.itemStackRenderState().getModelIdentity().equals(hud.itemStackRenderState().getModelIdentity()))
                throw new AssertionError("HUD changed the native head model/profile identity");
            if (!hud.pose().equals(expectedPose) || !java.util.Objects.equals(hud.scissorArea(), expectedScissor) ||
                hud.x() != 3 || hud.y() != 4)
                throw new AssertionError("HUD head lost its pose, scissor or coordinates");
            graphics.pose().translate(100, 100);
            if (!hud.pose().equals(expectedPose)) throw new AssertionError("Submitted pose was not copied");
            if (!ItemStack.isSameItemSameComponents(before, head)) throw new AssertionError("HUD mutated the observed head components");
            HudItemRenderer.INSTANCE.item(graphics, new ItemStack(Items.DIAMOND), 1, 2);
            if (graphics.overlays != 2) throw new AssertionError("Non-head items lost the ordinary helper path");
            graphics.disableScissor();
        }
        LoggerFactory.getLogger("SkyglintHeadSmoke").info(
            "REBORN_HEAD_ICON_PASSED: Sheep/Tiger native model identity and components preserved, copied pose/scissor, inventory-overlay isolation, non-head helper unchanged");
    }

    static Screen proofScreen() { return new HeadProofScreen(); }

    /** Simulates an inventory mod adding a 16x16 backing, without depending on another mod. */
    private static final class InventoryOverlayProbe extends GuiGraphicsExtractor {
        private int overlays;
        InventoryOverlayProbe(Minecraft client, GuiRenderState state) { super(client, state, 0, 0); }
        @Override public void item(ItemStack stack, int x, int y) {
            overlays++;
            fill(x, y, x + 16, y + 16, 0xFF585858);
            super.item(stack, x, y);
        }
    }

    private static final class HeadProofScreen extends Screen {
        HeadProofScreen() { super(Component.literal("HUD head overlay isolation - synthetic probe")); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xE0101620);
            graphics.centeredText(minecraft.font, "Synthetic inventory-overlay compatibility probe", width / 2, 35, -1);
            graphics.centeredText(minecraft.font, "Inventory helper + simulated backing", width / 3, 65, -1);
            graphics.centeredText(minecraft.font, "HUD native head model", width * 2 / 3, 65, -1);
            for (int index = 0; index < heads.size(); index++) {
                ItemStack head = heads.get(index);
                int y = 105 + index * 105;
                graphics.text(minecraft.font, index == 0 ? "Sheep" : "Tiger", 30, y + 15, -1);
                for (int column = 0; column < 2; column++) {
                    graphics.pose().pushMatrix();
                    try {
                        graphics.pose().translate((column == 0 ? width / 3f : width * 2 / 3f) - 24, y);
                        graphics.pose().scale(3, 3);
                        if (column == 0) {
                            graphics.fill(0, 0, 16, 16, 0xFF585858);
                            graphics.item(head, 0, 0);
                        } else HudItemRenderer.INSTANCE.item(graphics, head, 0, 0);
                    } finally { graphics.pose().popMatrix(); }
                }
            }
            graphics.centeredText(minecraft.font, "Both columns use the same complete native pet ItemStack", width / 2, 350, 0xFFB0BAC8);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
