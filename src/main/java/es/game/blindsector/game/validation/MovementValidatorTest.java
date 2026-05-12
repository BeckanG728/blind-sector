package es.game.blindsector.game.validation;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MovementValidatorTest {

    private MovementValidator validator;
    private PlayerState player;

    @BeforeEach
    void setUp() {
        validator = new MovementValidator();
        player = new PlayerState("p1", 7, 7);
    }

    @Nested
    @DisplayName("Movimientos válidos — no lanza excepción")
    class MovimientosValidos {

        @Test
        @DisplayName("Movimiento recto dentro de distancia 4")
        void movimientoRectoValido() {
            assertThatNoException().isThrownBy(() -> validator.validate(player, 11, 7));
        }

        @Test
        @DisplayName("Movimiento diagonal dentro de distancia 4")
        void movimientoDiagonalValido() {
            assertThatNoException().isThrownBy(() -> validator.validate(player, 11, 11));
        }

        @Test
        @DisplayName("Quedarse quieto — distancia 0")
        void quedarsEquieto() {
            assertThatNoException().isThrownBy(() -> validator.validate(player, 7, 7));
        }

        @Test
        @DisplayName("Distancia exactamente 4 — límite permitido")
        void distanciaExacta4() {
            assertThatNoException().isThrownBy(() -> validator.validate(player, 3, 7));
        }
    }

    @Nested
    @DisplayName("OUT_OF_BOUNDS — destino fuera del tablero")
    class FueraDelTablero {

        @Test
        @DisplayName("Fila negativa → OUT_OF_BOUNDS")
        void filaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 7, -1),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Fila 15 → OUT_OF_BOUNDS")
        void fila15() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 7, 15),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Columna negativa → OUT_OF_BOUNDS")
        void columnaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, -1, 7),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("Columna 15 → OUT_OF_BOUNDS")
        void columna15() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 15, 7),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("OUT_OF_BOUNDS tiene prioridad sobre INVALID_MOVE")
        void outOfBoundsPrimero() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 20, 20),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }
    }

    @Nested
    @DisplayName("INVALID_MOVE — distancia Chebyshev excedida")
    class DistanciaExcedida {

        @Test
        @DisplayName("Distancia 5 en recto → INVALID_MOVE")
        void distancia5Recto() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 12, 7),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_MOVE);
        }

        @Test
        @DisplayName("Distancia 5 en diagonal → INVALID_MOVE")
        void distancia5Diagonal() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(player, 12, 12),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_MOVE);
        }
    }
}
