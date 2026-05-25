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
     * pero MANTIENE el objeto en memoria temporalmente para que los clientes lean el final.
     */
    @Transactional
    public void finalize(String gameId, String winnerId, int turnsPlayed) {
        // 1. Criterio de aceptación: Si winnerId es null, se debe almacenar la cadena "draw"
        String finalWinner = (winnerId == null) ? "draw" : winnerId;

        // 2. Buscamos la entidad existente para poder actualizarla
        GameEntity existingEntity = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la partida en la base de datos con el ID: " + gameId));

        // 3. Modificamos la entidad usando el mapper especializado para el UPDATE final
        GameEntity finishedEntity = GameMapper.toFinishedEntity(existingEntity, finalWinner, turnsPlayed);

        // 4. Criterio de aceptación: Ejecuta el UPDATE en MySQL vía GameRepository
        gameRepository.save(finishedEntity);

        // 5. CORRECCIÓN: NO remover de inmediato para evitar romper el Polling del rival.
        // Se comenta esta línea para que permanezca accesible en el GameMemoryStore:
        // gameMemoryStore.remove(gameId);
    }
}