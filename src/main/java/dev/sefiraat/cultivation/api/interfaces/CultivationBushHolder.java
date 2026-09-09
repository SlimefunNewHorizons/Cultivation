package dev.sefiraat.cultivation.api.interfaces;

import dev.sefiraat.cultivation.implementation.utils.DisplayGroupGenerators;
import dev.drake.sefilib.entity.display.DisplayGroup;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public interface CultivationBushHolder {

    String BUSH = "bush_display_present";
    String GROUP_PARENT = "bush_display_group";

    default boolean hasDisplayBush(@Nonnull Block block) {
        return hasDisplayBush(block.getLocation());
    }

    default boolean hasDisplayBush(@Nonnull Location location) {
        String hasBush = BlockStorage.getLocationInfo(location, BUSH);
        return Boolean.parseBoolean(hasBush);
    }

    default boolean hasDisplayBush(@Nonnull Config config) {
        String hasBush = config.getString(BUSH);
        return Boolean.parseBoolean(hasBush);
    }

    default void removeBush(@Nonnull Location location) {
        removeBushDisplayGroup(location);
        BlockStorage.addBlockInfo(location, BUSH, null);
        BlockStorage.addBlockInfo(location, GROUP_PARENT, null);
    }

    default void addDisplayBush(@Nonnull Location location) {
        DisplayGroup displayGroup = DisplayGroupGenerators.generateBush(location.clone().add(0.5, 0, 0.5));
        // A responsive interaction is required for left-click removal.
        displayGroup.getParentDisplay().setResponsive(true);
        BlockStorage.addBlockInfo(location, BUSH, "true");
        BlockStorage.addBlockInfo(location, GROUP_PARENT, displayGroup.getParentUUID().toString());
    }

    default void setAge(@Nonnull Location location, int age) {
        DisplayGroup displayGroup = getBushDisplayGroup(location);
        if (displayGroup == null && hasDisplayBush(location)) {
            // The parent interaction was lost: rebuild the bush display so it stays interactive.
            addDisplayBush(location);
            displayGroup = getBushDisplayGroup(location);
        }
        if (displayGroup != null) {
            DisplayGroupGenerators.setBushAge(displayGroup, age);
        }
    }

    default void addItemsToDisplay(@Nonnull Location location, @Nonnull ItemStack itemStack) {
        if (hasDisplayBush(location)) {
            DisplayGroup group = getBushDisplayGroup(location);
            if (group == null) {
                // The parent interaction was lost (e.g. chunk reload or cleanup), so rebuild the
                // bush display instead of leaving an untouchable ghost.
                addDisplayBush(location);
                group = getBushDisplayGroup(location);
            }
            if (group != null) {
                DisplayGroupGenerators.addItemsToPlant(group, itemStack);
            }
        }
    }

    default void removeItems(@Nonnull Location location) {
        if (hasDisplayBush(location)) {
            DisplayGroup group = getBushDisplayGroup(location);
            if (group != null) {
                DisplayGroupGenerators.removeItemsFromPlant(group);
            }
        }
    }

    @Nullable
    default UUID getBushDisplayGroupUUID(@Nonnull Location location) {
        String uuid = BlockStorage.getLocationInfo(location, GROUP_PARENT);
        if (uuid == null) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            BlockStorage.addBlockInfo(location, GROUP_PARENT, null);
            return null;
        }
    }

    @Nullable
    default DisplayGroup getBushDisplayGroup(@Nonnull Location location) {
        UUID uuid = getBushDisplayGroupUUID(location);
        if (uuid == null) {
            return null;
        }
        DisplayGroup displayGroup = DisplayGroup.fromUUID(uuid);
        if (displayGroup != null) {
            // Repara displays de builds antiguas al leerlos, pero solo si hace falta:
            // setResponsive reenvia metadatos de entidad a todos los jugadores cercanos.
            if (!displayGroup.getParentDisplay().isResponsive()) {
                displayGroup.getParentDisplay().setResponsive(true);
            }
        }
        return displayGroup;
    }

    default void removeBushDisplayGroup(@Nonnull Location location) {
        DisplayGroup displayGroup = getBushDisplayGroup(location);
        if (displayGroup != null) {
            displayGroup.remove();
        }
    }
}
