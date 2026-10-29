package dev.sefiraat.cultivation.implementation.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.Registry;
import dev.sefiraat.cultivation.api.datatypes.SeedPackDataType;
import dev.sefiraat.cultivation.api.datatypes.instances.FloraLevelProfile;
import dev.sefiraat.cultivation.api.datatypes.instances.SeedPackInstance;
import dev.sefiraat.cultivation.api.slimefun.items.trees.TreeBlockDescriptor;
import dev.sefiraat.cultivation.implementation.slimefun.tools.SeedPack;
import dev.sefiraat.cultivation.implementation.utils.Keys;
import dev.sefiraat.sefilib.entity.display.DisplayGroup;
import dev.sefiraat.sefilib.string.Theme;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@CommandAlias("cultivation|cv")
public class CultivationCommands extends BaseCommand {

    @Default
    public void onDefault(CommandSender sender) {
        sender.sendMessage(Theme.ERROR + "Please provide a valid subcommand.");
    }

    //TODO apply permissions
    @Subcommand("pos1")
    @CommandPermission("cultivation.admin.structures")
    public void onPos1(CommandSender sender) {
        if (sender instanceof Player player) {
            Registry.getInstance().addPositionOne(player);
            System.out.println("pos1 set");
        } else {
            sender.sendMessage(Theme.applyThemeToString(Theme.WARNING, "Must be executed by a player"));
        }
    }

    @Subcommand("pos2")
    @CommandPermission("cultivation.admin.structures")
    public void onPos2(CommandSender sender) {
        if (sender instanceof Player player) {
            Registry.getInstance().addPositionTwo(player);
            System.out.println("pos2 set");
        } else {
            sender.sendMessage(Theme.applyThemeToString(Theme.WARNING, "Must be executed by a player"));
        }
    }

    @CommandCompletion("name")
    @Subcommand("saveStructure")
    @CommandPermission("cultivation.admin.structures")
    public void saveStructure(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return;
        }
        BlockPosition pos1 = Registry.getInstance().getPositionOne(player);
        BlockPosition pos2 = Registry.getInstance().getPositionTwo(player);
        Block base = player.getTargetBlockExact(20, FluidCollisionMode.NEVER);
        if (pos1 == null || pos2 == null || base == null) {
            return;
            //todo send message
        }

        Map<TreeBlockDescriptor, String> blockDescriptors = new HashMap<>();

        int upperX = Math.max(pos1.getX(), pos2.getX());
        int upperY = Math.max(pos1.getY(), pos2.getY());
        int upperZ = Math.max(pos1.getZ(), pos2.getZ());
        int lowerX = Math.min(pos1.getX(), pos2.getX());
        int lowerY = Math.min(pos1.getY(), pos2.getY());
        int lowerZ = Math.min(pos1.getZ(), pos2.getZ());

