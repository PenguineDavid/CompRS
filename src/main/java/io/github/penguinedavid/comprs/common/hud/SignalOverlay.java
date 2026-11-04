package io.github.penguinedavid.comprs.common.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SignalOverlay {

    private SignalOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!io.github.penguinedavid.comprs.common.main.showSignalOverlay) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gui.screen() != null
            || !(minecraft.hitResult instanceof BlockHitResult blockHit)
            || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockHit.getBlockPos());
        int signal;
        if (blockEntity instanceof ComparatorBlockEntity comparator) {
            signal = comparator.getOutputSignal();
        } else if (blockEntity instanceof BarrelBlockEntity) {
            signal = AbstractContainerMenu.getRedstoneSignalFromBlockEntity(blockEntity);
        } else {
            return;
        }
        if (signal <= 15) {
            return;
        }

        String text = "Signal: " + signal;
        int x = (graphics.guiWidth() - minecraft.font.width(text)) / 2;
        int y = graphics.guiHeight() / 2 - 18;
        graphics.text(minecraft.font, text, x, y, 0xFFFFFFFF, true);
    }
}