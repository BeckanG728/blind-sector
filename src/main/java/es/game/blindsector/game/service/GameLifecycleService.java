package es.game.blindsector.game.service;

import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.persistence.entity.GameEntity;
import es.game.blindsector.persistence.mapper.GameMapper;
import es.game.blindsector.persistence.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameLifecycleService {

    private final GameRepository gameRepository;
    private final GameMemoryStore gameMemoryStore;

    public GameLifecycleService(GameRepository gameRepository, GameMemoryStore gameMemoryStore) {
        this.gameRepository = gameRepository;
        this.gameMemoryStore = gameMemoryStore;
    }

    /**
     * Finaliza formalmente una partida guardando los resultados en MySQL
     * y liberando la memoria caché.
     */
    @Transactional
    public void finalize(String gameId, String winnerId, int turnsPlayed) {
        // 1. Criterio de aceptación: Si winnerId es null, se debe almacenar la cadena "draw"
        String finalWinner = (winnerId == null) ? "draw" : winnerId;

        // 2. Buscamos la entidad existente para poder actualizarla
        // (Si por alguna razón no existiera, se lanza una excepción)
        GameEntity existingEntity = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la partida en la base de datos con el ID: " + gameId));

        // 3. Modificamos la entidad usando el mapper especializado para el UPDATE final
        GameEntity finishedEntity = GameMapper.toFinishedEntity(existingEntity, finalWinner, turnsPlayed);

        // 4. Criterio de aceptación: Ejecuta el UPDATE en MySQL vía GameRepository
        gameRepository.save(finishedEntity);

        // 5. Criterio de aceptación: Después del UPDATE, llama a GameMemoryStore.remove(gameId)
        // Nota: Es idempotente por diseño en la memoria, si no existe no lanzará excepción.
        gameMemoryStore.remove(gameId);
    }
}