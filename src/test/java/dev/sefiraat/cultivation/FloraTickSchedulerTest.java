package dev.sefiraat.cultivation;

import dev.sefiraat.cultivation.implementation.utils.FloraTickScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresión del throttle de ticks de Cultivation (ticket #401).
 *
 * <p>Sin MockBukkit en el reactor no se puede construir un {@code Location} real, así que se
 * ejercita la decisión pura de cadencia y se verifica por reflexión que el planificador conserva
 * las tres piezas que el ticket exige: intervalo, cuota por pasada y cola por cambios de estado.
 */
class FloraTickSchedulerTest {

    @Test
    void unaPosicionNuevaSiempreSeRevisa() throws Exception {
        assertTrue(isDue(null, 0, 200), "sin historial la planta debe repararse en la primera pasada");
    }

    @Test
    void unaPosicionRecienRevisadaEspera() throws Exception {
        assertFalse(isDue(1000, 1001, 200), "revisar cada tick era justo el coste que se elimina");
        assertFalse(isDue(1000, 1199, 200), "el intervalo no debe vencer antes de tiempo");
    }

    @Test
    void elIntervaloVenceExactamenteEnElLimite() throws Exception {
        assertTrue(isDue(1000, 1200, 200), "al cumplirse el intervalo la revisión vuelve a tocar");
        assertTrue(isDue(1000, 5000, 200));
    }

    @Test
    void elReinicioDelContadorDeTicksNoCongelaLaReparacion() throws Exception {
        // Bukkit.getCurrentTick() vuelve a cero al reiniciar: sin esta guarda las posiciones
        // vistas antes del reinicio nunca volverían a repararse.
        assertTrue(isDue(500_000, 5, 200));
    }

    @Test
    void losValoresConfigurablesSeSaneanAPositivos() {
        FloraTickScheduler.setIntervalTicks(0);
        assertEquals(1, FloraTickScheduler.getIntervalTicks(), "un intervalo de 0 dejaría el throttle inerte");
        FloraTickScheduler.setQuotaPerTick(-5);
        assertEquals(1, FloraTickScheduler.getQuotaPerTick(), "una cuota negativa bloquearía toda reparación");

        FloraTickScheduler.setIntervalTicks(FloraTickScheduler.DEFAULT_INTERVAL_TICKS);
        FloraTickScheduler.setQuotaPerTick(FloraTickScheduler.DEFAULT_QUOTA_PER_TICK);
        assertEquals(FloraTickScheduler.DEFAULT_INTERVAL_TICKS, FloraTickScheduler.getIntervalTicks());
        assertEquals(FloraTickScheduler.DEFAULT_QUOTA_PER_TICK, FloraTickScheduler.getQuotaPerTick());
    }

    @Test
    void elPlanificadorConservaLaColaPorCambiosDeEstado() throws Exception {
        Class<?> scheduler = FloraTickScheduler.class;
        assertNotNull(
            scheduler.getDeclaredMethod("markDirty", org.bukkit.Location.class),
            "markDirty es la vía por la que colocar, crecer o cosechar salta el intervalo"
        );
        assertNotNull(
            scheduler.getDeclaredMethod("forget", org.bukkit.Location.class),
            "forget evita que el historial retenga posiciones ya rotas"
        );
        assertNotNull(
            scheduler.getDeclaredMethod("shouldInspect", org.bukkit.Location.class),
            "shouldInspect es el único punto de entrada del throttle"
        );
    }

    @Test
    void elTickerSigueLeyendoElEstadoDelConfigDeBlockStorage() throws Exception {
        // La ruta caliente no debe volver a BlockStorage: onTickAlways recibe el Config del bloque.
        assertNotNull(
            dev.sefiraat.cultivation.api.interfaces.CultivationPlantHolder.class
                .getDeclaredMethod("hasDisplayPlant", me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config.class),
            "hasDisplayPlant(Config) es la lectura barata que usa el tick"
        );
        assertNotNull(
            dev.sefiraat.cultivation.api.interfaces.CultivationBushHolder.class
                .getDeclaredMethod("hasDisplayBush", me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config.class),
            "hasDisplayBush(Config) es la lectura barata que usa el tick"
        );
    }

    @Test
    void elPerfilDeNivelSeCacheaEnUnMapaConcurrente() throws Exception {
        java.lang.reflect.Field field = dev.sefiraat.cultivation.api.interfaces.CultivationLevelProfileHolder.class
            .getDeclaredField("PROFILE_MAP");
        Object value = field.get(null);
        assertTrue(
            value instanceof java.util.concurrent.ConcurrentMap,
            "PROFILE_MAP se escribe desde el ticker y se lee desde listeners: debe ser concurrente"
        );
    }

    private static boolean isDue(Integer lastInspection, int serverTick, int interval) throws Exception {
        Method method = FloraTickScheduler.class
            .getDeclaredMethod("isDue", Integer.class, int.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, lastInspection, serverTick, interval);
    }
}
