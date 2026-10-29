package dev.sefiraat.cultivation.implementation.listeners;

import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.api.interfaces.CustomPlacementBlock;
import dev.sefiraat.cultivation.api.slimefun.items.bushes.CultivationBush;
import dev.sefiraat.cultivation.api.slimefun.items.plants.CultivationPlant;
import dev.sefiraat.sefilib.entity.display.DisplayGroup;
import dev.sefiraat.cultivation.api.slimefun.items.plants.HarvestablePlant;
import dev.sefiraat.cultivation.implementation.utils.Keys;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import dev.sefiraat.cultivation.api.interfaces.CultivationCroppable;
import dev.sefiraat.cultivation.implementation.slimefun.CultivationStacks;
import dev.sefiraat.cultivation.implementation.slimefun.tools.CropSticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The purpose of this listener is to allow us to cancel the block placement if not on the
 * correct material. If done within the onBlockPlace handler, the BlockStorage is retained
 * leading to dupes.
 * Also allows other objects implementing {@link CustomPlacementBlock}
 * to fire their own methods
 * TODO PR to slimefun to either do blockstorage after checking the event is cancelled or to remove
 */
public class CustomPlacementListener implements Listener {

    private static final java.util.Set<String> PENDING_REMOVAL = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static boolean tryAcquireRemoval(Location loc) {
        return PENDING_REMOVAL.add(locKey(loc));
    }

