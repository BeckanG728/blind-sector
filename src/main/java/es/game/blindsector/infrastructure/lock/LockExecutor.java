package es.game.blindsector.infrastructure.lock;

import es.game.blindsector.game.domain.GameState;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class LockExecutor {

    private final GameLockManager lockManager;

    public LockExecutor(GameLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public <T> T executeWithLock(GameState game, Supplier<T> action) {
        ReentrantLock lock = lockManager.getLock(game);

        lock.lock();
        try {
            return action.get();
        } finally {
            // El bloque finally garantiza que el lock siempre se libera, asi lanze el RuntimeException
            lock.unlock();
        }
    }
}