        for (int x = lowerX; x <= upperX; x++) {
            for (int z = lowerZ; z <= upperZ; z++) {
                for (int y = lowerY; y <= upperY; y++) {
                    BlockPosition blockPosition = new BlockPosition(player.getWorld(), x, y, z);
                    Block block = blockPosition.getBlock();
                    Material blockMaterial = block.getType();
                    if (isNotAllowed(blockMaterial)) {
                        continue;
                    }
                    String blockName = getBlockId(blockPosition.getBlock());
                    int offsetX = x - base.getX();
                    int offsetY = y - base.getY();
                    int offsetZ = z - base.getZ();
                    TreeBlockDescriptor descriptor = new TreeBlockDescriptor(blockName, offsetX, offsetY, offsetZ);

                    BlockData blockData = block.getBlockData();
                    if (blockData instanceof Orientable orientable) {
                        descriptor.setAxis(orientable.getAxis());
                    }

                    blockDescriptors.put(descriptor, blockName);
                }
            }
        }
        try {
            Gson gson = new Gson().newBuilder().setPrettyPrinting().create();
            JsonObject root = new JsonObject();
            JsonArray structure = new JsonArray();
            for (Map.Entry<TreeBlockDescriptor, String> entry : blockDescriptors.entrySet()) {
                JsonObject blockObject = new JsonObject();
                TreeBlockDescriptor descriptor = entry.getKey();
                blockObject.add("block_id", new JsonPrimitive(descriptor.getName()));
                blockObject.add("x", new JsonPrimitive(descriptor.getX()));
                blockObject.add("y", new JsonPrimitive(descriptor.getY()));
                blockObject.add("z", new JsonPrimitive(descriptor.getZ()));
                if (descriptor.getBlockFace() != null) {
                    blockObject.add("direction", new JsonPrimitive(descriptor.getBlockFace().name()));
                }
                if (descriptor.getAxis() != null) {
                    blockObject.add("axis", new JsonPrimitive(descriptor.getAxis().name()));
                }
                structure.add(blockObject);
            }
            root.add("name", new JsonPrimitive(name));
            root.add("structure", structure);
            String path = Cultivation.getInstance().getDataFolder().getAbsolutePath() + File.separator;
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(path + name + ".json"));
            gson.toJson(root, bufferedWriter);
            bufferedWriter.flush();
            bufferedWriter.close();
        } catch (IOException ioException) {
            Cultivation.logError(ioException.getMessage());
        }
        System.out.println("saved");
    }

    @CommandCompletion("name")
    @Subcommand("loadStructure")
    @CommandPermission("cultivation.admin.structures")
    public void loadStructure(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return;
        }
        File file = new File(Cultivation.getInstance().getDataFolder() + File.separator + name + ".json");
        try {
            JsonReader jsonReader = new JsonReader(new FileReader(file));
            JsonObject jsonObject = new Gson().fromJson(jsonReader, JsonObject.class);
            JsonArray jsonArray = jsonObject.getAsJsonArray("structure");
            Block base = player.getTargetBlockExact(20, FluidCollisionMode.NEVER);

            if (base == null) {
                return;
            }

            for (JsonElement jsonElement : jsonArray.asList()) {
                if (!(jsonElement instanceof JsonObject object)) {
                    continue;
                }

                int x = object.get("x").getAsInt();
                int y = object.get("y").getAsInt();
                int z = object.get("z").getAsInt();
                String blockId = object.get("block_id").getAsString();
                String[] idStrings = blockId.split(":");
                ItemStack itemStack;
                Location location = base.getLocation().clone().add(x, y, z);
                Block block = location.getBlock();
                if (idStrings[0].equals("slimefun")) {
                    SlimefunItem slimefunItem = SlimefunItem.getById(idStrings[1]);
                    if (slimefunItem == null) {
                        continue;
                        //todo better error handling
                    }
                    itemStack = slimefunItem.getItem();
                    Material material = itemStack.getType();
                    block.setType(material, true);
                    BlockStorage.store(block, slimefunItem.getId());
                } else {
                    itemStack = new ItemStack(Material.valueOf(idStrings[1]));
                    Material material = itemStack.getType();
                    block.setType(material);
                }
            }
        } catch (FileNotFoundException exception) {
            Cultivation.logError(exception.getMessage());
        }
    }

    private boolean isNotAllowed(@Nonnull Material material) {
        return material.isAir() || material == Material.WATER || material == Material.LAVA;
    }

    private String getBlockId(Block block) {
        Material blockMaterial = block.getType();
        SlimefunItem slimefunItem = BlockStorage.check(block);
        return slimefunItem == null
               ? "minecraft:" + blockMaterial.name()
               : "slimefun:" + slimefunItem.getId();
    }

    @Subcommand("removeEntities")
    @CommandCompletion("@range:0-10000")
    @CommandPermission("cultivation.admin.entities")
    public void removeDisplayGroups(CommandSender sender, @Optional Integer radius) {
        int removed = 0;
        if (radius != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Theme.applyThemeToString(Theme.WARNING, "A radius can only be used by a player"));
                return;
            }
            for (Entity entity : player.getWorld().getNearbyEntities(
                player.getLocation(),
                radius,
                radius,
                radius,
                this::isCultivationEntity
            )) {
                if (entity instanceof Interaction interaction) {
                    DisplayGroup displayGroup = DisplayGroup.fromInteraction(interaction);
                    if (displayGroup != null) {
                        displayGroup.remove();
                        removed++;
                    }
                } else if (entity instanceof Display display && isCultivationDisplay(display)) {
                    // Solo se borra el display si lleva la etiqueta de Cultivation. El remove()
                    // incondicional que habia aqui borraba cualquier entidad que pasara el filtro.
                    entity.remove();
                    removed++;
                }
            }
            sender.sendMessage(Theme.applyThemeToString(Theme.SUCCESS, "Removed " + removed + " display entit" + (removed == 1 ? "y" : "ies") + " within " + radius + " blocks"));
        } else {
            for (World world : Bukkit.getWorlds()) {
                for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                    DisplayGroup displayGroup = DisplayGroup.fromInteraction(interaction);
                    if (displayGroup != null) {
                        displayGroup.remove();
                        removed++;
                    }
                }
                for (Display display : world.getEntitiesByClass(Display.class)) {
                    if (isCultivationDisplay(display)) {
                        display.remove();
                        removed++;
                    }
                }
            }
            sender.sendMessage(Theme.applyThemeToString(Theme.SUCCESS, "Removed " + removed + " display entit" + (removed == 1 ? "y" : "ies") + " across all worlds"));
        }
    }

    private boolean isCultivationEntity(@Nonnull Entity entity) {
        // Una Interaction solo cuenta si de verdad pertenece a un DisplayGroup de Cultivation.
        // Antes valia cualquiera, y las Interaction las usan tambien los hologramas, los NPC y
        // los displays de otros addons: con un radio grande el comando se llevaba por delante
        // entidades ajenas. La rama sin radio ya filtraba asi; esta se habia quedado atras.
        if (entity instanceof Interaction interaction) {
            return DisplayGroup.fromInteraction(interaction) != null;
        }
        return entity instanceof Display display && isCultivationDisplay(display);
    }

    private boolean isCultivationDisplay(@Nonnull Display display) {
        return display.getPersistentDataContainer().has(Keys.DISPLAY_ENTITY, PersistentDataType.STRING);
    }

    @Subcommand("packpeek")
    public void packPeek(CommandSender sender) {
        if (sender instanceof Player player) {
            ItemStack itemStack = player.getInventory().getItemInMainHand();
            if (itemStack == null || itemStack.getType().isAir()) {
                player.sendMessage(Theme.WARNING.apply("You must be holding a Seed Pack for this"));
                return;
            }

            SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
            if (!(slimefunItem instanceof SeedPack pack)) {
                player.sendMessage(Theme.WARNING.apply("You must be holding a Seed Pack for this"));
                return;
            }

            ItemMeta itemMeta = itemStack.getItemMeta();
            SeedPackInstance instance = PersistentDataAPI.get(itemMeta, SeedPackDataType.KEY, SeedPackDataType.TYPE);
            if (instance == null) {
                player.sendMessage(Theme.WARNING.apply("This pack is empty!"));
                return;
            }

            player.sendMessage("------------------------------------");
            player.sendMessage("Contents");
            player.sendMessage("------------------------------------");
            for (Map.Entry<FloraLevelProfile, Integer> entry : instance.getAmountMap().entrySet()) {
                FloraLevelProfile profile = entry.getKey();
                String neatKey =
                    " Lv: " + profile.getLevel() +
                    " Sp: " + profile.getSpeed() +
                    " St: " + profile.getStrength();
                player.sendMessage(Theme.CLICK_INFO.asTitle(neatKey, entry.getValue()));
            }
        }
    }
}

