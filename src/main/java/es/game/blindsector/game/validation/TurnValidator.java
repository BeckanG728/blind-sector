package es.game.blindsector.game.validation;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.turn.domain.TurnAction;
import org.springframework.stereotype.Component;

/**
 * Valida que una acción de turno sea aceptable en el contexto actual de la partida.

 * Orden de validación:
 *   1. Estado de la partida  → GAME_NOT_ACTIVE   (la partida debe estar en ACTIVE)
 *   2. Versión de turno      → STALE_TURN        (el turno declarado debe coincidir)
 *   3. Deduplicación         → DUPLICATE_ACTION  (el jugador no puede actuar dos veces)

 * Este validador opera sobre el GameState dentro del lock de la partida.
 * La seguridad ante concurrencia la garantiza LockExecutor — este validador
 * no necesita ser thread-safe por sí mismo.
 */
@Component
public class TurnValidator {

    /**
     * Lanza una {@link GameException} si la acción no puede aceptarse en el turno actual.
     *
     * @param game   estado actual de la partida (leído dentro del lock)
     * @param action acción que el jugador intenta registrar
     * @throws GameException GAME_NOT_ACTIVE   si {@code game.status != ACTIVE}
     * @throws GameException STALE_TURN        si {@code action.turn != game.turnNumber}
     * @throws GameException DUPLICATE_ACTION  si el jugador ya tiene una acción pendiente
     */
    public void validate(GameState game, TurnAction action) {

        if (game.getStatus() != GameStatus.ACTIVE) {
            throw new GameException(
                    GameErrorCode.GAME_NOT_ACTIVE,
                    "La partida '%s' no está en estado ACTIVE (estado actual: %s)"
                            .formatted(game.getGameId(), game.getStatus())
            );
        }

        if (action.getTurn() != game.getTurnNumber()) {
            throw new GameException(
                    GameErrorCode.STALE_TURN,
                    "Acción extemporánea: turno declarado %d, turno activo %d"
                            .formatted(action.getTurn(), game.getTurnNumber())
            );
        }

        if (game.getPendingActions().containsKey(action.getPlayerId())) {
            throw new GameException(
                    GameErrorCode.DUPLICATE_ACTION,
                    "El jugador '%s' ya envió su acción para el turno %d"
                            .formatted(action.getPlayerId(), game.getTurnNumber())
            );
        }
    }
}
