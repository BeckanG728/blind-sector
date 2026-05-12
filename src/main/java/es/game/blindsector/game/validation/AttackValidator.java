package es.game.blindsector.game.validation;

import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.shared.utils.GridUtils;
import org.springframework.stereotype.Component;

/**
 * Valida el punto central del ataque declarado por un jugador.

 * Solo se valida que el centro esté dentro del tablero (0–14).
 * Las celdas del área 3×3 que queden fuera del tablero se recortan
 * automáticamente en ImpactResolver — no es error del jugador atacar
 * desde un borde, solo que el centro en sí sea alcanzable.
 */
@Component
public class AttackValidator {

    /**
     * Lanza una {@link GameException} si el punto central del ataque está fuera del tablero.
     *
     * @param attackCol columna del punto central del ataque
     * @param attackRow fila del punto central del ataque
     * @throws GameException INVALID_ATTACK si el punto central está fuera de [0, 14]
     */
    public void validate(int attackCol, int attackRow) {

        if (!GridUtils.isInBounds(attackCol, attackRow)) {
            throw new GameException(
                    GameErrorCode.INVALID_ATTACK,
                    "Punto de ataque (%d,%d) fuera del tablero (0-14)".formatted(attackCol, attackRow)
            );
        }
    }
}