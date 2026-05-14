package es.game.blindsector.game.engine;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de MovementEngine.
 * No levanta Spring — instancia directa de la clase.
 * Escenarios cubiertos (según criterios de aceptación P2-01):
 *  - Movimiento recto válido
 *  - Movimiento diagonal válido
 *  - Quedarse quieto (distancia 0)
 *  - Exceder distancia Chebyshev
 *  - Salir del tablero por cada borde (top, bottom, left, right)
 *  - Orden de validación: OUT_OF_BOUNDS tiene prioridad sobre INVALID_MOVE
 */
class MovementEngineTest {

    private MovementEngine engine;
    private PlayerState player;

    @BeforeEach
    void setUp() {
        engine = new MovementEngine();
        // Jugador en el centro del tablero
        player = new PlayerState("player-1", 7, 7);
    }

    // ------------------------------------------------------------------ //
    //  Movimientos válidos                                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Movimientos válidos")
    class MovimientosValidos {

        @Test
        @DisplayName("Movimiento recto — 4 celdas hacia la derecha")
        void movimientoRectoValido() {
            engine.applyMovement(player, 11, 7);

            assertThat(player.getPosCol()).isEqualTo(11);
            assertThat(player.getPosRow()).isEqualTo(7);
        }

        @Test
        @DisplayName("Movimiento diagonal — 4 celdas en diagonal")
        void movimientoDiagonalValido() {
            engine.applyMovement(player, 11, 11);

            assertThat(player.getPosCol()).isEqualTo(11);
            assertThat(player.getPosRow()).isEqualTo(11);
        }

        @Test
        @DisplayName("Movimiento diagonal — 1 celda en diagonal")
        void movimientoDiagonalCorto() {
            engine.applyMovement(player, 8, 8);

            assertThat(player.getPosCol()).isEqualTo(8);
            assertThat(player.getPosRow()).isEqualTo(8);
        }

        @Test
        @DisplayName("Quedarse quieto — distancia Chebyshev 0")
        void quedarsEQuieto() {
            engine.applyMovement(player, 7, 7);

            assertThat(player.getPosCol()).isEqualTo(7);
            assertThat(player.getPosRow()).isEqualTo(7);
        }

        @Test
        @DisplayName("Movimiento exactamente a distancia 4")
        void movimientoExactamenteDistancia4() {
            // Vertical puro: (7,7) → (7,3), distancia Chebyshev = 4
            engine.applyMovement(player, 7, 3);

            assertThat(player.getPosRow()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------ //
    //  Movimientos inválidos — distancia excedida                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Distancia Chebyshev excedida")
    class DistanciaExcedida {

        @Test
        @DisplayName("Distancia 5 en recto → INVALID_MOVE")
        void distancia5Recto() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 12, 7),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_MOVE);
        }

        @Test
        @DisplayName("Distancia 5 en diagonal → INVALID_MOVE")
        void distancia5Diagonal() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 12, 12),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_MOVE);
        }

        @Test
        @DisplayName("La posición del jugador NO se modifica si el movimiento es inválido")
        void posicionNoModificadaEnError() {
            catchThrowableOfType(
                    () -> engine.applyMovement(player, 14, 14),
                    GameException.class
            );

            // Jugador sigue en (7,7)
            assertThat(player.getPosCol()).isEqualTo(7);
            assertThat(player.getPosRow()).isEqualTo(7);
        }
    }

    // ------------------------------------------------------------------ //
    //  Fuera del tablero — borde superior                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Fuera del tablero")
    class FueraDelTablero {

        @Test
        @DisplayName("Fila negativa → OUT_OF_BOUNDS")
        void filaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 7, -1),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Fila 15 → OUT_OF_BOUNDS")
        void fila15() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 7, 15),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Columna negativa → OUT_OF_BOUNDS")
        void columnaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, -1, 7),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Columna 15 → OUT_OF_BOUNDS")
        void columna15() {
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 15, 7),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        // ---- Prioridad de validación ---- //

        @Test
        @DisplayName("OUT_OF_BOUNDS tiene prioridad sobre INVALID_MOVE")
        void outOfBoundsPrimero() {
            // (7,7) → (20,20): fuera del tablero Y excede distancia
            // Debe lanzar OUT_OF_BOUNDS, no INVALID_MOVE
            GameException ex = catchThrowableOfType(
                    () -> engine.applyMovement(player, 20, 20),
                    GameException.class
            );

            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Posición no se modifica al salir del tablero")
        void posicionNoModificadaFueraTablero() {
            catchThrowableOfType(
                    () -> engine.applyMovement(player, -1, -1),
                    GameException.class
            );

            assertThat(player.getPosCol()).isEqualTo(7);
            assertThat(player.getPosRow()).isEqualTo(7);
        }
    }

    // ------------------------------------------------------------------ //
    //  Casos de borde del tablero (celdas 0 y 14)                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Celdas límite del tablero")
    class CeldasLimite {

        @Test
        @DisplayName("Celda (0,0) desde posición cercana — válida")
        void esquinaSupIzqValida() {
            player = new PlayerState("p", 3, 3);
            engine.applyMovement(player, 0, 0);

            assertThat(player.getPosCol()).isEqualTo(0);
            assertThat(player.getPosRow()).isEqualTo(0);
        }

        @Test
        @DisplayName("Celda (14,14) desde posición cercana — válida")
        void esquinaInfDerValida() {
            player = new PlayerState("p", 11, 11);
            engine.applyMovement(player, 14, 14);

            assertThat(player.getPosCol()).isEqualTo(14);
            assertThat(player.getPosRow()).isEqualTo(14);
        }
    }
}