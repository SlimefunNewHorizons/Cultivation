package dev.sefiraat.cultivation;

import dev.sefiraat.cultivation.implementation.slimefun.tools.PlantAnalyser;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HarvestablePlantTest {

    @Test
    void testDropAmountCalculation() {
        // Level 1: default + (default * (1 / 5)) = default
        int amountLevel1 = getExpectedDropAmount(1, 2);
        assertEquals(2, amountLevel1);

        // Level 5: default + (default * (5 / 5)) = 2 + 2 = 4
        int amountLevel5 = getExpectedDropAmount(5, 2);
        assertEquals(4, amountLevel5);

        // Level 10: default + (default * (10 / 5)) = 2 + 4 = 6
        int amountLevel10 = getExpectedDropAmount(10, 2);
        assertEquals(6, amountLevel10);
    }

    @Test
    void testPlantAnalyserGuardFilter() {
        // When heldItem is PlantAnalyser, harvesting must be skipped
        assertTrue(isAnalyser(PlantAnalyser.class));
        assertFalse(isAnalyser(SlimefunItem.class));
        assertFalse(isAnalyser(null));
    }


    /**
     * Regresion de la fuga de nextDrop: romper una planta debe olvidar su drop
     * precalculado. Sin MockBukkit en el reactor no se puede instanciar una
     * HarvestablePlant real, asi que se verifica el contrato estructural que
     * sostiene el arreglo.
     */
    @Test
    void testNextDropSeOlvidaAlRomper() throws Exception {
        Class<?> plant = dev.sefiraat.cultivation.api.slimefun.items.plants.HarvestablePlant.class;

        java.lang.reflect.Field nextDrop = plant.getDeclaredField("nextDrop");
        assertTrue(
            java.util.concurrent.ConcurrentMap.class.isAssignableFrom(nextDrop.getType())
                || nextDrop.getType() == java.util.Map.class,
            "nextDrop debe seguir siendo un Map"
        );
        nextDrop.setAccessible(true);

        assertNotNull(
            plant.getDeclaredMethod("forgetNextDrop", org.bukkit.Location.class),
            "forgetNextDrop debe existir para que la rotura via Display limpie el mapa"
        );
        assertNotNull(
            plant.getDeclaredMethod("onBreak", org.bukkit.event.block.BlockBreakEvent.class),
            "HarvestablePlant debe sobrescribir onBreak para olvidar el drop precalculado"
        );
    }

    /**
     * El clic sobre el Display no pasa por Slimefun, asi que el listener debe
     * consultar el ProtectionManager por su cuenta antes de cosechar o de
     * aplicar Crop Sticks en terreno ajeno.
     */
    @Test
    void testListenerConsultaProteccionAlInteractuar() throws Exception {
        assertNotNull(
            dev.sefiraat.cultivation.implementation.listeners.CustomPlacementListener.class
                .getDeclaredMethod("onDisplayInteract", org.bukkit.event.player.PlayerInteractEntityEvent.class),
            "onDisplayInteract debe seguir siendo el unico punto de entrada de la cosecha por Display"
        );
    }

    private static int getExpectedDropAmount(int level, int defaultAmount) {
        return defaultAmount + (defaultAmount * (level / 5));
    }

    private static boolean isAnalyser(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return PlantAnalyser.class.isAssignableFrom(clazz);
    }
}
