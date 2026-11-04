package io.github.penguinedavid.comprs.common.command;

import io.github.penguinedavid.comprs.common.util.BarrelSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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

    public static final String OVERLOADED_STACK_MARKER = "comprs_overloaded_signal_stack";

    private BarrelGiver() {
    }

    public record Result(boolean success, String message) {
    }

    /** Gives the player a signal barrel in an empty inventory slot. Requires Creative mode. */
    public static Result give(int signal) {
        Minecraft minecraft = Minecraft.getInstance();
        Result unavailable = checkCreativeMode(minecraft);
        if (unavailable != null) {
            return unavailable;
        }

        ItemStack barrel = new ItemStack(Items.BARREL);
        barrel.set(DataComponents.CUSTOM_NAME, Component.literal("signal " + signal));
        barrel.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        barrel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(redstoneContents(signal)));
        return giveIntoEmptySlot(minecraft, barrel, "Given signal " + signal + " barrel");
    }

    public static Result giveOverloaded(int signal) {
        Minecraft minecraft = Minecraft.getInstance();
        Result unavailable = checkCreativeMode(minecraft);
        if (unavailable != null) {
            return unavailable;
        }

        ItemStack barrel = new ItemStack(Items.BARREL);
        barrel.set(DataComponents.CUSTOM_NAME, Component.literal("overloaded signal " + signal));
        barrel.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        barrel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(overloadedContents(signal)));
        return giveIntoEmptySlot(minecraft, barrel, "Given overloaded signal " + signal + " barrel");
    }

    private static Result checkCreativeMode(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null
            || minecraft.gameMode.getPlayerMode() != GameType.CREATIVE
            || !minecraft.player.hasInfiniteMaterials()) {
            return new Result(false, "Creative mode is required");
        }
        return null;
    }

    private static Result giveIntoEmptySlot(Minecraft minecraft, ItemStack item, String successMessage) {
        int inventorySlot = minecraft.player.getInventory().getFreeSlot();
        if (inventorySlot == -1) {
            return new Result(false, "Inventory full");
        }

        minecraft.player.getInventory().setItem(inventorySlot, item.copy());
        int menuSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        minecraft.gameMode.handleCreativeModeItemAdd(item, menuSlot);
        return new Result(true, successMessage);
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

    private static List<ItemStack> overloadedContents(int signal) {
        int totalItems = BarrelSignal.overloadedItemCount(signal);
        List<ItemStack> contents = new ArrayList<>();
        while (totalItems > 0) {
            int stackSize = Math.min(BarrelSignal.REDSTONE_STACK_SIZE, totalItems);
            ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
            stack.setCount(stackSize);
            CompoundTag marker = new CompoundTag();
            marker.putBoolean(OVERLOADED_STACK_MARKER, true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
            contents.add(stack);
            totalItems -= stackSize;
        }
        return contents;
    }
}