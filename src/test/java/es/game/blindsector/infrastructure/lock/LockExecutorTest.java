package es.game.blindsector.infrastructure.lock;

import es.game.blindsector.game.domain.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class LockExecutorTest {

    private GameLockManager lockManager;
    private LockExecutor lockExecutor;
    private GameState mockGame;
    private ReentrantLock realLock;

    @BeforeEach
    void setUp() {
        lockManager = Mockito.mock(GameLockManager.class);
        lockExecutor = new LockExecutor(lockManager);
        mockGame = Mockito.mock(GameState.class);

        // Usamos un lock real para probar el comportamiento de concurrencia real
        realLock = new ReentrantLock();
        when(lockManager.getLock(mockGame)).thenReturn(realLock);
    }

    @Test
    void executeWithLock_shouldExecuteSequentiallyWhenConcurrent() throws InterruptedException {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch thread1Started = new CountDownLatch(1);
        CountDownLatch thread2CanFinish = new CountDownLatch(1);
        CountDownLatch allThreadsFinished = new CountDownLatch(2);

        // Hilo 1: Entrará primero, adquirirá el lock y se quedará esperando dentro del bloque seguro
        Thread thread1 = new Thread(() -> {
            lockExecutor.executeWithLock(mockGame, () -> {
                executionOrder.add("THREAD_1_START");
                thread1Started.countDown(); // Avisa al hilo principal que ya tiene el lock
                try {
                    // Espera simulando que está haciendo lógica compleja de juego
                    thread2CanFinish.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                executionOrder.add("THREAD_1_END");
                return null;
            });
            allThreadsFinished.countDown();
        });

        // Hilo 2: Intenta entrar mientras Hilo 1 tiene el lock
        Thread thread2 = new Thread(() -> {
            try {
                // Esperamos un instante a que el Hilo 1 realmente tome el control del lock
                thread1Started.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            lockExecutor.executeWithLock(mockGame, () -> {
                executionOrder.add("THREAD_2_EXECUTE");
                return null;
            });
            allThreadsFinished.countDown();
        });

        // Arrancamos ambos hilos
        thread1.start();
        thread2.start();

        // Esperamos a que el Hilo 1 esté firmemente aposentado dentro de su ejecución con lock
        thread1Started.await(1, TimeUnit.SECONDS);

        // Liberamos el cerrojo del test para que el Hilo 1 pueda terminar
        thread2CanFinish.countDown();

        // Esperamos a que ambos terminen de ejecutarse por completo
        boolean completed = allThreadsFinished.await(3, TimeUnit.SECONDS);
        assertTrue(completed, "Los hilos tardaron demasiado, posible deadlock masivo.");

        // Verificacion importante:
        // A pesar de ejecutarse en paralelo, THREAD_2_EXECUTE no puede estar entre START y END de THREAD_1
        // El orden estricto debe ser secuencial:
        assertEquals("THREAD_1_START", executionOrder.get(0));
        assertEquals("THREAD_1_END", executionOrder.get(1));
        assertEquals("THREAD_2_EXECUTE", executionOrder.get(2));
    }

    @Test
    void executeWithLock_shouldReleaseLockEvenWhenExceptionIsThrown() {
        assertThrows(RuntimeException.class, () -> {
            lockExecutor.executeWithLock(mockGame, () -> {
                throw new RuntimeException("Simulated error in game logic");
            });
        });

        // Verificamos que el lock no se ha quedado bloqueado (fuga de lock)
        assertFalse(realLock.isLocked(), "El lock quedó retenido tras una excepción.");
    }
}