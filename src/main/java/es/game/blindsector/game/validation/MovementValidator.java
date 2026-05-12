package es.game.blindsector.game.validation;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.shared.utils.GridUtils;
import org.springframework.stereotype.Component;

/**
 * Valida el movimiento declarado por un jugador antes de enviarlo al engine.

 * Orden de validación (idéntico al de MovementEngine):
 *   1. Límites del tablero  → OUT_OF_BOUNDS   (prioridad)
 *   2. Distancia Chebyshev  → INVALID_MOVE

 * Quedarse en la misma celda (distancia 0) es siempre válido.

 * Separar la validación del engine permite testear las reglas de negocio
 * de forma independiente y mantener el engine enfocado en mutar estado.
 */
@Component
public class MovementValidator {

    /**
     * Lanza una {@link GameException} si el movimiento no es válido.
     * No retorna ningún valor — la ausencia de excepción indica éxito.
     *
     * @param current estado actual del jugador (posición de origen)
     * @param toCol   columna destino
     * @param toRow   fila destino
     * @throws GameException OUT_OF_BOUNDS  si el destino está fuera del tablero (0–14)
     * @throws GameException INVALID_MOVE   si la distancia Chebyshev excede 4
     */
    public void validate(PlayerState current, int toCol, int toRow) {

        if (!GridUtils.isInBounds(toCol, toRow)) {
            throw new GameException(
                    GameErrorCode.OUT_OF_BOUNDS,
                    "Destino (%d,%d) fuera del tablero (0-14)".formatted(toCol, toRow)
            );
        }

        int distance = GridUtils.chebyshevDistance(current.getPosCol(), current.getPosRow(), toCol, toRow);
        if (distance > 4) {
            throw new GameException(
                    GameErrorCode.INVALID_MOVE,
                    "Distancia Chebyshev %d excede el máximo permitido (4)".formatted(distance)
            );
        }
    }
}