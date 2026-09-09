package dev.sefiraat.cultivation.api.slimefun.items.plants;

import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.Registry;
import dev.sefiraat.cultivation.api.datatypes.FloraLevelProfileDataType;
import dev.sefiraat.cultivation.api.datatypes.instances.FloraLevelProfile;
import dev.sefiraat.cultivation.api.interfaces.CultivationCroppable;
import dev.sefiraat.cultivation.api.interfaces.CultivationFlora;
import dev.sefiraat.cultivation.api.interfaces.CultivationLevelProfileHolder;
import dev.sefiraat.cultivation.api.interfaces.CultivationPlantHolder;
import dev.sefiraat.cultivation.api.slimefun.RecipeTypes;
import dev.sefiraat.cultivation.api.slimefun.groups.CultivationGroups;
import dev.sefiraat.cultivation.api.slimefun.items.CultivationFloraItem;
import dev.sefiraat.cultivation.api.slimefun.plant.BreedResult;
import dev.sefiraat.cultivation.api.slimefun.plant.BreedingPair;
import dev.sefiraat.cultivation.api.slimefun.plant.Growth;
import dev.sefiraat.cultivation.api.slimefun.plant.PlantTheme;
import dev.sefiraat.cultivation.api.utils.LevelType;
import dev.sefiraat.cultivation.api.utils.StatisticUtils;
import dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler;
import dev.sefiraat.cultivation.implementation.utils.Keys;
import dev.drake.sefilib.entity.display.DisplayInteractable;
import dev.drake.sefilib.misc.ParticleUtils;
import dev.drake.sefilib.string.Theme;
import dev.drake.sefilib.world.LocationUtils;
import io.github.bakedlibs.dough.data.persistent.PersistentDataAPI;
import com.github.drakescraft_labs.slimefun4.api.SlimefunAddon;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockBreakHandler;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import dev.sefiraat.cultivation.implementation.slimefun.CultivationStacks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is used to define a CultivationPlant that will grow as a
 * {@link CultivationFlora}
 */
