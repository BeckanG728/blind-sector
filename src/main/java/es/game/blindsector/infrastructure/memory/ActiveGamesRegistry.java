package es.game.blindsector.infrastructure.memory;

import es.game.blindsector.game.domain.GameState;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveGamesRegistry {
    private final ConcurrentHashMap<String, GameState> activeGames = new ConcurrentHashMap<>();

    public void save(GameState game){
        activeGames.put(game.getGameId(), game);
    }

    public Optional<GameState> findById(String gameId){
        return Optional.ofNullable(activeGames.get(gameId));
    }

    public void remove(String gameId){
        activeGames.remove(gameId);
    }

    public Collection<GameState> getAllActive(){
        return activeGames.values();
    }
}
