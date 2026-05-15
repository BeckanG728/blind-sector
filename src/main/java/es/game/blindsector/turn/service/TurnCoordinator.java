package es.game.blindsector.turn.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.engine.TurnResolver;
import es.game.blindsector.game.validation.AttackValidator;
import es.game.blindsector.game.validation.MovementValidator;
import es.game.blindsector.game.validation.TurnValidator;
import es.game.blindsector.infrastructure.lock.LockExecutor;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnCoordinatorResult;
import es.game.blindsector.turn.domain.TurnResolutionResult;
import org.springframework.stereotype.Component;

/**
 * Coordina el ciclo de vida de un turno: validación, acumulación y resolución.

 * Todo el flujo ocurre dentro del lock de la partida (LockExecutor).
 * Esto garantiza que dos threads concurrentes no puedan resolver el mismo
 * turno dos veces, ni que una acción llegue durante la resolución.

 * Flujo interno de submitAction:
 *   1. Validar la acción (TurnValidator, MovementValidator, AttackValidator)
 *   2. Registrar firstActionReceivedAt si es la primera acción del turno
 *   3. Agregar la acción a pendingActions
 *   4a. Si pendingActions.size() == 1 → retornar waiting=true
 *   4b. Si pendingActions.size() == 2 → resolver el turno y retornar resolved=true

 * Responsabilidades fuera del scope:
 *   - Cargar GameState desde memoria (TurnSubmissionService)
 *   - Persistir el resultado final (GameLifecycleService)
 *   - Detectar y forzar timeouts (TurnTimeoutService)
 */
@Component
public class TurnCoordinator {
    private final TurnValidator      turnValidator;
    private final MovementValidator  movementValidator;
    private final AttackValidator    attackValidator;
    private final TurnResolver       turnResolver;
    private final LockExecutor       lockExecutor;

    public TurnCoordinator(TurnValidator turnValidator,
                           MovementValidator movementValidator,
                           AttackValidator attackValidator,
                           TurnResolver turnResolver,
                           LockExecutor lockExecutor) {
        this.turnValidator     = turnValidator;
        this.movementValidator = movementValidator;
        this.attackValidator   = attackValidator;
        this.turnResolver      = turnResolver;
        this.lockExecutor      = lockExecutor;
    }

    /**
     * Registra la acción de un jugador y, si ambos han actuado, resuelve el turno.

     * Todas las validaciones y mutaciones ocurren dentro del lock de la partida.
     * Si cualquier validación falla, se lanza {@link es.game.blindsector.shared.exception.GameException}
     * antes de modificar el estado.
     *
     * @param game   estado de la partida (obtenido de GameMemoryStore por el caller)
     * @param action acción que el jugador quiere registrar
     * @return {@link TurnCoordinatorResult} con {@code waiting=true} si falta la otra acción,
     *         o {@code resolved=true} con el {@link TurnResolutionResult} si el turno se resolvió
     */
    public TurnCoordinatorResult submitAction(GameState game, TurnAction action) {
        return lockExecutor.executeWithLock(game, () -> executeInsideLock(game, action));
    }

    // ── Lógica que corre dentro del lock ─────────────────────────────────

    private TurnCoordinatorResult executeInsideLock(GameState game, TurnAction action) {

        // ── Validaciones ────────────────────────────────────────────────
        turnValidator.validate(game, action);

        PlayerState actingPlayer = resolveActingPlayer(game, action.getPlayerId());
        movementValidator.validate(actingPlayer, action.getMoveToCol(), action.getMoveToRow());
        attackValidator.validate(action.getAttackCol(), action.getAttackRow());

        // ── Registrar timestamp de primera acción ────────────────────────
        if (game.getPendingActions().isEmpty()) {
            game.setFirstActionReceivedAt(System.currentTimeMillis());
        }

        // ── Acumular acción ──────────────────────────────────────────────
        game.getPendingActions().put(action.getPlayerId(), action);

        // ── ¿Tenemos las dos acciones? ───────────────────────────────────
        if (game.getPendingActions().size() < 2) {
            return TurnCoordinatorResult.waiting();
        }

        // ── Resolver turno ───────────────────────────────────────────────
        return resolveTurn(game);
    }

    private TurnCoordinatorResult resolveTurn(GameState game) {
        game.setStatus(GameStatus.RESOLVING);

        TurnAction actionA = game.getPendingActions().get(game.getPlayerA().getPlayerId());
        TurnAction actionB = game.getPendingActions().get(game.getPlayerB().getPlayerId());

        TurnResolutionResult result = turnResolver.resolve(game, actionA, actionB);

        // ── Limpiar acciones pendientes ──────────────────────────────────
        game.getPendingActions().clear();
        game.setFirstActionReceivedAt(0L);

        // ── Actualizar estado de la partida ──────────────────────────────
        if (result.isGameOver()) {
            game.setStatus(GameStatus.FINISHED);
        } else {
            game.setStatus(GameStatus.ACTIVE);
        }

        return TurnCoordinatorResult.resolved(result);
    }

    // ── Helper ───────────────────────────────────────────────────────────

    /**
     * Localiza el PlayerState correspondiente al playerId dentro del GameState.
     * Necesario para pasar al MovementValidator la posición actual del jugador.

     * Si el playerId no coincide con ningún jugador de la partida, TurnValidator
     * ya habría fallado antes. Como medida defensiva adicional se lanza
     * IllegalStateException.
     */
    private PlayerState resolveActingPlayer(GameState game, String playerId) {
        if (playerId.equals(game.getPlayerA().getPlayerId())) {
            return game.getPlayerA();
        }
        if (playerId.equals(game.getPlayerB().getPlayerId())) {
            return game.getPlayerB();
        }
        throw new IllegalStateException(
                "PlayerId '%s' no pertenece a la partida '%s'"
                        .formatted(playerId, game.getGameId())
        );
    }
}
