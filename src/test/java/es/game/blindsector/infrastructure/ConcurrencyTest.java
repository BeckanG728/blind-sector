package es.game.blindsector.infrastructure;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.domain.TurnAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    private GameState gameState;
    private String playerId;

    @BeforeEach
    void setUp() {
        // Inicializamos los estados de los jugadores (F0-05)
        PlayerState playerA = new PlayerState();
        playerA.setPlayerId("player-123");

        PlayerState playerB = new PlayerState();
        playerB.setPlayerId("player-456");

        // Construimos el GameState con su ReentrantLock real por defecto
        gameState = new GameState("game-001", GameStatus.ACTIVE, 1, playerA, playerB);

        playerId = playerA.getPlayerId();
    }

    @Test
    void testDobleResolucionSimultaneaPrevenidaPorLocking() throws InterruptedException {
        // Configuramos herramientas de concurrencia
        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch readyLatch = new CountDownLatch(numThreads); // Línea de salida
        CountDownLatch startLatch = new CountDownLatch(1);         // Disparo de salida
        CountDownLatch finishLatch = new CountDownLatch(numThreads); // Meta

        // Contadores atómicos para verificar los resultados del test de forma segura entre hilos
        AtomicInteger successfulResolutions = new AtomicInteger(0);
        AtomicInteger deduplicationRejections = new AtomicInteger(0);
        AtomicInteger unexpectedExceptions = new AtomicInteger(0);

        // Definimos la acción repetida que ambos hilos intentarán procesar
        TurnAction duplicateAction = new TurnAction();

        // Tarea concurrente que simula el flujo coordinado por LockExecutor y TurnCoordinator
        Runnable submitActionTask = () -> {
            readyLatch.countDown(); // El hilo avisa que está listo
            try {
                startLatch.await(); // Espera el disparo de salida simultáneo

                // --- INICIO DE ZONA CRÍTICA (Simulando LockExecutor P1-03) ---
                gameState.getLock().lock();
                try {
                    // Mecanismo de deduplicación comprobado DENTRO del lock (TurnCoordinator P2-06)
                    if (!gameState.getPendingActions().containsKey(playerId)) {

                        // Registramos la acción en el mapa concurrente
                        gameState.getPendingActions().put(playerId, duplicateAction);

                        // Simula la llamada a TurnResolver.resolve() (P2-04)
                        successfulResolutions.incrementAndGet();

                    } else {
                        // El segundo hilo en entrar debería caer aquí por la deduplicación
                        deduplicationRejections.incrementAndGet();
                    }
                } finally {
                    gameState.getLock().unlock();
                    // Fin de zona crítica
                }

            } catch (Exception e) {
                unexpectedExceptions.incrementAndGet();
                e.printStackTrace();
            } finally {
                finishLatch.countDown(); // El hilo terminó su ejecución
            }
        };

        // Lanzamos ambos hilos al executor
        for (int i = 0; i < numThreads; i++) {
            executor.submit(submitActionTask);
        }

        // Esperamos a que ambos hilos estén posicionados en startLatch.await()
        readyLatch.await(2, TimeUnit.SECONDS);

        // ¡FUEGO! Soltamos ambos hilos al mismo tiempo
        startLatch.countDown();

        // Esperamos a que ambos terminen de competir por el lock
        boolean cleanFinish = finishLatch.await(5, TimeUnit.SECONDS);

        // Apagamos el pool de hilos de forma ordenada
        executor.shutdown();

        // --- ASERCIONES (Criterios de Aceptación) ---
        assertTrue(cleanFinish, "El test tardó demasiado tiempo, posible deadlock.");
        assertEquals(0, unexpectedExceptions.get(), "Se lanzaron excepciones no controladas de manera síncrona.");

        // Criterio 2 y 3: Solo una acción es aceptada (resolve ejecuta 1 vez), la otra se rechaza por deduplicación
        assertEquals(1, successfulResolutions.get(), "TurnResolver.resolve() debió ejecutarse exactamente UNA vez.");
        assertEquals(1, deduplicationRejections.get(), "El segundo hilo debió ser rechazado por deduplicación (containsKey).");

        // Verificación del estado final del mapa
        assertEquals(1, gameState.getPendingActions().size(), "El mapa de acciones pendientes solo debe contener 1 elemento.");
        assertTrue(gameState.getPendingActions().containsKey(playerId));
    }
}
