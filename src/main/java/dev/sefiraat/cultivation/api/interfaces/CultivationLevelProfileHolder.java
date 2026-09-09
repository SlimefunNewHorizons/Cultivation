package dev.sefiraat.cultivation.api.interfaces;

import dev.sefiraat.cultivation.api.datatypes.instances.FloraLevelProfile;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNullableByDefault;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface CultivationLevelProfileHolder {

    /** Escrito desde el ticker y leido desde comandos y listeners: debe ser concurrente. */
    Map<Location, FloraLevelProfile> PROFILE_MAP = new ConcurrentHashMap<>();

    @Nonnull
    default FloraLevelProfile getLevelProfile(@Nonnull Block block) {
        return getLevelProfile(block.getLocation());
    }

    @Nonnull
    default FloraLevelProfile getLevelProfile(@Nonnull Location location) {
        FloraLevelProfile cached = PROFILE_MAP.get(location);
        if (cached != null) {
            return cached;
        }
        // Cuatro lecturas de BlockStorage por planta y por tick era el coste real de calcular la
        // tasa de crecimiento. El perfil solo cambia por setLevelProfile, que refresca este mapa,
        // asi que memorizar la lectura es seguro y deja el tick en una consulta de mapa.
        String levelString = BlockStorage.getLocationInfo(location, FloraLevelProfile.BS_KEY_LEVEL);
        String speedString = BlockStorage.getLocationInfo(location, FloraLevelProfile.BS_KEY_SPEED);
        String strengthString = BlockStorage.getLocationInfo(location, FloraLevelProfile.BS_KEY_STRENGTH);
        String analysedString = BlockStorage.getLocationInfo(location, FloraLevelProfile.BS_KEY_ANALYZED);
        FloraLevelProfile profile = getLevelProfile(levelString, speedString, strengthString, analysedString);
        PROFILE_MAP.put(location.clone(), profile);
        return profile;
    }

    @Nonnull
    default FloraLevelProfile getLevelProfile(@Nonnull Config config) {
        String levelString = config.getString(FloraLevelProfile.BS_KEY_LEVEL);
        String speedString = config.getString(FloraLevelProfile.BS_KEY_SPEED);
        String strengthString = config.getString(FloraLevelProfile.BS_KEY_STRENGTH);
        String analyzedString = config.getString(FloraLevelProfile.BS_KEY_ANALYZED);
        return getLevelProfile(levelString, speedString, strengthString, analyzedString);
    }

    @ParametersAreNullableByDefault
    default FloraLevelProfile getLevelProfile(String levelString,
                                              String speedString,
                                              String strengthString,
                                              String analysedString
    ) {
        int level = parsePositiveValue(levelString);
        int speed = parsePositiveValue(speedString);
        int strength = parsePositiveValue(strengthString);
        boolean analyzed = Boolean.parseBoolean(analysedString);
        return new FloraLevelProfile(level, speed, strength, analyzed);
    }

    private static int parsePositiveValue(String value) {
        if (value == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    default void setLevelProfile(@Nonnull Location location, FloraLevelProfile profile) {
        setLevelProfile(location, profile.getLevel(), profile.getSpeed(), profile.getStrength(), profile.isAnalyzed());
        PROFILE_MAP.put(location, profile);
    }

    default void removeLevelProfile(@Nonnull Location location) {
        PROFILE_MAP.remove(location);
    }

    default void setLevelProfile(@Nonnull Location location, int level, int speed, int strength, boolean analyzed) {
        BlockStorage.addBlockInfo(location, FloraLevelProfile.BS_KEY_LEVEL, String.valueOf(level));
        BlockStorage.addBlockInfo(location, FloraLevelProfile.BS_KEY_SPEED, String.valueOf(speed));
        BlockStorage.addBlockInfo(location, FloraLevelProfile.BS_KEY_STRENGTH, String.valueOf(strength));
        BlockStorage.addBlockInfo(location, FloraLevelProfile.BS_KEY_ANALYZED, String.valueOf(analyzed));
    }
}
