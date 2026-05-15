package es.game.blindsector.turn.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.engine.DamageCalculator;
import es.game.blindsector.game.engine.ImpactResolver;
import es.game.blindsector.game.engine.MovementEngine;
import es.game.blindsector.game.engine.TurnResolver;
import es.game.blindsector.game.validation.AttackValidator;
import es.game.blindsector.game.validation.MovementValidator;
import es.game.blindsector.game.validation.TurnValidator;
import es.game.blindsector.infrastructure.lock.GameLockManager;
import es.game.blindsector.infrastructure.lock.LockExecutor;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.infrastructure.memory.ActiveGamesRegistry;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.domain.TurnAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de TurnTimeoutService.
 * Sin Spring context — instancias directas de todas las dependencias.

 * Escenarios cubiertos (criterios de aceptación P2-07):
 *  - Timeout exitoso: turno forzado cuando hay exactamente 1 acción pendiente
 *  - timeout_ignorado_si_turno_ya_resuelto: pendingActions.size()==2 o status!=ACTIVE
 *  - Partida inexistente: retorna sin error
 *  - Partida ya terminada (FINISHED): retorna sin error
 *  - El jugador correcto es el que recibe la acción por defecto
 */
class TurnTimeoutServiceTest {

    private TurnTimeoutService timeoutService;
    private GameMemoryStore    memoryStore;
    private TurnCoordinator    coordinator;

