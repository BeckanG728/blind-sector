package es.game.blindsector.game.engine;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.shared.utils.GridUtils;
import org.springframework.stereotype.Component;

/**
 * Aplica el movimiento declarado por un jugador a su PlayerState.
 * Orden de validación (el mismo que MovementValidator):
 *   1. Límites del tablero  → OUT_OF_BOUNDS
 *   2. Distancia Chebyshev  → INVALID_MOVE
 * Quedarse en la misma celda (distancia 0) es siempre válido.
 */
@Component
public class MovementEngine {

    /**
     * Valida y aplica el movimiento a {@code player}.
     *------------------------------------------------------------------------
     * @param player  estado mutable del jugador (posición se modifica in-place)
     * @param toCol   columna destino (0-14)
     * @param toRow   fila destino   (0-14)
     * @throws GameException OUT_OF_BOUNDS  si el destino sale del tablero
     * @throws GameException INVALID_MOVE   si la distancia Chebyshev excede 4
     */
    public void applyMovement(PlayerState player, int toCol, int toRow) {

        if (!GridUtils.isInBounds(toCol, toRow)) {
            throw new GameException(
                    GameErrorCode.OUT_OF_BOUNDS,
                    "Destino (%d,%d) fuera del tablero (0-14)".formatted(toCol, toRow)
            );
        }

        int distance = GridUtils.chebyshevDistance(player.getPosCol(), player.getPosRow(), toCol, toRow);
        if (distance > 4) {
            throw new GameException(
                    GameErrorCode.INVALID_MOVE,
                    "Distancia Chebyshev %d excede el máximo permitido (4)".formatted(distance)
            );
        }
        player.setPosCol(toCol);
        player.setPosRow(toRow);
    }
}