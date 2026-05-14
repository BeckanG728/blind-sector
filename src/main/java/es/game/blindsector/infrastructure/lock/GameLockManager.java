package es.game.blindsector.infrastructure.lock;

import es.game.blindsector.game.domain.GameState;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class GameLockManager {

    //Extrae el ReentrantLock de la instancia de GameState
    public ReentrantLock getLock(GameState game) {
        return game.getLock();
    }
}