    public static void releaseRemoval(Location loc) {
        PENDING_REMOVAL.remove(locKey(loc));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(@Nonnull BlockPlaceEvent event) {
        SlimefunItem slimefunItem = SlimefunItem.getByItem(event.getItemInHand());
        if (slimefunItem instanceof CustomPlacementBlock block) {
            block.whenPlaced(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWaterHitsPlant(@Nonnull BlockFromToEvent event) {
        Location location = event.getToBlock().getLocation();
        unsafelyKillItem(location, BlockStorage.check(location));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtends(@Nonnull BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Block issueBlock = block.getRelative(BlockFace.UP);
            Location location = issueBlock.getLocation();
            unsafelyKillItem(location, BlockStorage.check(location));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetracts(@Nonnull BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Block issueBlock = block.getRelative(BlockFace.UP);
            Location location = issueBlock.getLocation();
            unsafelyKillItem(location, BlockStorage.check(location));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(@Nonnull BlockSpreadEvent event) {
        Location location = event.getBlock().getLocation();
        unsafelyKillItem(location, BlockStorage.check(location));
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoneMeal(@Nonnull BlockFertilizeEvent event) {
        for (BlockState blockState : event.getBlocks()) {
            Block issueBlock = blockState.getBlock();
            Location location = issueBlock.getLocation();
            unsafelyKillItem(location, BlockStorage.check(location));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSnowmanBlockForm(@Nonnull EntityBlockFormEvent event) {
        Location location = event.getBlock().getLocation();
        unsafelyKillItem(location, BlockStorage.check(location));
    }

    /**
     * Plants/Bushes are represented by player head / display entities. Breaking the supporting
     * block bypasses Slimefun's normal break handler, so remove the captured displays once
     * Minecraft has resolved the physics update instead of leaving an untouchable ghost.
     * Also handles AIR-display plants where BlockPhysics may not clear BlockStorage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlantPhysics(@Nonnull BlockPhysicsEvent event) {
        Block block = event.getBlock();
        SlimefunItem slimefunItem = BlockStorage.check(block);
        if (!(slimefunItem instanceof CultivationPlant) && !(slimefunItem instanceof CultivationBush)) {
            return;
        }
        // Solo AIR-display (stage>=1) necesita nuestro manejo; PLAYER_HEAD stage0 lo maneja Slimefun SENSITIVE_MATERIALS
        if (block.getType() != Material.AIR) {
            return;
        }
        if (block.getRelative(BlockFace.DOWN).getType().isSolid()) {
            return;
        }

        Location location = block.getLocation();
        SlimefunItem capturedItem = slimefunItem;
        // Capturar grupos antes del tick siguiente (pueden perderse al limpiar storage)
        DisplayGroup plantDisplay = null;
        DisplayGroup cropDisplay = null;
        DisplayGroup bushDisplay = null;
        if (capturedItem instanceof CultivationPlant plant) {
            plantDisplay = plant.getPlantDisplayGroup(location);
            cropDisplay = plant.getCropDisplayGroup(location);
        } else if (capturedItem instanceof CultivationBush bush) {
            bushDisplay = bush.getBushDisplayGroup(location);
        }

        DisplayGroup finalPlantDisplay = plantDisplay;
        DisplayGroup finalCropDisplay = cropDisplay;
        DisplayGroup finalBushDisplay = bushDisplay;
        Bukkit.getScheduler().runTask(Cultivation.getInstance(), () -> {
            // Si el soporte sigue sólido alguien lo repuso en el mismo tick
            if (location.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
                return;
            }
            SlimefunItem still = BlockStorage.check(location);
            if (still != null) {
                unsafelyKillItem(location, still);
                return;
            }
            // Limpieza de ghosts huérfanos donde BlockStorage ya fue borrado pero quedan displays
            if (finalPlantDisplay != null) {
                finalPlantDisplay.remove();
            }
            if (finalCropDisplay != null) {
                finalCropDisplay.remove();
            }
            if (finalBushDisplay != null) {
                finalBushDisplay.remove();
            }
            if (BlockStorage.hasBlockInfo(location.getBlock())) {
                BlockStorage.clearBlockInfo(location);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSupportBreak(@Nonnull org.bukkit.event.block.BlockBreakEvent event) {
        Block broken = event.getBlock();
        Block above = broken.getRelative(BlockFace.UP);
        // Solo AIR-display necesita nuestro manejo; PLAYER_HEAD stage0 lo maneja Slimefun via SENSITIVE_MATERIALS
        if (above.getType() != Material.AIR) {
            return;
        }
        SlimefunItem aboveItem = BlockStorage.check(above);
        if (aboveItem == null) {
            return;
        }
        Location loc = above.getLocation();
        Bukkit.getScheduler().runTask(Cultivation.getInstance(), () -> {
            SlimefunItem still = BlockStorage.check(loc);
            if (still != null) {
                unsafelyKillItem(loc, still);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLiquidDispense(@Nonnull BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (isLiquid(event.getItem())
            && block.getBlockData() instanceof Directional directional
        ) {
            BlockFace face = directional.getFacing();
            Location location = block.getRelative(face).getLocation();
            unsafelyKillItem(location, BlockStorage.check(location));
        }
    }

    /**
     * Cosecha y uso de herramientas via click directo a Displays e Interaction.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDisplayInteract(@Nonnull PlayerInteractEntityEvent event) {
        DisplayGroup group = null;
        if (event.getRightClicked() instanceof Display display) {
            String parentUuid = display.getPersistentDataContainer().get(Keys.DISPLAY_ENTITY, PersistentDataType.STRING);
            if (parentUuid != null) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(parentUuid);
                    group = DisplayGroup.fromUUID(uuid);
                } catch (IllegalArgumentException ignored) {}
            }
        } else if (event.getRightClicked() instanceof Interaction interaction) {
            group = DisplayGroup.fromInteraction(interaction);
        }

        if (group == null) return;
        Location loc = group.getLocation().getBlock().getLocation();
        SlimefunItem item = BlockStorage.check(loc);
        if (item == null) return;

        Player player = event.getPlayer();

        // El clic sobre el Display/Interaction no pasa por Slimefun, asi que la
        // proteccion de la region hay que consultarla aqui igual que en la rotura.
        if (Slimefun.getProtectionManager() != null
                && !Slimefun.getProtectionManager().hasPermission(player, loc, io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction.INTERACT_BLOCK)) {
            event.setCancelled(true);
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        SlimefunItem sfHeld = SlimefunItem.getByItem(held);

        // Caso 1: Aplicar CropSticks a la planta o soporte
        if (sfHeld instanceof CropSticks && item instanceof CultivationCroppable croppable) {
            if (croppable.incrementCrop(loc)) {
                player.swingMainHand();
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    held.setAmount(held.getAmount() - 1);
                }
                event.setCancelled(true);
                return;
            }
        }

        // Caso 2: Cosechar planta madura
        if (item instanceof HarvestablePlant plant && plant.isMature(loc.getBlock())) {
            event.setCancelled(true);
            plant.harvest(loc.getBlock());
            player.swingMainHand();
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDisplayAttack(@Nonnull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Display display)) return;
        if (!(event.getDamager() instanceof Player player)) return;
        String parentUuid = display.getPersistentDataContainer().get(Keys.DISPLAY_ENTITY, PersistentDataType.STRING);
        if (parentUuid == null) return;
        java.util.UUID uuid;
        try { uuid = java.util.UUID.fromString(parentUuid); } catch (IllegalArgumentException e) { return; }
        DisplayGroup group = DisplayGroup.fromUUID(uuid);
        if (group == null) return;
        handleGroupBreak(event, player, group);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractionAttack(@Nonnull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction)) return;
        if (!(event.getDamager() instanceof Player player)) return;
        DisplayGroup group = DisplayGroup.fromInteraction(interaction);
        if (group == null) return;
        handleGroupBreak(event, player, group);
    }

    private void handleGroupBreak(@Nonnull EntityDamageByEntityEvent event, @Nonnull Player player, @Nonnull DisplayGroup group) {
        Location loc = group.getLocation().getBlock().getLocation();
        SlimefunItem item = BlockStorage.check(loc);
        if (item == null) {
            group.remove();
            event.setCancelled(true);
            return;
        }

        if (Slimefun.getProtectionManager() != null
                && !Slimefun.getProtectionManager().hasPermission(player, loc, io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction.BREAK_BLOCK)) {
            return;
        }

        event.setCancelled(true);
        unsafelyKillItem(loc, item);
        try { group.remove(); } catch (Exception ignored) {}
    }

    private void unsafelyKillItem(@Nonnull Location location, @Nullable SlimefunItem slimefunItem) {
        if (slimefunItem == null) return;
        // Deduplicar: si ya se está borrando en otro handler/tick, no dropear de nuevo (evita 4x)
        if (!tryAcquireRemoval(location)) return;
        try {
            // Re-validar dentro del lock - otro hilo puede haber limpiado entre el check y el acquire
            SlimefunItem current = BlockStorage.check(location);
            if (current == null || current != slimefunItem) {
                // Si cambio de item o ya borrado, usar el current si aún es flora
                slimefunItem = current;
                if (slimefunItem == null) return;
            }
            if (slimefunItem instanceof CultivationPlant plant) {
                Location dropLoc = location.clone().add(0.5, 0.5, 0.5);
                ItemStack itemToDrop = plant.getDroppedItemStack(location);
                boolean wasCropped = plant.isCropped(location);
                boolean wasCrossCropped = plant.isCrossCropped(location);
                if (itemToDrop != null && itemToDrop.getType() != Material.AIR) {
                    dropLoc.getWorld().dropItem(dropLoc, itemToDrop);
                }
                if (wasCrossCropped) {
                    ItemStack sticks = CultivationStacks.CROP_STICKS.clone();
                    sticks.setAmount(2);
                    dropLoc.getWorld().dropItem(dropLoc, sticks);
                } else if (wasCropped) {
                    dropLoc.getWorld().dropItem(dropLoc, CultivationStacks.CROP_STICKS.clone());
                }
                if (plant instanceof HarvestablePlant harvestable) {
                    harvestable.forgetNextDrop(location);
                }
                plant.removeCropped(location);
                plant.removePlantDisplayGroup(location);
                plant.removeLevelProfile(location);
                plant.removeOwner(location);
                BlockStorage.clearBlockInfo(location);
                location.getBlock().setType(Material.AIR);
            } else if (slimefunItem instanceof CultivationBush bush) {
                Location dropLoc = location.clone().add(0.5, 0.5, 0.5);
                dropLoc.getWorld().dropItem(dropLoc, bush.getItem().clone());
                bush.removeBushDisplayGroup(location);
                bush.removeOwner(location);
                BlockStorage.clearBlockInfo(location);
                location.getBlock().setType(Material.AIR);
            }
        } finally {
            releaseRemoval(location);
        }
    }

    private boolean isLiquid(@Nonnull ItemStack itemStack) {
        Material material = itemStack.getType();
        return material == Material.WATER_BUCKET
            || material == Material.LAVA_BUCKET
            || material == Material.POWDER_SNOW_BUCKET;
    }
}
