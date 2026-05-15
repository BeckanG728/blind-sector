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
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnCoordinatorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de TurnCoordinator.
 * Sin Spring context — instancias directas de todas las dependencias.

 * Se usa LockExecutor real para validar el comportamiento concurrente.
 * TurnResolver y validadores también son instancias reales (no mocks)
 * para garantizar que el flujo integrado es correcto.
 */
class TurnCoordinatorTest {

    private TurnCoordinator coordinator;
    private GameState game;

    @BeforeEach
    void setUp() {
        coordinator = new TurnCoordinator(
                new TurnValidator(),
                new MovementValidator(),
                new AttackValidator(),
                new TurnResolver(new MovementEngine(), new ImpactResolver(), new DamageCalculator()),
                new LockExecutor(new GameLockManager())
        );
        game = buildGame();
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private GameState buildGame() {
        PlayerState playerA = new PlayerState("player-a", 2, 2);
        PlayerState playerB = new PlayerState("player-b", 12, 12);
        GameState g = new GameState("game-1",GameStatus.ACTIVE,1, playerA, playerB);
        return g;
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
    //  Flujo principal — primera acción                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Primera acción del turno")
    class PrimeraAccion {

        @Test
        @DisplayName("Retorna waiting=true cuando solo un jugador ha actuado")
        void primeraAccionRetornaWaiting() {
            TurnAction actionA = action("player-a", 1, 3, 3, 7, 7);

            TurnCoordinatorResult result = coordinator.submitAction(game, actionA);

            assertThat(result.getWaiting()).isTrue();
            assertThat(result.getResolved()).isFalse();
            assertThat(result.getResolutionResult()).isNull();
        }

        @Test
        @DisplayName("La acción queda registrada en pendingActions")
        void accionRegistradaEnPending() {
            TurnAction actionA = action("player-a", 1, 3, 3, 7, 7);

            coordinator.submitAction(game, actionA);

            assertThat(game.getPendingActions()).containsKey("player-a");
        }

        @Test
        @DisplayName("firstActionReceivedAt se registra con la primera acción")
        void timestampRegistrado() {
            long antes = System.currentTimeMillis();
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));
            long despues = System.currentTimeMillis();

            assertThat(game.getFirstActionReceivedAt())
                    .isBetween(antes, despues);
        }

        @Test
        @DisplayName("firstActionReceivedAt NO se sobreescribe con la segunda acción")
        void timestampNoSobreescrito() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));
            long timestampPrimeraAccion = game.getFirstActionReceivedAt();

            coordinator.submitAction(game, action("player-b", 1, 11, 11, 0, 0));

            // Tras la resolución se limpia a 0 — pero nunca fue sobreescrito
            // durante la segunda acción; verificamos que la resolución lo limpia
            assertThat(game.getFirstActionReceivedAt()).isEqualTo(0L);
            assertThat(timestampPrimeraAccion).isGreaterThan(0L);
        }
    }

    // ------------------------------------------------------------------ //
    //  Flujo principal — segunda acción (resolución)                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Segunda acción — resolución del turno")
    class SegundaAccion {

        @Test
        @DisplayName("Retorna resolved=true cuando ambos jugadores han actuado")
        void segundaAccionRetornaResolved() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

            TurnCoordinatorResult result = coordinator.submitAction(
                    game, action("player-b", 1, 11, 11, 0, 0));

            assertThat(result.getResolved()).isTrue();
            assertThat(result.getWaiting()).isFalse();
            assertThat(result.getResolutionResult()).isNotNull();
        }

        @Test
        @DisplayName("pendingActions queda vacío tras la resolución")
        void pendingActionsLimpioTrasResolucion() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));
            coordinator.submitAction(game, action("player-b", 1, 11, 11, 0, 0));

            assertThat(game.getPendingActions()).isEmpty();
        }

        @Test
        @DisplayName("Estado pasa a ACTIVE si la partida continúa")
        void estadoActivoSiContinua() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 0, 0));
            coordinator.submitAction(game, action("player-b", 1, 11, 11, 14, 14));

            // Ambos atacan lejos → nadie muere → estado ACTIVE
            assertThat(game.getStatus()).isEqualTo(GameStatus.ACTIVE);
        }

        @Test
        @DisplayName("Estado pasa a FINISHED si la partida termina")
        void estadoFinishedSiTermina() {
            // B con 25 HP, A lo golpea moviéndose → 25 de daño → B a 0
            PlayerState playerA = new PlayerState("player-a", 2, 2);
            PlayerState playerB = new PlayerState("player-b", 8, 8);
            GameState gameConBajoHp = new GameState("game-2",GameStatus.ACTIVE,1, playerA, playerB);

            coordinator.submitAction(gameConBajoHp, action("player-a", 1, 3, 3, 8, 8));
            coordinator.submitAction(gameConBajoHp, action("player-b", 1, 8, 8, 0, 0));

            assertThat(gameConBajoHp.getStatus()).isEqualTo(GameStatus.FINISHED);
        }

        @Test
        @DisplayName("El turno se incrementa tras la resolución")
        void turnoIncrementado() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 0, 0));
            coordinator.submitAction(game, action("player-b", 1, 11, 11, 14, 14));

            assertThat(game.getTurnNumber()).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------ //
    //  Rechazos por validación                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Rechazos por validación")
    class Rechazos {

        @Test
        @DisplayName("DUPLICATE_ACTION si el mismo jugador envía dos acciones")
        void duplicateAction() {
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));

            GameException ex = catchThrowableOfType(
                    () -> coordinator.submitAction(game, action("player-a", 1, 4, 4, 8, 8)),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.DUPLICATE_ACTION);
        }

        @Test
        @DisplayName("STALE_TURN si el turno declarado no coincide")
        void staleTurn() {
            GameException ex = catchThrowableOfType(
                    () -> coordinator.submitAction(game, action("player-a", 99, 3, 3, 7, 7)),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.STALE_TURN);
        }

        @Test
        @DisplayName("GAME_NOT_ACTIVE si la partida no está en estado ACTIVE")
        void gameNotActive() {
            game.setStatus(GameStatus.FINISHED);

            GameException ex = catchThrowableOfType(
                    () -> coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7)),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_ACTIVE);
        }

        @Test
        @DisplayName("OUT_OF_BOUNDS si el movimiento sale del tablero")
        void movimientoFueraDelTablero() {
            GameException ex = catchThrowableOfType(
                    () -> coordinator.submitAction(game, action("player-a", 1, -1, -1, 7, 7)),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.OUT_OF_BOUNDS);
        }

        @Test
        @DisplayName("INVALID_ATTACK si el punto de ataque está fuera del tablero")
        void ataqueInvalido() {
            GameException ex = catchThrowableOfType(
                    () -> coordinator.submitAction(game, action("player-a", 1, 3, 3, 15, 15)),
                    GameException.class
            );
            assertThat(ex.getErrorCode()).isEqualTo(GameErrorCode.INVALID_ATTACK);
        }

        @Test
        @DisplayName("Estado del juego no se modifica si la validación falla")
        void estadoIntactoSiFalla() {
            try {
                coordinator.submitAction(game, action("player-a", 99, 3, 3, 7, 7));
            } catch (GameException ignored) {}

            assertThat(game.getPendingActions()).isEmpty();
            assertThat(game.getStatus()).isEqualTo(GameStatus.ACTIVE);
            assertThat(game.getFirstActionReceivedAt()).isEqualTo(0L);
        }
    }

    // ------------------------------------------------------------------ //
    //  Concurrencia — doble resolución                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Concurrencia — el lock previene doble resolución")
    class Concurrencia {

        @Test
        @DisplayName("Dos threads enviando la acción del mismo jugador → solo una es aceptada")
        void soloUnaAccionAceptadaPorJugador() throws InterruptedException {
            int threads = 10;
            CountDownLatch latch   = new CountDownLatch(1);
            AtomicInteger aceptadas = new AtomicInteger(0);
            AtomicInteger rechazadas = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        latch.await();
                        coordinator.submitAction(game, action("player-a", 1, 3, 3, 7, 7));
                        aceptadas.incrementAndGet();
                    } catch (GameException e) {
                        rechazadas.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
            pool.shutdown();
            pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(aceptadas.get()).isEqualTo(1);
            assertThat(rechazadas.get()).isEqualTo(threads - 1);
        }

        @Test
        @DisplayName("Turno completo concurrente — TurnResolver se ejecuta exactamente una vez")
        void resolucionExactamenteUnaVez() throws InterruptedException {
            // Precargamos acción de A
            coordinator.submitAction(game, action("player-a", 1, 3, 3, 0, 0));
            int turnoAntes = game.getTurnNumber();

            // Múltiples threads intentan enviar la acción de B
            int threads = 10;
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger resoluciones = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        latch.await();
                        TurnCoordinatorResult r = coordinator.submitAction(
                                game, action("player-b", 1, 11, 11, 14, 14));
                        if (r.getResolved()) resoluciones.incrementAndGet();
                    } catch (GameException | InterruptedException ignored) {}
                });
            }

            latch.countDown();
            pool.shutdown();
            pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            // El turno solo se resolvió una vez
            assertThat(resoluciones.get()).isEqualTo(1);
            assertThat(game.getTurnNumber()).isEqualTo(turnoAntes + 1);
        }
    }
}
