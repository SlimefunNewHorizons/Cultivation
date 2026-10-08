package dev.sefiraat.cultivation;

import dev.sefiraat.cultivation.api.interfaces.CultivationTrimmable;
import dev.sefiraat.cultivation.api.slimefun.items.bushes.CultivationBush;
import dev.sefiraat.cultivation.implementation.slimefun.tools.TrimmingTool;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión para Ticket #213:
 * TrimmingTool lanza NPE al podar arbusto maduro cuando getTrimmingResult es nulo.
 */
class CultivationBushTrimmingTest {

    @Test
    void testCultivationBushOverridesGetTrimmingResult() throws NoSuchMethodException {
        Method bushMethod = CultivationBush.class.getDeclaredMethod("getTrimmingResult");
        assertNotNull(bushMethod, "CultivationBush debe declarar getTrimmingResult()");
        assertEquals(ItemStack.class, bushMethod.getReturnType(), "getTrimmingResult debe devolver ItemStack");

        Method ifaceMethod = CultivationTrimmable.class.getMethod("getTrimmingResult");
        assertNotNull(ifaceMethod, "CultivationTrimmable debe tener getTrimmingResult()");
        assertTrue(CultivationTrimmable.class.isAssignableFrom(CultivationBush.class),
            "CultivationBush debe implementar CultivationTrimmable");
    }

    @Test
    void testTrimmingToolExistsAndInheritsRefillableUseItem() {
        assertTrue(dev.drake.sefilib.slimefun.items.RefillableUseItem.class.isAssignableFrom(TrimmingTool.class),
            "TrimmingTool debe heredar de RefillableUseItem");
    }
}
