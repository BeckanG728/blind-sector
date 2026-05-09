package es.game.blindsector.infrastructure.memory;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.shared.enums.GameStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActiveGamesRegistryTest {

    private ActiveGamesRegistry registry = new ActiveGamesRegistry();

    @Test
    void save_and_findById() {
        GameState game = new GameState("game-1", GameStatus.ACTIVE, 1, null, null);
        registry.save(game);
        assertTrue(registry.findById("game-1").isPresent());
    }

    @Test
    void findById_inexistente() {
        assertTrue(registry.findById("game-null").isEmpty());
    }

    @Test
    void remove() {
        GameState game = new GameState("game-2", GameStatus.ACTIVE, 1, null, null);
        registry.save(game);
        registry.remove("game-2");
        assertTrue(registry.findById("game-2").isEmpty());
    }

    @Test
    void getAllActive() {
        registry.save(new GameState("game-01", GameStatus.ACTIVE, 1, null, null));
        registry.save(new GameState("game-02", GameStatus.ACTIVE, 1, null, null));
        assertEquals(2, registry.getAllActive().size());
    }
}