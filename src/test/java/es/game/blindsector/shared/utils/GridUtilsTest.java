package es.game.blindsector.shared.utils;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.game.domain.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridUtilsTest {

    // ─── isInBounds ──────────────────────────────────────────────────────────

    @Test
    void isInBounds_celdaEsquinaSupIzq_true() {
        assertTrue(GridUtils.isInBounds(0, 0));
    }

    @Test
    void isInBounds_celdaEsquinaInfDer_true() {
        assertTrue(GridUtils.isInBounds(14, 14));
    }

    @Test
    void isInBounds_celdaFueraTableroNegativo_false() {
        assertFalse(GridUtils.isInBounds(-1, 0));
        assertFalse(GridUtils.isInBounds(0, -1));
    }

    @Test
    void isInBounds_celdaFueraTableroPositivo_false() {
        assertFalse(GridUtils.isInBounds(15, 0));
        assertFalse(GridUtils.isInBounds(0, 15));
    }

    @Test
    void isInBounds_celdaCentro_true() {
        assertTrue(GridUtils.isInBounds(7, 7));
    }

    // ─── chebyshevDistance ───────────────────────────────────────────────────

    @Test
    void chebyshevDistance_mismaCelda_cero() {
        assertEquals(0, GridUtils.chebyshevDistance(5, 5, 5, 5));
    }

    @Test
    void chebyshevDistance_distanciaRecta_horizontal() {
        assertEquals(3, GridUtils.chebyshevDistance(2, 5, 5, 5));
    }

    @Test
    void chebyshevDistance_distanciaRecta_vertical() {
        assertEquals(4, GridUtils.chebyshevDistance(5, 1, 5, 5));
    }

    @Test
    void chebyshevDistance_distanciaDiagonal_exacta() {
        // Diagonal (3,3) → (6,6): max(3,3) = 3
        assertEquals(3, GridUtils.chebyshevDistance(3, 3, 6, 6));
    }

    @Test
    void chebyshevDistance_distanciaDiagonal_asimetrica() {
        // (0,0) → (3,5): max(3,5) = 5
        assertEquals(5, GridUtils.chebyshevDistance(0, 0, 3, 5));
    }

    // ─── resolveRegion ───────────────────────────────────────────────────────

    @Test
    void resolveRegion_cuadrante_A1() {
        // Celdas (0-4, 0-4) → región (0,0) → "A1"
        Region r = GridUtils.resolveRegion(0, 0);
        assertEquals("A1", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_B1() {
        // Celdas (5-9, 0-4) → región (1,0) → "B1"
        Region r = GridUtils.resolveRegion(5, 0);
        assertEquals("B1", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_C1() {
        Region r = GridUtils.resolveRegion(14, 0);
        assertEquals("C1", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_A2() {
        Region r = GridUtils.resolveRegion(0, 5);
        assertEquals("A2", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_B2_centro() {
        // Centro exacto del tablero (7,7) → región (1,1) → "B2"
        Region r = GridUtils.resolveRegion(7, 7);
        assertEquals("B2", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_C2() {
        Region r = GridUtils.resolveRegion(14, 7);
        assertEquals("C2", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_A3() {
        Region r = GridUtils.resolveRegion(0, 14);
        assertEquals("A3", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_B3() {
        Region r = GridUtils.resolveRegion(7, 14);
        assertEquals("B3", r.toLabel());
    }

    @Test
    void resolveRegion_cuadrante_C3() {
        Region r = GridUtils.resolveRegion(14, 14);
        assertEquals("C3", r.toLabel());
    }

    // ─── computeImpactArea ───────────────────────────────────────────────────

    @Test
    void computeImpactArea_centroCentral_nueveCeldas() {
        ImpactArea area = GridUtils.computeImpactArea(7, 7);
        assertEquals(9, area.getPositions().size());
    }

    @Test
    void computeImpactArea_centroCentral_contieneAdyacentes() {
        ImpactArea area = GridUtils.computeImpactArea(7, 7);
        assertTrue(area.contains(new Position(6, 6)));
        assertTrue(area.contains(new Position(7, 7)));
        assertTrue(area.contains(new Position(8, 8)));
        assertTrue(area.contains(new Position(7, 6)));
        assertTrue(area.contains(new Position(8, 7)));
    }

    @Test
    void computeImpactArea_esquinaSupIzq_cuatroCeldas() {
        // Esquina (0,0): solo celdas (0,0),(1,0),(0,1),(1,1)
        ImpactArea area = GridUtils.computeImpactArea(0, 0);
        assertEquals(4, area.getPositions().size());
        assertTrue(area.contains(new Position(0, 0)));
        assertTrue(area.contains(new Position(1, 0)));
        assertTrue(area.contains(new Position(0, 1)));
        assertTrue(area.contains(new Position(1, 1)));
    }

    @Test
    void computeImpactArea_borde_seisCeldas() {
        // Borde superior centro (7,0): fila -1 fuera → 6 celdas
        ImpactArea area = GridUtils.computeImpactArea(7, 0);
        assertEquals(6, area.getPositions().size());
    }

    @Test
    void computeImpactArea_esquinaInfDer_cuatroCeldas() {
        ImpactArea area = GridUtils.computeImpactArea(14, 14);
        assertEquals(4, area.getPositions().size());
        assertTrue(area.contains(new Position(13, 13)));
        assertTrue(area.contains(new Position(14, 13)));
        assertTrue(area.contains(new Position(13, 14)));
        assertTrue(area.contains(new Position(14, 14)));
    }

    @Test
    void computeImpactArea_noContienePosFuera() {
        ImpactArea area = GridUtils.computeImpactArea(0, 0);
        assertFalse(area.contains(new Position(-1, 0)));
        assertFalse(area.contains(new Position(0, -1)));
    }
}
