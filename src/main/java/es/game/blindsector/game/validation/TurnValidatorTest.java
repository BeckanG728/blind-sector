package es.game.blindsector.game.validation;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.turn.domain.TurnAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TurnValidatorTest {

    private TurnValidator validator;
    private GameState game;

    @BeforeEach
    void setUp() {
        validator = new TurnValidator();

        PlayerState playerA = new PlayerState("player-a", 2, 2);
        PlayerState playerB = new PlayerState("player-b", 12, 12);

        game = new GameState("game-1",GameStatus.ACTIVE,1, playerA, playerB);
    }

    // ------------------------------------------------------------------ //
    //  Caso válido                                                         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Acción válida — no lanza excepción")
    void accionValida() {
        TurnAction action = buildAction("player-a", 1, 5, 5, 7, 7);
        assertThatNoException().isThrownBy(() -> validator.validate(game, action));
    }

    @Test
    @DisplayName("Segundo jugador también puede enviar acción válida")
    void segundoJugadorValido() {
        TurnAction actionA = buildAction("player-a", 1, 5, 5, 7, 7);
        game.getPendingActions().put("player-a", actionA);

        TurnAction actionB = buildAction("player-b", 1, 10, 10, 3, 3);

        assertThatNoException().isThrownBy(() -> validator.validate(game, actionB));
    }

    // ------------------------------------------------------------------ //
    //  GAME_NOT_ACTIVE                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("GAME_NOT_ACTIVE — partida no en estado ACTIVE")
    class GameNotActive {

        @Test
        @DisplayName("Estado WAITING → GAME_NOT_ACTIVE")
        void estadoWaiting() {
            game.setStatus(GameStatus.WAITING);
            TurnAction action = buildAction("player-a", 1, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_ACTIVE);
        }

        @Test
        @DisplayName("Estado RESOLVING → GAME_NOT_ACTIVE")
        void estadoResolving() {
            game.setStatus(GameStatus.RESOLVING);
            TurnAction action = buildAction("player-a", 1, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_ACTIVE);
        }

        @Test
        @DisplayName("Estado FINISHED → GAME_NOT_ACTIVE")
        void estadoFinished() {
            game.setStatus(GameStatus.FINISHED);
            TurnAction action = buildAction("player-a", 1, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_ACTIVE);
        }

        @Test
        @DisplayName("GAME_NOT_ACTIVE tiene prioridad sobre STALE_TURN")
        void gameNotActivePrimero() {
            game.setStatus(GameStatus.FINISHED);
            TurnAction action = buildAction("player-a", 99, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_ACTIVE);
        }
    }

    // ------------------------------------------------------------------ //
    //  STALE_TURN                                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("STALE_TURN — turno versionado incorrecto")
    class StaleTurn {

        @Test
        @DisplayName("Turno declarado menor que el activo → STALE_TURN")
        void turnoPasado() {
            TurnAction action = buildAction("player-a", 0, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.STALE_TURN);
        }

        @Test
        @DisplayName("Turno declarado mayor que el activo → STALE_TURN")
        void turnoFuturo() {
            TurnAction action = buildAction("player-a", 5, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, action),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.STALE_TURN);
        }

        @Test
        @DisplayName("STALE_TURN tiene prioridad sobre DUPLICATE_ACTION")
        void staleTurnPrimero() {
            TurnAction existente = buildAction("player-a", 1, 5, 5, 7, 7);
            game.getPendingActions().put("player-a", existente);

            TurnAction reintento = buildAction("player-a", 0, 5, 5, 7, 7);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, reintento),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.STALE_TURN);
        }
    }

    // ------------------------------------------------------------------ //
    //  DUPLICATE_ACTION                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DUPLICATE_ACTION — jugador ya actuó este turno")
    class DuplicateAction {

        @Test
        @DisplayName("Mismo jugador envía segunda acción → DUPLICATE_ACTION")
        void segundaAccionMismoJugador() {
            TurnAction primera = buildAction("player-a", 1, 5, 5, 7, 7);
            game.getPendingActions().put("player-a", primera);

            TurnAction segunda = buildAction("player-a", 1, 6, 6, 8, 8);

            GameException ex = catchThrowableOfType(
                    () -> validator.validate(game, segunda),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.DUPLICATE_ACTION);
        }

        @Test
        @DisplayName("El otro jugador no es afectado por la acción pendiente del primero")
        void otroJugadorNoAfectado() {
            TurnAction accionA = buildAction("player-a", 1, 5, 5, 7, 7);
            game.getPendingActions().put("player-a", accionA);

            TurnAction accionB = buildAction("player-b", 1, 10, 10, 3, 3);

            assertThatNoException().isThrownBy(() -> validator.validate(game, accionB));
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper                                                              //
    // ------------------------------------------------------------------ //

    private TurnAction buildAction(String playerId, int turn,
                                   int moveToCol, int moveToRow,
                                   int attackCol, int attackRow) {
        TurnAction action = new TurnAction();
        action.setPlayerId(playerId);
        action.setTurn(turn);
        action.setMoveToCol(moveToCol);
        action.setMoveToRow(moveToRow);
        action.setAttackCol(attackCol);
        action.setAttackRow(attackRow);
        action.setSubmittedAt(System.currentTimeMillis());
        return action;
    }
}
