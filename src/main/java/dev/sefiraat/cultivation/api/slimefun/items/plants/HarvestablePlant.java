package dev.sefiraat.cultivation.api.slimefun.items.plants;

import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.api.datatypes.instances.FloraLevelProfile;
import dev.sefiraat.cultivation.api.interfaces.CultivationHarvestable;
import dev.sefiraat.cultivation.api.slimefun.plant.Growth;
import dev.sefiraat.cultivation.api.slimefun.plant.PlantTheme;
import dev.sefiraat.cultivation.implementation.slimefun.tools.PlantAnalyser;
import dev.sefiraat.cultivation.implementation.utils.Keys;
import dev.drake.sefilib.entity.display.DisplayGroup;
import io.github.bakedlibs.dough.collections.RandomizedSet;
import com.github.drakescraft_labs.slimefun4.api.events.PlayerRightClickEvent;
import com.github.drakescraft_labs.slimefun4.api.items.ItemSetting;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.items.settings.DoubleRangeSetting;
import com.github.drakescraft_labs.slimefun4.api.items.settings.IntRangeSetting;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This plant can be harvested by right-clicking it
 * dropping the provided ItemStack into the world.
 * The plant then reverts to its first stage in its {@link PlantTheme}
 */
public class HarvestablePlant extends CultivationPlant implements CultivationHarvestable {

    private static final String KEY_GROWTH_RATE = "growth-rate";
    private final RandomizedSet<ItemStack> harvestItems = new RandomizedSet<>();
    private final Map<Location, ItemStack> nextDrop = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public HarvestablePlant(SlimefunItemStack item, Growth growth) {
        super(item, growth);
        addItemSetting(new DoubleRangeSetting(this, KEY_GROWTH_RATE, 0.001, growth.getGrowthRate(), 1D));
        Optional<ItemSetting<Double>> doubleOptional = getItemSetting(KEY_GROWTH_RATE, double.class);
        doubleOptional.ifPresent(doubleItemSetting -> this.growth.setGrowthRate(doubleItemSetting.getValue()));
    }

    @Nonnull
    public HarvestablePlant addHarvestingResult(@Nonnull ItemStack harvestStack) {
        return addHarvestingResult(harvestStack, 1);
    }

    @Nonnull
    public HarvestablePlant addHarvestingResult(@Nonnull ItemStack harvestStack, int weight) {
        String amountKey = "drop-" + harvestStack.getType().name().toLowerCase(Locale.ROOT) + "-amount";
        String weightKey = "drop-" + harvestStack.getType().name().toLowerCase(Locale.ROOT) + "-weight";
        int defaultAmount = harvestStack.getAmount();

        addItemSetting(new IntRangeSetting(this, amountKey, 1, defaultAmount, 64));
        addItemSetting(new IntRangeSetting(this, weightKey, 1, weight, 999));

        Optional<ItemSetting<Integer>> setAmount = getItemSetting(amountKey, int.class);
        Optional<ItemSetting<Integer>> setWeight = getItemSetting(weightKey, int.class);

        int finalAmount = setAmount.map(ItemSetting::getValue).orElse(defaultAmount);
        int finalWeight = setWeight.map(ItemSetting::getValue).orElse(weight);

        harvestStack.setAmount(finalAmount);

        this.harvestItems.add(harvestStack, finalWeight);
        return this;
    }

    @Override
    protected void onBlockUse(@NotNull PlayerRightClickEvent event) {
        if (event.useBlock() == org.bukkit.event.Event.Result.DENY
                || event.getInteractEvent().isCancelled()) {
            return;
        }
        Optional<Block> blockOptional = event.getClickedBlock();
        if (blockOptional.isEmpty() || harvestItems.isEmpty()) {
            return;
        }
        Block block = blockOptional.get();
        if (this.isMature(block)) {
            harvest(block);
            // Evitar que Slimefun abra el BlockMenu dummy tras cosechar
            event.cancel();
        }
    }

    /**
     * Olvida el drop precalculado de esta posicion. Sin esto el mapa crece sin
     * limite con cada planta rota y una planta nueva colocada en la misma
     * posicion heredaria el drop de la anterior.
     */
    public void forgetNextDrop(@Nonnull Location location) {
        nextDrop.remove(location);
    }