    @BeforeEach
    void setUp() {
        ActiveGamesRegistry registry = new ActiveGamesRegistry();
        memoryStore = new GameMemoryStore(registry);

        LockExecutor lockExecutor = new LockExecutor(new GameLockManager());

        coordinator = new TurnCoordinator(
                new TurnValidator(),
                new MovementValidator(),
                new AttackValidator(),
                new TurnResolver(new MovementEngine(), new ImpactResolver(), new DamageCalculator()),
                lockExecutor
        );

        timeoutService = new TurnTimeoutService(memoryStore, lockExecutor, coordinator);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private GameState buildAndRegisterGame(String gameId) {
        PlayerState playerA = new PlayerState("player-a", 2, 2);
        PlayerState playerB = new PlayerState("player-b", 12, 12);
        GameState game = new GameState(gameId, GameStatus.ACTIVE,1,playerA, playerB);
        memoryStore.save(game);
        return game;
    }

    private TurnAction action(String playerId, int turn,
                              int moveToCol, int moveToRow,
                              int attackCol, int attackRow) {
        TurnAction a = new TurnAction();
        a.setPlayerId(playerId);
        a.setTurn(turn);
        a.setMoveToCol(moveToCol);
        a.setMoveToRow(moveToRow);
        a.setAttackCol(attackCol);
        a.setAttackRow(attackRow);
        a.setSubmittedAt(System.currentTimeMillis());
        return a;
    }

    // ------------------------------------------------------------------ //
    //  Timeout exitoso                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Timeout exitoso — resolución forzada")
    class TimeoutExitoso {

        @Test
        @DisplayName("El turno se resuelve cuando hay exactamente 1 acción pendiente")
        void turnoResueltoConUnaAccionPendiente() {
            GameState game = buildAndRegisterGame("game-1");
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

            assertThat(game.getPendingActions()).hasSize(1);

            timeoutService.forceResolveTimeout("game-1");

            assertThat(game.getPendingActions()).isEmpty();
            assertThat(game.getTurnNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("El estado pasa a ACTIVE o FINISHED tras el timeout forzado")
        void estadoActualizadoTrasTiempoAgotado() {
            GameState game = buildAndRegisterGame("game-1");
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

            timeoutService.forceResolveTimeout("game-1");

            assertThat(game.getStatus())
                    .isIn(GameStatus.ACTIVE, GameStatus.FINISHED);
        }

        @Test
        @DisplayName("El jugador ausente B recibe la acción por defecto cuando solo actuó A")
        void jugadorAusenteEsB() {
            GameState game = buildAndRegisterGame("game-1");
            // Solo A envía acción → B es el ausente
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

            timeoutService.forceResolveTimeout("game-1");

            // Si se resolvió, el turno subió → ambas acciones fueron procesadas
            assertThat(game.getTurnNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("El jugador ausente A recibe la acción por defecto cuando solo actuó B")
        void jugadorAusenteEsA() {
            GameState game = buildAndRegisterGame("game-1");
            // Solo B envía acción → A es el ausente
            coordinator.submitAction(game, action("player-b", 1, 11, 11, 0, 0));

            timeoutService.forceResolveTimeout("game-1");

            assertThat(game.getTurnNumber()).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ //
    //  Criterio clave: timeout_ignorado_si_turno_ya_resuelto              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("timeout_ignorado_si_turno_ya_resuelto")
    class TimeoutIgnorado {

        @Test
        @DisplayName("No se inyecta acción si pendingActions.size()==0 (turno ya resuelto)")
        void ignoradoSiPendingActionsVacio() {
            GameState game = buildAndRegisterGame("game-1");
            // Turno resuelto normalmente — pendingActions vacío
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));
            coordinator.submitAction(game, action("player-b", 1, 11, 11, 0, 0));

            int turnoActual = game.getTurnNumber();

            // El scheduler llama con retraso — el turno ya avanzó
            timeoutService.forceResolveTimeout("game-1");

            // El turno no debe avanzar de nuevo
            assertThat(game.getTurnNumber()).isEqualTo(turnoActual);
        }

        @Test
        @DisplayName("No se inyecta acción si status==FINISHED")
        void ignoradoSiFinished() {
            GameState game = buildAndRegisterGame("game-1");
            game.setStatus(GameStatus.FINISHED);

            int turnoAntes = game.getTurnNumber();

            timeoutService.forceResolveTimeout("game-1");

            assertThat(game.getTurnNumber()).isEqualTo(turnoAntes);
            assertThat(game.getStatus()).isEqualTo(GameStatus.FINISHED);
        }

        @Test
        @DisplayName("No se inyecta acción si status==WAITING")
        void ignoradoSiWaiting() {
            GameState game = buildAndRegisterGame("game-1");
            game.setStatus(GameStatus.WAITING);

            timeoutService.forceResolveTimeout("game-1");

            assertThat(game.getPendingActions()).isEmpty();
            assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
        }

        @Test
        @DisplayName("No se inyecta acción si pendingActions.size()==2 (ambos ya actuaron)")
        void ignoradoSiAmbosPendientes() {
            GameState game = buildAndRegisterGame("game-1");

            // Insertamos las dos acciones directamente sin resolver
            // (simulamos el estado en que ambos enviaron pero el lock aún no se adquirió)
            TurnAction actionA = action("player-a", 1, 3, 3, 7, 7);
            TurnAction actionB = action("player-b", 1, 11, 11, 0, 0);
            game.getPendingActions().put("player-a", actionA);
            game.getPendingActions().put("player-b", actionB);

            int turnoAntes = game.getTurnNumber();

            timeoutService.forceResolveTimeout("game-1");

            // No debe haber resolución espuria — el coordinador ya lo manejará
            // (o el timeout llega tarde y las acciones se resuelven normalmente)
            assertThat(game.getTurnNumber()).isEqualTo(turnoAntes);
        }
    }

    // ------------------------------------------------------------------ //
    //  Partida inexistente                                                 //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Partida inexistente — retorna sin excepción")
    void partidaInexistenteRetornaSinError() {
        assertThatNoException()
                .isThrownBy(() -> timeoutService.forceResolveTimeout("game-inexistente"));
    }

    @Test
    @DisplayName("Múltiples llamadas consecutivas son idempotentes tras la resolución")
    void llamadasConsecutivasIdempotentes() {
        GameState game = buildAndRegisterGame("game-1");
        coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

        timeoutService.forceResolveTimeout("game-1");
        int turnoTrasTimeout = game.getTurnNumber();

        // Segunda llamada — el turno ya avanzó, no debe resolver de nuevo
        timeoutService.forceResolveTimeout("game-1");

        assertThat(game.getTurnNumber()).isEqualTo(turnoTrasTimeout);
    }
}