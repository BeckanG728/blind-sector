package es.game.blindsector.turn.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.infrastructure.lock.LockExecutor;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.turn.domain.TurnAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fuerza la resolución de un turno cuando un jugador no envió su acción
 * dentro del tiempo límite configurado.

 * Contrato de seguridad:
 *   La verificación de condición (status==ACTIVE && pendingActions.size()==1)
 *   ocurre DENTRO del lock, justo antes de inyectar la acción por defecto.
 *   Si el turno ya fue resuelto entre que el scheduler detectó el timeout y
 *   este servicio adquirió el lock, la inyección se cancela silenciosamente.

 * Acción por defecto para el jugador ausente:
 *   - Movimiento: misma celda actual (sin desplazamiento)
 *   - Ataque: (-1, -1) — fuera del tablero, siempre resulta en MISS

 * Responsabilidades fuera del scope:
 *   - Detectar qué partidas han superado el timeout (TurnTimeoutScheduler)
 *   - Lógica de resolución del turno (TurnCoordinator → TurnResolver)
 */
@Component
public class TurnTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(TurnTimeoutService.class);

    private final GameMemoryStore  gameMemoryStore;
    private final LockExecutor     lockExecutor;
    private final TurnCoordinator  turnCoordinator;

    public TurnTimeoutService(GameMemoryStore gameMemoryStore,
                              LockExecutor lockExecutor,
                              TurnCoordinator turnCoordinator) {
        this.gameMemoryStore  = gameMemoryStore;
        this.lockExecutor     = lockExecutor;
        this.turnCoordinator  = turnCoordinator;
    }

    /**
     * Fuerza la resolución del turno actual de {@code gameId} si aún hay
     * exactamente una acción pendiente y la partida sigue en estado ACTIVE.

     * Si la partida no existe, ya terminó, o el turno ya fue resuelto,
     * retorna sin efecto ni excepción.
     *
     * @param gameId identificador de la partida a forzar
     */
    public void forceResolveTimeout(String gameId) {
        GameState game;
        try {
            game = gameMemoryStore.getOrThrow(gameId);
        } catch (GameException e) {
            if (e.getErrorCode() == GameErrorCode.GAME_NOT_FOUND) {
                log.debug("Timeout ignorado: partida '{}' ya no existe en memoria.", gameId);
                return;
            }
            throw e;
        }

        lockExecutor.executeWithLock(game, () -> {
            executeInsideLock(game);
            return null;
        });
    }

    // ── Lógica que corre dentro del lock ─────────────────────────────────

    private void executeInsideLock(GameState game) {

        // ── Verificar condición dentro del lock ──────────────────────────
        // El turno puede haberse resuelto entre que el scheduler detectó el
        // timeout y este método adquirió el lock. Si no se cumple la
        // condición exacta, no se hace nada.
        if (game.getStatus() != GameStatus.ACTIVE) {
            log.debug("Timeout ignorado para '{}': estado actual es {}.",
                    game.getGameId(), game.getStatus());
            return;
        }

        if (game.getPendingActions().size() != 1) {
            log.debug("Timeout ignorado para '{}': pendingActions.size()={}.",
                    game.getGameId(), game.getPendingActions().size());
            return;
        }

        // ── Identificar al jugador ausente ───────────────────────────────
        String absentPlayerId = resolveAbsentPlayer(game);
        PlayerState absentPlayer = absentPlayerId.equals(game.getPlayerA().getPlayerId())
                ? game.getPlayerA()
                : game.getPlayerB();

        log.info("Timeout forzado para partida '{}', turno {}. Jugador ausente: '{}'.",
                game.getGameId(), game.getTurnNumber(), absentPlayerId);

        // ── Construir acción por defecto ─────────────────────────────────
        TurnAction defaultAction = buildDefaultAction(absentPlayer, game.getTurnNumber());

        // ── Delegar resolución al coordinador ────────────────────────────
        // TurnCoordinator intentará adquirir el lock, pero LockExecutor usa
        // ReentrantLock (reentrant), por lo que el mismo thread puede re-entrar.
        turnCoordinator.submitAction(game, defaultAction);
    }

    // ── Helpers privados ─────────────────────────────────────────────────

    /**
     * Determina qué jugador no tiene acción pendiente en el turno actual.
     */
    private String resolveAbsentPlayer(GameState game) {
        String playerAId = game.getPlayerA().getPlayerId();
        String playerBId = game.getPlayerB().getPlayerId();

        if (!game.getPendingActions().containsKey(playerAId)) {
            return playerAId;
        }
        return playerBId;
    }

    /**
     * Construye una acción neutral para el jugador ausente:
     *   - Se queda en su posición actual (sin movimiento)
     *   - Ataca a (-1, -1), que siempre resulta en MISS al estar fuera del tablero

     * El punto de ataque (-1, -1) es intencionadamente inválido para garantizar
     * MISS sin necesidad de lógica adicional en ImpactResolver.
     * AttackValidator NO se llama para acciones de timeout — se bypasea
     * a través de pendingActions.put directamente en el coordinador,
     * o se usa un ataque fuera de tablero que ImpactResolver interpreta como MISS.

     * Nota: si AttackValidator rechaza (-1,-1), la acción por defecto debe
     * construirse con un punto de ataque válido pero dirigido a una celda
     * imposible de contener al rival (ej. la propia posición del jugador ausente
     * si el rival está lejos). La implementación final depende del comportamiento
     * acordado en equipo para timeouts. El valor por defecto aquí es (-1,-1)
     * como señal explícita de "acción de timeout".
     */
    private TurnAction buildDefaultAction(PlayerState absentPlayer, int currentTurn) {
        TurnAction defaultAction = new TurnAction();
        defaultAction.setPlayerId(absentPlayer.getPlayerId());
        defaultAction.setTurn(currentTurn);
        defaultAction.setMoveToCol(absentPlayer.getPosCol()); // sin movimiento
        defaultAction.setMoveToRow(absentPlayer.getPosRow()); // sin movimiento
        defaultAction.setAttackCol(-1);  // fuera del tablero → MISS garantizado
        defaultAction.setAttackRow(-1);  // fuera del tablero → MISS garantizado
        defaultAction.setSubmittedAt(System.currentTimeMillis());
        return defaultAction;
    }
}