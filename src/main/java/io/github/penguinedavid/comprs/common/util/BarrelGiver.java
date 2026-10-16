package io.github.penguinedavid.comprs.common.command;

import io.github.penguinedavid.comprs.common.util.BarrelSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the player a barrel filled so a comparator reads a chosen signal strength.
 * Shared by the ,barrel chat command and the GUI's Barrel panel so both behave
 * identically. This is the Minecraft-facing half; the math lives in {@link BarrelSignal}.
 */
public final class BarrelGiver {

    private BarrelGiver() {
    }

    public record Result(boolean success, String message) {
    }

    /** Replaces the selected hotbar slot with a signal barrel. Requires Creative mode. */
    public static Result give(int signal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null
            || minecraft.gameMode.getPlayerMode() != GameType.CREATIVE
            || !minecraft.player.hasInfiniteMaterials()) {
            return new Result(false, "Creative mode is required");
        }

        ItemStack barrel = new ItemStack(Items.BARREL);
        barrel.set(DataComponents.CUSTOM_NAME, Component.literal("signal " + signal));
        barrel.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        barrel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(redstoneContents(signal)));
        int selectedSlot = minecraft.player.getInventory().getSelectedSlot();
        minecraft.player.getInventory().setItem(selectedSlot, barrel.copy());
        minecraft.gameMode.handleCreativeModeItemAdd(barrel, 36 + selectedSlot);
        return new Result(true, "Given signal " + signal + " barrel");
    }

    private static List<ItemStack> redstoneContents(int signal) {
        int totalItems = BarrelSignal.redstoneItemCount(signal);
        List<ItemStack> contents = new ArrayList<>();
        while (totalItems > 0) {
            int stackSize = Math.min(BarrelSignal.REDSTONE_STACK_SIZE, totalItems);
            contents.add(new ItemStack(Items.REDSTONE, stackSize));
            totalItems -= stackSize;
        }
        return contents;
    }
}