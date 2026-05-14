package es.game.blindsector.game.engine;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.HitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ImpactResolverTest {

    private ImpactResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ImpactResolver();
    }

    // ------------------------------------------------------------------ //
    //  resolveImpact — resultados de hit                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("resolveImpact — detección de impacto")
    class ResolveImpact {

        @Test
        @DisplayName("DIRECT_HIT cuando el target está en la celda central exacta")
        void directHitEnCentroExacto() {
            PlayerState target = new PlayerState("p2", 7, 7);
            HitResult result = resolver.resolveImpact(7, 7, target);
            assertThat(result).isEqualTo(HitResult.DIRECT_HIT);
        }

        @Test
        @DisplayName("HIT cuando el target está en el borde del área 3×3")
        void hitEnBordeDelArea() {
            // Ataque centrado en (7,7), target en (8,8) — borde diagonal del área
            PlayerState target = new PlayerState("p2", 8, 8);
            HitResult result = resolver.resolveImpact(7, 7, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }

        @Test
        @DisplayName("HIT cuando el target está en celda adyacente horizontal")
        void hitAdyacenteHorizontal() {
            PlayerState target = new PlayerState("p2", 9, 7);
            HitResult result = resolver.resolveImpact(8, 7, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }

        @Test
        @DisplayName("HIT cuando el target está en celda adyacente vertical")
        void hitAdyacenteVertical() {
            PlayerState target = new PlayerState("p2", 7, 6);
            HitResult result = resolver.resolveImpact(7, 7, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }

        @Test
        @DisplayName("MISS cuando el target está fuera del área 3×3")
        void missTargetFueraDelArea() {
            // Ataque en (7,7), target en (10,10) — fuera del 3×3
            PlayerState target = new PlayerState("p2", 10, 10);
            HitResult result = resolver.resolveImpact(7, 7, target);
            assertThat(result).isEqualTo(HitResult.MISS);
        }

        @Test
        @DisplayName("MISS cuando el target está exactamente 2 celdas fuera del centro")
        void missTargetDosCeldasFuera() {
            PlayerState target = new PlayerState("p2", 9, 9);
            HitResult result = resolver.resolveImpact(7, 7, target);
            assertThat(result).isEqualTo(HitResult.MISS);
        }

        @Test
        @DisplayName("DIRECT_HIT tiene prioridad sobre HIT — target en centro")
        void directHitTienePrioridadSobreHit() {
            // El centro está dentro del área; debe ser DIRECT_HIT, no HIT
            PlayerState target = new PlayerState("p2", 5, 5);
            HitResult result = resolver.resolveImpact(5, 5, target);
            assertThat(result).isEqualTo(HitResult.DIRECT_HIT);
        }
    }

    // ------------------------------------------------------------------ //
    //  resolveImpact — ataques en bordes y esquinas del tablero           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Ataques en bordes y esquinas del tablero")
    class AtaquesEnBordes {

        @Test
        @DisplayName("Ataque en esquina (0,0) — HIT cuando el target está en (1,1)")
        void hitEnEsquinaSupIzq() {
            PlayerState target = new PlayerState("p2", 1, 1);
            HitResult result = resolver.resolveImpact(0, 0, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }

        @Test
        @DisplayName("Ataque en esquina (0,0) — DIRECT_HIT cuando el target está en (0,0)")
        void directHitEnEsquinaSupIzq() {
            PlayerState target = new PlayerState("p2", 0, 0);
            HitResult result = resolver.resolveImpact(0, 0, target);
            assertThat(result).isEqualTo(HitResult.DIRECT_HIT);
        }

        @Test
        @DisplayName("Ataque en esquina (14,14) — HIT cuando el target está en (13,13)")
        void hitEnEsquinaInfDer() {
            PlayerState target = new PlayerState("p2", 13, 13);
            HitResult result = resolver.resolveImpact(14, 14, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }

        @Test
        @DisplayName("Ataque en borde lateral (0,7) — MISS para target fuera del área recortada")
        void missEnBordeLateral() {
            // El área de (0,7) cubre cols 0..1 y rows 6..8 — la col -1 no existe
            PlayerState target = new PlayerState("p2", 5, 7);
            HitResult result = resolver.resolveImpact(0, 7, target);
            assertThat(result).isEqualTo(HitResult.MISS);
        }

        @Test
        @DisplayName("Ataque en borde superior (7,0) — HIT en celda válida del área recortada")
        void hitEnBordeSuperior() {
            // Área de (7,0): rows 0..1 (la fila -1 no existe), cols 6..8
            PlayerState target = new PlayerState("p2", 8, 1);
            HitResult result = resolver.resolveImpact(7, 0, target);
            assertThat(result).isEqualTo(HitResult.HIT);
        }
    }

    // ------------------------------------------------------------------ //
    //  computeImpactArea — número de celdas generadas                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("computeImpactArea — tamaño del área según posición")
    class ComputeImpactArea {

        @Test
        @DisplayName("Centro del tablero produce exactamente 9 celdas")
        void centroProduceNueveCeldas() {
            ImpactArea area = resolver.computeImpactArea(7, 7);
            assertThat(area.getPositions()).hasSize(9);
        }

        @Test
        @DisplayName("Esquina (0,0) produce exactamente 4 celdas")
        void esquinaProduceCuatroCeldas() {
            ImpactArea area = resolver.computeImpactArea(0, 0);
            // Celdas válidas: (0,0),(1,0),(0,1),(1,1)
            assertThat(area.getPositions()).hasSize(4);
        }

        @Test
        @DisplayName("Esquina (14,14) produce exactamente 4 celdas")
        void esquinaInfDerProduceCuatroCeldas() {
            ImpactArea area = resolver.computeImpactArea(14, 14);
            assertThat(area.getPositions()).hasSize(4);
        }

        @Test
        @DisplayName("Borde lateral (0,7) produce exactamente 6 celdas")
        void bordeLateralProduceSeisCeldas() {
            // Cols válidas: 0,1 (la -1 no existe) × Rows: 6,7,8 = 6 celdas
            ImpactArea area = resolver.computeImpactArea(0, 7);
            assertThat(area.getPositions()).hasSize(6);
        }

        @Test
        @DisplayName("Borde superior (7,0) produce exactamente 6 celdas")
        void bordeSuperiorProduceSeisCeldas() {
            // Cols: 6,7,8 × Rows válidas: 0,1 (la -1 no existe) = 6 celdas
            ImpactArea area = resolver.computeImpactArea(7, 0);
            assertThat(area.getPositions()).hasSize(6);
        }

        @Test
        @DisplayName("El área contiene la celda central")
        void areaContieneElCentro() {
            ImpactArea area = resolver.computeImpactArea(7, 7);
            assertThat(area.contains(new Position(7, 7))).isTrue();
        }

        @Test
        @DisplayName("El área no contiene celdas fuera del rango 3×3")
        void areaNoContieneCeldasFuera() {
            ImpactArea area = resolver.computeImpactArea(7, 7);
            assertThat(area.contains(new Position(10, 10))).isFalse();
            assertThat(area.contains(new Position(4, 4))).isFalse();
        }
    }
}