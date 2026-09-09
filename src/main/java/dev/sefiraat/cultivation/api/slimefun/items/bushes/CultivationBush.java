package dev.sefiraat.cultivation.api.slimefun.items.bushes;

import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.Registry;
import dev.sefiraat.cultivation.api.interfaces.CultivationBushHolder;
import dev.sefiraat.cultivation.api.interfaces.CultivationFlora;
import dev.sefiraat.cultivation.api.interfaces.CultivationTrimmable;
import dev.sefiraat.cultivation.api.slimefun.RecipeTypes;
import dev.sefiraat.cultivation.api.slimefun.groups.CultivationGroups;
import dev.sefiraat.cultivation.api.slimefun.items.CultivationFloraItem;
import dev.sefiraat.cultivation.api.slimefun.plant.Growth;
import dev.sefiraat.cultivation.implementation.slimefun.tools.TrimmingTool;
import dev.drake.sefilib.entity.display.DisplayInteractable;
import com.github.drakescraft_labs.slimefun4.api.SlimefunAddon;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockBreakHandler;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * This bush can be trimmed by right-clicking a {@link TrimmingTool}
 * dropping the provided ItemStack into the world.
 * This class is used to define a Bush that will grow as a {@link CultivationFlora}
 */
public abstract class CultivationBush extends CultivationFloraItem<CultivationBush>
    implements CultivationFlora, CultivationTrimmable, CultivationBushHolder, DisplayInteractable {

    @ParametersAreNonnullByDefault
    protected CultivationBush(SlimefunItemStack item, Growth growth) {
        super(CultivationGroups.BUSHES, item, RecipeTypes.TRADING_FARMER, new ItemStack[0], null, growth);
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(
            new BlockBreakHandler(false, false) {
                @Override
                @ParametersAreNonnullByDefault
                public void onPlayerBreak(BlockBreakEvent blockBreakEvent, ItemStack itemStack, List<ItemStack> list) {
                    onBreak(blockBreakEvent);
                }
            }
        );
    }

    @Override
    public void onTickAlways(org.bukkit.Location location, com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem flora, me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config data) {
        try {
            if (!location.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                // Usar deduplicación global para no dropear 4x cuando onPlantPhysics/onSupportBreak también disparan
                if (!dev.sefiraat.cultivation.implementation.listeners.CustomPlacementListener.tryAcquireRemoval(location)) return;
                Bukkit.getScheduler().runTask(dev.sefiraat.cultivation.Cultivation.getInstance(), () -> {
                    try {
                        if (BlockStorage.check(location) instanceof CultivationBush still && !location.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                            Location loc = location;
                            loc.getWorld().dropItem(loc.clone().add(0.5, 0.5, 0.5), still.getItem().clone());
                            still.removeBushDisplayGroup(loc);
                            still.removeOwner(loc);
                            dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler.forget(loc);
                            BlockStorage.clearBlockInfo(loc);
                            loc.getBlock().setType(org.bukkit.Material.AIR);
                        }
                    } finally {
                        dev.sefiraat.cultivation.implementation.listeners.CustomPlacementListener.releaseRemoval(location);
                    }
                });
                return;
            }
        } catch (Exception ignored) {}
        // Misma auto-reparación de siempre, pero espaciada y acotada por FloraTickScheduler:
        // reconstruir el DisplayGroup de cada arbusto en cada pasada costaba una búsqueda global
        // de entidad y varias lecturas de PDC por arbusto y por segundo.
        try {
            if (!dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler.shouldInspect(location)) {
                return;
            }
            // Dentro de la ventana se conserva la comprobación completa de siempre: el grupo
            // puede haber perdido su lista de hijos aunque el Interaction padre siga vivo.
            var group = getBushDisplayGroup(location);
            if (hasDisplayBush(data) && group != null) {
                return;
            }
            addDisplayBush(location);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int getMaxGrowthStages() {
        return 3;
    }

    @OverridingMethodsMustInvokeSuper
    protected void onBreak(@NotNull BlockBreakEvent event) {
        var location = event.getBlock().getLocation();
        removeBush(location);
        removeOwner(location);
        dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler.forget(location);
    }

    @Override
    public CultivationBush tryRegister(@NotNull SlimefunAddon addon) {
        Registry.getInstance().addBush(this);
        return super.tryRegister(addon);
    }
}