public abstract class CultivationPlant extends CultivationFloraItem<CultivationPlant>
        implements CultivationFlora, CultivationLevelProfileHolder, CultivationCroppable, CultivationPlantHolder,
        DisplayInteractable {

    private static final long INVALID_BREED_NOTICE_COOLDOWN_MILLIS = 300_000L;
    private static final ConcurrentHashMap<InvalidBreedNoticeKey, Long> INVALID_BREED_NOTICE_AT = new ConcurrentHashMap<>();

    @Nonnull
    public static final Set<BlockFace> BREEDING_DIRECTIONS = Set.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST);

    @Nonnull
    protected Set<BreedingPair> breedingPairs = new HashSet<>();

    @ParametersAreNonnullByDefault
    protected CultivationPlant(SlimefunItemStack item, Growth growth) {
        this(item, RecipeTypes.PLANT_BREEDING, new ItemStack[0], growth);
    }

    @ParametersAreNonnullByDefault
    protected CultivationPlant(SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            Growth growth) {
        this(item, recipeType, recipe, growth, null);
    }

    @ParametersAreNonnullByDefault
    protected CultivationPlant(SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            Growth growth,
            @Nullable ItemStack recipeOutput) {
        super(CultivationGroups.PLANTS, item, recipeType, recipe, recipeOutput, growth);
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(
                new BlockBreakHandler(false, false) {
                    @Override
                    @ParametersAreNonnullByDefault
                    public void onPlayerBreak(BlockBreakEvent blockBreakEvent, ItemStack itemStack,
                            List<ItemStack> list) {
                        onBreak(blockBreakEvent);
                    }
                });
    }

    @Override
    public void onTickAlways(Location location, SlimefunItem flora, Config data) {
        // Soporte roto: solo AIR-display (stage>=1); stage0 PLAYER_HEAD lo maneja Slimefun SENSITIVE
        try {
            Location blockLoc = location.getBlock().getLocation();
            if (blockLoc.getBlock().getType() != Material.AIR) return;
            if (!blockLoc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
                if (!dev.sefiraat.cultivation.implementation.listeners.CustomPlacementListener.tryAcquireRemoval(blockLoc)) return;
                Bukkit.getScheduler().runTask(Cultivation.getInstance(), () -> {
                    try {
                        if (BlockStorage.check(blockLoc) instanceof CultivationPlant still && !blockLoc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
                            Location loc = blockLoc;
                            loc.getWorld().dropItem(loc.clone().add(0.5, 0.5, 0.5), getDroppedItemStack(loc));
                            removeCropped(loc);
                            removePlantDisplayGroup(loc);
                            removeLevelProfile(loc);
                            removeOwner(loc);
                            FloraTickScheduler.forget(loc);
                            BlockStorage.clearBlockInfo(loc);
                            loc.getBlock().setType(Material.AIR);
                        }
                    } finally {
                        dev.sefiraat.cultivation.implementation.listeners.CustomPlacementListener.releaseRemoval(blockLoc);
                    }
                });
                return;
            }
        } catch (Exception ignored) {}
        // Auto-reparación de ghosts: plantas en stage AIR sin Interaction (pre-62f791e) quedaban irrompibles.
        // La reparación sigue siendo automática, pero eventual: FloraTickScheduler la espacia por
        // posición y la acota por tick para que una granja grande no reconstruya un DisplayGroup
        // completo por planta y por segundo en el hilo principal.
        try {
            String stageStr = data.getString(Keys.FLORA_GROWTH_STAGE);
            if (stageStr == null) {
                return;
            }
            int stage = Integer.parseInt(stageStr);
            if (stage < 1 || !FloraTickScheduler.shouldInspect(location)) {
                return;
            }
            // Dentro de la ventana se conserva la comprobación completa de siempre: el grupo
            // puede haber perdido su lista de hijos aunque el Interaction padre siga vivo.
            var group = getPlantDisplayGroup(location);
            if (hasDisplayPlant(data) && group != null) {
                return;
            }
            addDisplayPlant(location);
            if (stage >= getMaxGrowthStages() && flora instanceof HarvestablePlant hp) {
                ItemStack itemStack = hp.getRandomItemWithDropModifier(location);
                if (itemStack != null) {
                    addItemsToDisplay(location, itemStack.clone());
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void tryBreed(@Nonnull Block motherBlock, @Nonnull CultivationPlant plant) {
        double breedChance = ThreadLocalRandom.current().nextDouble();
        if (breedChance > getDefaultGrowthRate()) {
            // No breed attempted this tick
            return;
        }

        for (BlockFace face : BREEDING_DIRECTIONS) {
            Block middleBlock = motherBlock.getRelative(face);
            SlimefunItem middleItem = BlockStorage.check(middleBlock);
            boolean isAir = middleBlock.getType() == Material.AIR;
            boolean isPlaceholder = middleItem instanceof NothingPlant;
            boolean isUnoccupiedStick = middleItem instanceof CultivationCroppable && !isMature(middleBlock);

            if (!isAir && !isPlaceholder && !isUnoccupiedStick) {
                continue;
            }
            Block potentialMate = middleBlock.getRelative(face);
            SlimefunItem mateItem = BlockStorage.check(potentialMate);

            if (mateItem instanceof CultivationPlant mate) {
                testBreed(plant, mate, middleBlock, motherBlock, potentialMate);
            }
        }
    }

    @Override
    public void whenPlaced(@NotNull BlockPlaceEvent event) {
        super.whenPlaced(event);

        Location location = event.getBlock().getLocation();
        ItemStack itemStack = event.getItemInHand();
        ItemMeta itemMeta = itemStack.getItemMeta();

        FloraLevelProfile profile = PersistentDataAPI.get(
                itemMeta,
                FloraLevelProfileDataType.KEY,
                FloraLevelProfileDataType.TYPE,
                new FloraLevelProfile(1, 1, 1, false));

        setLevelProfile(location, profile);
        PROFILE_MAP.put(location, profile);
        FloraTickScheduler.markDirty(location);
    }

    @OverridingMethodsMustInvokeSuper
    public void onBreak(@NotNull BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        ItemStack itemToDrop = getDroppedItemStack(location);
        boolean wasCropped = isCropped(location);
        boolean wasCrossCropped = isCrossCropped(location);
        removeCropped(location);
        removePlant(location);
        Location dropLoc = location.clone().add(0.5, 0.5, 0.5);
        if (itemToDrop != null && itemToDrop.getType() != Material.AIR) {
            location.getWorld().dropItem(dropLoc, itemToDrop);
        }
        if (wasCrossCropped) {
            ItemStack sticks = CultivationStacks.CROP_STICKS.clone();
            sticks.setAmount(2);
            location.getWorld().dropItem(dropLoc, sticks);
        } else if (wasCropped) {
            location.getWorld().dropItem(dropLoc, CultivationStacks.CROP_STICKS.clone());
        }
        removeLevelProfile(location);
        removeOwner(location);
        FloraTickScheduler.forget(location);
        event.setDropItems(false);
    }

    public ItemStack getDroppedItemStack(@Nonnull Location location) {
        return getStack(this, getLevelProfile(location));
    }

    @Override
    @ParametersAreNonnullByDefault
    protected boolean canGrow(Block block, CultivationPlant flora, Config data, Location location, int growthStage) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        return getPlacements().contains(blockBelow.getType()) || isCropped(data);
    }

    @Override
    public double getGrowthRate(@Nonnull Location location) {
        FloraLevelProfile profile = getLevelProfile(location);
        if (profile != null && profile.getLevel() > 1) {
            return getDefaultGrowthRate() * profile.getLevel();
        }
        return getDefaultGrowthRate();
    }

    public double getGrowthRate(@Nonnull FloraLevelProfile profile) {
        return getDefaultGrowthRate() * profile.getLevel();
    }

    @ParametersAreNonnullByDefault
    private void testBreed(CultivationPlant mother,
            CultivationPlant mate,
            Block middleBlock,
            Block motherBlock,
            Block fatherBlock) {
        BreedResult result = Registry.getInstance().getBreedResult(mother.getId(), mate.getId());

        if (!isMature(motherBlock)
                || !isMature(fatherBlock)
                || !isCrossCropped(motherBlock)
                || !isCrossCropped(fatherBlock)) {
            return;
        }

        switch (result.getResultType()) {
            case NO_PAIRS ->
            {
                // A mature pair is retried often. Notify its owner once per pair, not every growth tick.
                if (notifyInvalidBreed(mother, mate, motherBlock.getLocation())) {
                    breedInvalidDisplay(middleBlock.getLocation());
                }
            }
            case SUCCESS -> {
                // Breed was a success - spawn child, log discovery
                CultivationPlant child = result.getMatchedPair().getChild();
                trySetChildSeed(motherBlock.getLocation(), middleBlock, child);
                StatisticUtils.incrementExp(getOwner(motherBlock.getLocation()), LevelType.HORTICULTURALIST, 2);
            }
            case SPREAD_NO_MUTATE -> {
                // Breed failed, spread success - spawn copy of mother
                trySetChildSeed(motherBlock.getLocation(), middleBlock, mother);
                StatisticUtils.incrementExp(getOwner(motherBlock.getLocation()), LevelType.HORTICULTURALIST, 1);
            }
            case SPREAD_MUTATE -> {
                // Breed not possible, but mutation possible.
                FloraLevelProfile motherProfile = getLevelProfile(motherBlock.getLocation());
                FloraLevelProfile fatherProfile = getLevelProfile(fatherBlock.getLocation());
                trySetChildSeed(motherBlock.getLocation(), middleBlock, mother);
                tryMutate(middleBlock, motherProfile, fatherProfile);
                StatisticUtils.incrementExp(getOwner(motherBlock.getLocation()), LevelType.HORTICULTURALIST, 1);
            }
        }
    }

    private void breedInvalidDisplay(@Nonnull Location location) {
        ParticleUtils.displayParticleRandomly(
                LocationUtils.centre(location),
                0.5,
                2,
                new Particle.DustOptions(Color.BLACK, 1));
    }

    /**
     * Explains black particles without exposing recipe internals or repeatedly spamming the owner.
     */
    private boolean notifyInvalidBreed(@Nonnull CultivationPlant mother,
            @Nonnull CultivationPlant mate,
            @Nonnull Location ownerLocation) {
        UUID owner = getOwner(ownerLocation);
        if (owner == null) {
            return false;
        }

        String first = mother.getId().compareTo(mate.getId()) <= 0 ? mother.getId() : mate.getId();
        String second = first.equals(mother.getId()) ? mate.getId() : mother.getId();
        InvalidBreedNoticeKey key = new InvalidBreedNoticeKey(owner, first, second);
        long now = System.currentTimeMillis();
        Long previousNotice = INVALID_BREED_NOTICE_AT.put(key, now);
        if (previousNotice != null && now - previousNotice < INVALID_BREED_NOTICE_COOLDOWN_MILLIS) {
            INVALID_BREED_NOTICE_AT.put(key, previousNotice);
            return false;
        }

        Player player = Bukkit.getPlayer(owner);
        if (player != null && player.isOnline()) {
            player.sendActionBar(Theme.WARNING.apply(
                    "Sin receta: " + mother.getItemName() + " + " + mate.getItemName() + ". Revisa el Diccionario de Cruces."));
            return true;
        }
        return false;
    }

    /** Separates repeated invalid pairs without suppressing feedback for a different experiment. */
    private record InvalidBreedNoticeKey(UUID owner, String firstPlantId, String secondPlantId) {
    }

    @ParametersAreNonnullByDefault
    private void trySetChildSeed(Location motherLocation, Block cloneBlock, CultivationPlant childSeed) {
        // Breeding data may reference an orphaned plant after an addon reload or a
        // removed registry entry. Abort this reproduction instead of breaking the
        // block interaction event with a NullPointerException.
        if (motherLocation == null || cloneBlock == null || childSeed == null || childSeed.growth == null) {
            org.bukkit.Bukkit.getLogger().warning("[Cultivation] Omitida reproducción con datos incompletos");
            return;
        }

        PlantTheme theme = childSeed.growth.getTheme();

        if (theme == null) {
            return;
        }

        UUID owner = getOwner(motherLocation);
        if (owner == null) {
            org.bukkit.Bukkit.getLogger().warning("[Cultivation] Omitida reproducción sin propietario: "
                    + motherLocation);
            return;
        }

        cloneBlock.setType(Material.PLAYER_HEAD);

        // Use native Bukkit API instead of PlayerHead.setSkin() which doesn't work in
        // 1.20.6
        org.bukkit.block.Skull skull = (org.bukkit.block.Skull) cloneBlock.getState();
        org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(java.util.UUID.randomUUID());
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
        BlockStorage.store(cloneBlock, childSeed.getId());
        BlockStorage.addBlockInfo(cloneBlock, Keys.FLORA_GROWTH_STAGE, "0");
        BlockStorage.addBlockInfo(cloneBlock, Keys.FLORA_OWNER, owner.toString());
        StatisticUtils.unlockDiscovery(owner, childSeed.getId());
        breedSuccess(cloneBlock.getLocation());
    }

    @ParametersAreNonnullByDefault
    private void tryMutate(Block cloneBlock, FloraLevelProfile motherProfile, FloraLevelProfile fatherProfile) {
        FloraLevelProfile profile = FloraLevelProfile.testMutation(motherProfile, fatherProfile);
        setLevelProfile(cloneBlock.getLocation(), profile);
    }

    protected void breedSuccess(@Nonnull Location location) {
        ParticleUtils.displayParticleRandomly(
                LocationUtils.centre(location),
                0.5,
                4,
                new Particle.DustOptions(org.bukkit.Color.LIME, 1));
    }

    /**
     * Adds a possible BreedingPair that will result in this seed as a child.
     * Can have multiple pairs resulting in the same child.
     *
     * @param mother       The ID of the potential Mother
     * @param father       The ID of the potential Mother
     * @param breedChance  The chance for the breed to return this plant
     * @param spreadChance The chance that the Mother will spread
     * @return Returns self
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public CultivationPlant addBreedingPair(String mother, String father, double breedChance, double spreadChance) {
        this.breedingPairs.add(new BreedingPair(this, mother, father, breedChance, spreadChance));
        return this;
    }

    /**
     * Gets all the possible ways this plant can be bred
     *
     * @return The {@link Set} of {@link BreedingPair}s this plant can be bred from
     */
    @Nonnull
    public Set<BreedingPair> getBreedingPairs() {
        return this.breedingPairs;
    }

    @Override
    public int getMaxGrowthStages() {
        return 2;
    }

    @Override
    public CultivationPlant tryRegister(@NotNull SlimefunAddon addon) {
        Registry.getInstance().addPlant(this);
        return super.tryRegister(addon);
    }

    public static ItemStack getStack(@Nonnull CultivationPlant plant, @Nonnull FloraLevelProfile profile) {
        ItemStack itemToDrop = plant.getItem().clone();
        ItemMeta itemMeta = itemToDrop.getItemMeta();

        PersistentDataAPI.set(
                itemMeta,
                FloraLevelProfileDataType.KEY,
                FloraLevelProfileDataType.TYPE,
                profile);

        if (profile.isAnalyzed()) {
            List<String> lore = itemMeta.getLore();
            lore.add("");
            lore.add(Theme.CLICK_INFO.asTitle("Drop Level", profile.getLevel()));
            lore.add(Theme.CLICK_INFO.asTitle("Speed", profile.getSpeed()));
            lore.add(Theme.CLICK_INFO.asTitle("Breed Strength", profile.getStrength()));
            itemMeta.setLore(lore);
        }

        itemToDrop.setItemMeta(itemMeta);
        return itemToDrop;
    }
}
