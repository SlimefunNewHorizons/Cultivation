package dev.sefiraat.cultivation.implementation.utils;

import dev.sefiraat.cultivation.Cultivation;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Planifica las revisiones de integridad de los Displays de flora.
 *
 * <p>El ciclo de Slimefun entrega cada bloque cargado una vez por pasada. Reconstruir el
 * {@link dev.drake.sefilib.entity.display.DisplayGroup} de cada planta en cada una de esas pasadas
 * costaba una busqueda global de entidad, varias lecturas de PDC y una reemision de metadatos por
 * planta y por segundo. Este planificador conserva la auto-reparacion pero la espacia en el tiempo:
 *
 * <ul>
 *     <li><b>Intervalo:</b> una posicion se revisa como mucho una vez cada
 *     {@code display-repair.interval-ticks}.</li>
 *     <li><b>Cuota por pasada:</b> como mucho {@code display-repair.max-per-tick} revisiones por
 *     tick del servidor, para que una granja grande no concentre el trabajo en un solo tick.</li>
 *     <li><b>Cola por cambios de estado:</b> {@link #markDirty(Location)} salta el intervalo y la
 *     cuota cuando algo cambio de verdad (colocacion, crecimiento, cosecha).</li>
 * </ul>
 */
public final class FloraTickScheduler {

    public static final String CONFIG_INTERVAL = "display-repair.interval-ticks";
    public static final String CONFIG_QUOTA = "display-repair.max-per-tick";

    public static final int DEFAULT_INTERVAL_TICKS = 200;
    public static final int DEFAULT_QUOTA_PER_TICK = 16;

    /** Limite de seguridad del historial: al superarlo se descarta entero y se vuelve a poblar. */
    static final int MAX_TRACKED_LOCATIONS = 50_000;

    private static final Map<Location, Integer> LAST_INSPECTION = new ConcurrentHashMap<>();
    private static final Set<Location> DIRTY = ConcurrentHashMap.newKeySet();

    private static volatile int intervalTicks = DEFAULT_INTERVAL_TICKS;
    private static volatile int quotaPerTick = DEFAULT_QUOTA_PER_TICK;

    private static int currentPassTick = Integer.MIN_VALUE;
    private static int usedThisPass = 0;

    private FloraTickScheduler() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Lee la cadencia desde config.yml. Se llama una sola vez al arrancar el addon; si la
     * configuracion no esta disponible se mantienen los valores por defecto.
     */
    public static void loadSettings() {
        Cultivation instance = Cultivation.getInstance();
        if (instance == null) {
            return;
        }
        setIntervalTicks(instance.getConfig().getInt(CONFIG_INTERVAL, DEFAULT_INTERVAL_TICKS));
        setQuotaPerTick(instance.getConfig().getInt(CONFIG_QUOTA, DEFAULT_QUOTA_PER_TICK));
    }

    public static void setIntervalTicks(int ticks) {
        intervalTicks = Math.max(1, ticks);
    }

    public static void setQuotaPerTick(int quota) {
        quotaPerTick = Math.max(1, quota);
    }

    public static int getIntervalTicks() {
        return intervalTicks;
    }

    public static int getQuotaPerTick() {
        return quotaPerTick;
    }

    /**
     * Encola una posicion para que la proxima pasada la revise sin esperar al intervalo.
     * Se usa cuando el estado de la planta cambio de verdad.
     */
    public static void markDirty(@Nonnull Location location) {
        DIRTY.add(location.clone());
    }

    /** Olvida una posicion cuando la flora se rompe o se invalida. */
    public static void forget(@Nonnull Location location) {
        LAST_INSPECTION.remove(location);
        DIRTY.remove(location);
    }

    /**
     * @return {@code true} si esta posicion debe revisar sus Displays en esta pasada.
     */
    public static boolean shouldInspect(@Nonnull Location location) {
        Integer serverTick = currentServerTick();
        if (serverTick == null) {
            // Sin servidor vivo (pruebas, apagado) no hay cadencia que respetar.
            return true;
        }
        return shouldInspect(location, serverTick);
    }

    /**
     * Variante con el tick inyectado para poder ejercitar la cadencia sin un servidor vivo.
     */
    static synchronized boolean shouldInspect(@Nonnull Location location, int serverTick) {
        if (DIRTY.remove(location)) {
            // Un cambio de estado real no espera turno ni consume cuota: si no se atiende ahora,
            // la planta se queda con un Display desincronizado hasta el siguiente intervalo.
            record(location, serverTick);
            return true;
        }

        if (!isDue(LAST_INSPECTION.get(location), serverTick, intervalTicks)) {
            return false;
        }

        if (serverTick != currentPassTick) {
            currentPassTick = serverTick;
            usedThisPass = 0;
        }
        if (usedThisPass >= quotaPerTick) {
            // Sin cuota en este tick: la posicion sigue vencida y entrara en una pasada posterior.
            return false;
        }
        usedThisPass++;
        record(location, serverTick);
        return true;
    }

    private static void record(@Nonnull Location location, int serverTick) {
        if (LAST_INSPECTION.size() >= MAX_TRACKED_LOCATIONS) {
            // El historial solo es una optimizacion: descartarlo cuesta una pasada extra de
            // revisiones, nunca correccion. Evita crecer sin limite si un mundo se recarga mucho.
            LAST_INSPECTION.clear();
        }
        LAST_INSPECTION.put(location.clone(), serverTick);
    }

    /**
     * Decision pura de cadencia, sin estado global.
     *
     * @param lastInspection El tick de la ultima revision, o {@code null} si nunca se reviso
     * @param serverTick     El tick actual del servidor
     * @param interval       El intervalo minimo entre revisiones
     */
    static boolean isDue(@Nullable Integer lastInspection, int serverTick, int interval) {
        if (lastInspection == null) {
            return true;
        }
        if (serverTick < lastInspection) {
            // El contador de ticks se reinicia con el servidor: no dejes la posicion congelada.
            return true;
        }
        return serverTick - lastInspection >= interval;
    }

    @Nullable
    private static Integer currentServerTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Solo para pruebas: deja el planificador en su estado inicial. */
    static synchronized void reset() {
        LAST_INSPECTION.clear();
        DIRTY.clear();
        currentPassTick = Integer.MIN_VALUE;
        usedThisPass = 0;
        intervalTicks = DEFAULT_INTERVAL_TICKS;
        quotaPerTick = DEFAULT_QUOTA_PER_TICK;
    }
}
