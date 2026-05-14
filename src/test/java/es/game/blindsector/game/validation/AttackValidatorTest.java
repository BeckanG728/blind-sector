package es.game.blindsector.game.validation;

import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AttackValidatorTest {

    private AttackValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AttackValidator();
    }

    @Nested
    @DisplayName("Ataques válidos — no lanza excepción")
    class AtaquesValidos {

        @Test
        @DisplayName("Centro del tablero (7,7)")
        void centroDeltablero() {
            assertThatNoException().isThrownBy(() -> validator.validate(7, 7));
        }

        @Test
        @DisplayName("Esquina superior izquierda (0,0)")
        void esquinaSupIzq() {
            assertThatNoException().isThrownBy(() -> validator.validate(0, 0));
        }

        @Test
        @DisplayName("Esquina inferior derecha (14,14)")
        void esquinaInfDer() {
            assertThatNoException().isThrownBy(() -> validator.validate(14, 14));
        }

        @Test
        @DisplayName("Borde lateral (0,7)")
        void bordeLateral() {
            assertThatNoException().isThrownBy(() -> validator.validate(0, 7));
        }
    }

    @Nested
    @DisplayName("INVALID_ATTACK — punto central fuera del tablero")
    class AtaquesInvalidos {

        @Test
        @DisplayName("Columna negativa → INVALID_ATTACK")
        void columnaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(-1, 7),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }

        @Test
        @DisplayName("Columna 15 → INVALID_ATTACK")
        void columna15() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(15, 7),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }

        @Test
        @DisplayName("Fila negativa → INVALID_ATTACK")
        void filaNegativa() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(7, -1),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }

        @Test
        @DisplayName("Fila 15 → INVALID_ATTACK")
        void fila15() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(7, 15),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }

        @Test
        @DisplayName("Ambas coordenadas fuera → INVALID_ATTACK")
        void ambasCoordsInvalidas() {
            GameException ex = catchThrowableOfType(
                    () -> validator.validate(-1, 20),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }
    }
}