package es.game.blindsector.game.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnCoordinatorResult;
import es.game.blindsector.turn.service.TurnCoordinator;
import org.springframework.stereotype.Service;

/**
 * Orquesta el flujo completo al recibir una acción de turno desde el controller.

 * Responsabilidades (exactamente estas, ninguna más):
 *   1. Cargar el GameState desde memoria (GameMemoryStore.getOrThrow)
 *   2. Delegar la acción al TurnCoordinator
 *   3. Si el turno se resolvió y la partida terminó → notificar a GameLifecycleService

 * Esta clase no contiene:
 *   - Lógica matemática ni de resolución
 *   - Validaciones de negocio
 *   - Manejo de locks
 *   - Acceso directo al GameState más allá de cargarlo

 * Cualquier GameException lanzada por las capas inferiores se propaga
 * tal cual — sin envolver, sin capturar, sin transformar.
 */
@Service
public class TurnSubmissionService {

    private final GameMemoryStore      gameMemoryStore;
    private final TurnCoordinator      turnCoordinator;
    private final GameLifecycleService gameLifecycleService;

    public TurnSubmissionService(GameMemoryStore gameMemoryStore,
                                 TurnCoordinator turnCoordinator,
                                 GameLifecycleService gameLifecycleService) {
        this.gameMemoryStore      = gameMemoryStore;
        this.turnCoordinator      = turnCoordinator;
        this.gameLifecycleService = gameLifecycleService;
    }

    /**
     * Procesa la acción de un jugador para el turno actual.
     *
     * @param gameId identificador de la partida
     * @param action acción que el jugador quiere registrar
     * @return resultado del coordinador: {@code waiting=true} si falta la otra acción,
     *         {@code resolved=true} con {@link es.game.blindsector.turn.domain.TurnResolutionResult}
     *         si el turno se resolvió en esta llamada
     * @throws es.game.blindsector.shared.exception.GameException propagada sin modificar
     *         si la partida no existe, la acción es inválida o el turno está cerrado
     */
    public TurnCoordinatorResult submit(String gameId, TurnAction action) {

        // ── 1. Cargar estado desde memoria ───────────────────────────────
        GameState game = gameMemoryStore.getOrThrow(gameId);

        // ── 2. Delegar al coordinador ────────────────────────────────────
        TurnCoordinatorResult result = turnCoordinator.submitAction(game, action);

        // ── 3. Cerrar partida si terminó ─────────────────────────────────
        if (result.getResolved() && result.getResolutionResult().isGameOver()) {
            String winnerId   = result.getResolutionResult().getWinnerId();
            int    turnsPlayed = game.getTurnNumber();
            gameLifecycleService.finalize(gameId, winnerId, turnsPlayed);
        }

        return result;
    }
}