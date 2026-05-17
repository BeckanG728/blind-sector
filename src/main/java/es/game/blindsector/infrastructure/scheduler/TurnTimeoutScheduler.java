package es.game.blindsector.infrastructure.scheduler;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.infrastructure.memory.ActiveGamesRegistry;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.service.TurnTimeoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class TurnTimeoutScheduler {

    private final ActiveGamesRegistry activeGamesRegistry;
    private final TurnTimeoutService turnTimeoutService;

    @Value("${turn.timeout.ms:30000}")
    private long timeoutMs;

    public TurnTimeoutScheduler(ActiveGamesRegistry activeGamesRegistry, TurnTimeoutService turnTimeoutService) {
        this.activeGamesRegistry = activeGamesRegistry;
        this.turnTimeoutService = turnTimeoutService;
    }

    /**
     * Proceso periódico que revisa el ciclo de vida de los turnos en memoria.
     * Se ejecuta según el intervalo configurado o por defecto cada 5 segundos (5000 ms).
     */
    @Scheduled(fixedDelayString = "${turn.scheduler.interval.ms:5000}")
    public void checkTurnTimeouts() {
        // 1. Criterio de aceptación: Lee partidas activas mediante ActiveGamesRegistry.getAllActive()
        Collection<GameState> activeGames = activeGamesRegistry.getAllActive();

        if (activeGames == null || activeGames.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // 2. Iteración sin adquirir ningún lock del GameState
        for (GameState game : activeGames) {

            // Evaluamos las tres condiciones explícitas del criterio de aceptación:
            // - status == ACTIVE
            // - pendingActions.size() == 1
            // - (now - firstActionReceivedAt) > timeout configurado
            if (game.getStatus() == GameStatus.ACTIVE
                    && game.getPendingActions().size() == 1
                    && game.getFirstActionReceivedAt() != null
                    && (now - game.getFirstActionReceivedAt()) > timeoutMs) {

                // 3. Delega la resolución sin tocar pendingActions ni el lock directamente
                turnTimeoutService.forceResolveTimeout(game.getGameId());
            }
        }
    }
}