    @Override
    public void onBreak(@NotNull org.bukkit.event.block.BlockBreakEvent event) {
        forgetNextDrop(event.getBlock().getLocation());
        super.onBreak(event);
    }

    public void harvest(@Nonnull Block block) {
        if (this.isMature(block)) {
            updateGrowthStage(block, 1);
            ItemStack itemStack = nextDrop.remove(block.getLocation());
            Location dropLoc = block.getLocation().clone().add(0.5, 0.5, 0.5);
            if (itemStack == null) {
                ItemStack drop = harvestItems.getRandom();
                if (drop != null) {
                    block.getWorld().dropItem(dropLoc, drop.clone());
                }
            } else {
                block.getWorld().dropItem(dropLoc, itemStack);
            }
            block.getWorld().spawnParticle(Particle.WAX_OFF, dropLoc, 5, 0.2, 0.2, 0.2);
            block.getWorld().playSound(dropLoc, org.bukkit.Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
        }
    }

    @Override
    public void updateGrowthStage(@Nonnull Block block, int growthStage) {
        if (growthStage == 0) {
            PlantTheme theme = growth.getTheme();
            if (theme != null) {
                // Use native Bukkit API instead of PlayerHead.setSkin() which doesn't work in
                // 1.20.6
                org.bukkit.block.Skull skull = (org.bukkit.block.Skull) block.getState();
                org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit
                        .createPlayerProfile(java.util.UUID.randomUUID());
                org.bukkit.profile.PlayerTextures textures = profile.getTextures();

                try {
                    // Convert hash to texture URL
                    String hash = theme.getSeed().getHash();
                    java.net.URL url = new java.net.URL("http://textures.minecraft.net/texture/" + hash);
                    textures.setSkin(url);
                    profile.setTextures(textures);
                    skull.setOwnerProfile(profile);
                    skull.update(true, false);
                } catch (java.net.MalformedURLException e) {
                    e.printStackTrace();
                }
                growthDisplay(block.getLocation());
            }
        } else if (growthStage == 1) {
            DisplayGroup plantDisplay = getPlantDisplayGroup(block.getLocation());
            if (!hasDisplayPlant(block) || plantDisplay == null) {
                // Missing flag or a lost parent interaction (ghost plant): rebuild the
                // display so the plant stays breakable and interactive.
                addDisplayPlant(block.getLocation());
            } else {
                removeItems(block.getLocation());
            }
            block.setType(Material.AIR);
        } else if (growthStage == 2) {
            DisplayGroup plantDisplay = getPlantDisplayGroup(block.getLocation());
            if (!hasDisplayPlant(block) || plantDisplay == null) {
                addDisplayPlant(block.getLocation());
            }
            ItemStack itemStack = getRandomItemWithDropModifier(block.getLocation());
            if (itemStack != null) {
                nextDrop.put(block.getLocation(), itemStack);
                addItemsToDisplay(block.getLocation(), itemStack.clone());
            }
        }
        BlockStorage.addBlockInfo(block, Keys.FLORA_GROWTH_STAGE, String.valueOf(growthStage));
        // Cambio de estado real: la siguiente pasada revisa el Display sin esperar al intervalo.
        dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler.markDirty(block.getLocation());
    }

    @Nonnull
    @Override
    public RandomizedSet<ItemStack> getHarvestingResults() {
        return this.harvestItems;
    }

    @Nullable
    public ItemStack getRandomItemWithDropModifier(@Nonnull Location location) {
        FloraLevelProfile profile = getLevelProfile(location);
        return getRandomItemWithDropModifier(profile);
    }

    @Nullable
    public ItemStack getRandomItemWithDropModifier(@Nonnull FloraLevelProfile profile) {
        ItemStack itemStack = this.harvestItems.getRandom();

        if (itemStack == null) {
            return null;
        }

        ItemStack clone = itemStack.clone();

        int amount = clone.getAmount();
        int adjustedAmount = getDropAmount(profile.getLevel(), amount);

        clone.setAmount(adjustedAmount);
        return clone;
    }

    public int getDropAmount(int level, int defaultAmount) {
        return (int) defaultAmount + (defaultAmount * (level / 5));
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected boolean validateFlora() {
        if (this.harvestItems.isEmpty()) {
            Cultivation.logWarning(this.getId() + " has no ItemStack(s) for harvesting, it will not be registered.");
            return false;
        }
        return true;
    }
}
