package es.game.blindsector.application.service;

import es.game.blindsector.domain.game.GameState;
import es.game.blindsector.domain.player.PlayerState;
import es.game.blindsector.domain.turn.TurnAction;
import es.game.blindsector.domain.turn.TurnCoordinatorResult;
import es.game.blindsector.domain.turn.TurnResolutionResult;
import es.game.blindsector.engine.TurnResolver;
import es.game.blindsector.engine.validation.AttackValidator;
import es.game.blindsector.engine.validation.MovementValidator;
import es.game.blindsector.engine.validation.TurnValidator;
import es.game.blindsector.infrastructure.lock.LockExecutor;
import es.game.blindsector.shared.enums.GameStatus;
import org.springframework.stereotype.Component;

/**
 * Coordina el ciclo de vida de un turno: validación, acumulación y resolución.
 * <p>
 * Todo el flujo ocurre dentro del lock de la partida (LockExecutor).
 * Esto garantiza que dos threads concurrentes no puedan resolver el mismo
 * turno dos veces, ni que una acción llegue durante la resolución.
 * <p>
 * Flujo interno de submitAction:
 *   1. Validar la acción (TurnValidator, MovementValidator, AttackValidator)
 *   2. Registrar firstActionReceivedAt si es la primera acción del turno
 *   3. Agregar la acción a pendingActions
 *   4a. Si pendingActions.size() == 1 → retornar waiting=true
 *   4b. Si pendingActions.size() == 2 → resolver el turno y retornar resolved=true
 * <p>
 * Para acciones de timeout (TurnTimeoutService) se usa resolveTurnForTimeout(),
 * que bypasea las validaciones e inyecta la acción directamente en pendingActions
 * antes de resolver. Debe llamarse desde dentro del lock de la partida.
 * <p>
 * Responsabilidades fuera del scope:
 *   - Cargar GameState desde memoria (TurnSubmissionService)
 *   - Persistir el resultado final (GameLifecycleService)
 *   - Detectar y forzar timeouts (TurnTimeoutService)
 */
@Component
public class TurnCoordinatorService {
    private final TurnValidator turnValidator;
    private final MovementValidator movementValidator;
    private final AttackValidator attackValidator;
    private final TurnResolver turnResolver;
    private final LockExecutor lockExecutor;

    public TurnCoordinatorService(TurnValidator turnValidator,
                                  MovementValidator movementValidator,
                                  AttackValidator attackValidator,
                                  TurnResolver turnResolver,
                                  LockExecutor lockExecutor) {
        this.turnValidator = turnValidator;
        this.movementValidator = movementValidator;
        this.attackValidator = attackValidator;
        this.turnResolver = turnResolver;
        this.lockExecutor = lockExecutor;
    }

    /**
     * Registra la acción de un jugador y, si ambos han actuado, resuelve el turno.
     * <p>
     * Todas las validaciones y mutaciones ocurren dentro del lock de la partida.
     * Si cualquier validación falla, se lanza {@link es.game.blindsector.shared.exception.GameException}
     * antes de modificar el estado.
     *
     * @param game   estado de la partida (obtenido de GameMemoryStore por el caller)
     * @param action acción que el jugador quiere registrar
     * @return {@link TurnCoordinatorResult} con {@code waiting=true} si falta la otra acción,
     * o {@code resolved=true} con el {@link TurnResolutionResult} si el turno se resolvió
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

        // ── Persistir resultado para polling (SnapshotService) ───────────
        game.setLastResolutionResult(result);

        // ── Actualizar estado de la partida ──────────────────────────────
        if (result.isGameOver()) {
            game.setStatus(GameStatus.FINISHED);
        } else {
            game.setStatus(GameStatus.ACTIVE);
        }

        return TurnCoordinatorResult.resolved(result);
    }

    // ── API para timeout (sin validaciones) ──────────────────────────────

    /**
     * Inyecta una acción de timeout directamente en {@code pendingActions} y,
     * si con ella se completan las dos acciones del turno, lo resuelve.
     *
     * <p>Este método bypasea todas las validaciones ({@link TurnValidator},
     * {@link MovementValidator},
     * {@link AttackValidator}) porque la acción
     * fue construida internamente por {@link TurnTimeoutService} y no proviene
     * de un jugador. En particular, el punto de ataque {@code (-1, -1)} es
     * intencionadamente inválido para garantizar MISS en {@code ImpactResolver}.
     *
     * <p><b>Precondición:</b> debe llamarse desde dentro del lock de la partida
     * (ya adquirido por {@link TurnTimeoutService}). {@link LockExecutor} usa
     * {@link java.util.concurrent.locks.ReentrantLock}, por lo que el mismo
     * thread puede re-entrar sin bloquearse.
     *
     * @param game          estado de la partida, ya bajo lock
     * @param timeoutAction acción por defecto construida por TurnTimeoutService
     * @return {@link TurnCoordinatorResult#waiting()} si aún falta la otra acción,
     * o {@link TurnCoordinatorResult#resolved(TurnResolutionResult)} si el turno se resolvió
     */
    TurnCoordinatorResult resolveTurnForTimeout(GameState game, TurnAction timeoutAction) {
        return lockExecutor.executeWithLock(game, () -> {

            // ── Inyectar sin validar ─────────────────────────────────────
            if (game.getPendingActions().isEmpty()) {
                game.setFirstActionReceivedAt(System.currentTimeMillis());
            }
            game.getPendingActions().put(timeoutAction.getPlayerId(), timeoutAction);

            // ── ¿Tenemos las dos acciones? ───────────────────────────────
            if (game.getPendingActions().size() < 2) {
                return TurnCoordinatorResult.waiting();
            }

            return resolveTurn(game);
        });
    }

    // ── Helper ───────────────────────────────────────────────────────────

    /**
     * Localiza el PlayerState correspondiente al playerId dentro del GameState.
     * Necesario para pasar al MovementValidator la posición actual del jugador.
     * <p>
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
