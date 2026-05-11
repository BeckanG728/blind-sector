package es.game.blindsector.infrastructure.memory;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.exception.GameException;
import org.springframework.stereotype.Component;

@Component
public class GameMemoryStore {
    private final ActiveGamesRegistry registry;

    public GameMemoryStore(ActiveGamesRegistry registry) {
        this.registry = registry;
    }

    //Recupera el estado de una partida o lanza una excepción tipada si no existe.
    //Esto evita que los servicios de dominio tengan que lidiar con Optional.
    public GameState getOrThrow(String gameId){
        return registry.findById(gameId)
                .orElseThrow(() -> new GameException(GameErrorCode.GAME_NOT_FOUND, "Partida con id " + gameId + " no encontrada en memoria."));
    }

    //Guarda o actualiza partida en el registro
    public void save(GameState game){
        registry.save(game);
    }

    //Elimina una partida por su identificador
    public void remove(String gameId){
        registry.remove(gameId);
    }
}
