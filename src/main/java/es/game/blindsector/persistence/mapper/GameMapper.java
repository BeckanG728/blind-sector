package es.game.blindsector.persistence.mapper;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.persistence.entity.GameEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class GameMapper {

    /**
     * Mapea el estado inicial de una partida del dominio a la entidad JPA para el INSERT inicial.
     */
    public GameEntity toEntity(GameState game) {
        if (game == null) {
            return null;
        }

        GameEntity entity = new GameEntity();
        entity.setGameId(game.getGameId());

        // Extraemos los IDs de los jugadores desde el estado del dominio
        if (game.getPlayerA() != null) {
            entity.setPlayerAId(game.getPlayerA().getPlayerId());
        }
        if (game.getPlayerB() != null) {
            entity.setPlayerBId(game.getPlayerB().getPlayerId());
        }

        // Al crearse, el estado suele mapearse como String (ej: "ACTIVE" o "LOBBY")
        if (game.getStatus() != null) {
            entity.setStatus(game.getStatus().name());
        }

        entity.setTurnsPlayed(game.getTurnNumber());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setWinnerId(null); // No hay ganador al iniciar

        return entity;
    }

    /**
     * Modifica una entidad existente con los datos de cierre de la partida para el UPDATE final.
     */
    public GameEntity toFinishedEntity(GameEntity existingEntity, String winnerId, int turnsPlayed) {
        if (existingEntity == null) {
            return null;
        }

        existingEntity.setStatus("FINISHED");
        existingEntity.setWinnerId(winnerId); // Puede ser el id del jugador o "draw"
        existingEntity.setTurnsPlayed(turnsPlayed);

        return existingEntity;
    }
}
