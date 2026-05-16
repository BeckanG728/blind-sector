package es.game.blindsector.persistence.mapper;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.persistence.entity.GameEntity;

public class GameMapper {

    /**
     * Convierte de GameState a GameEntity para el INSERT inicial (LobbyService.create()).
     * Nota: 'turnsPlayed' por defecto inicia en 0 (según entidad y DB)
     * y 'createdAt' se maneja automáticamente mediante @PrePersist.
     */
    public static GameEntity toEntity(GameState gameState) {
        if (gameState == null) {
            return null;
        }

        GameEntity entity = new GameEntity();
        entity.setGameId(gameState.getGameId());

        // Mapeamos los IDs de los jugadores desde PlayerState
        if (gameState.getPlayerA() != null) {
            entity.setPlayerAId(gameState.getPlayerA().getPlayerId());
        }
        if (gameState.getPlayerB() != null) {
            entity.setPlayerBId(gameState.getPlayerB().getPlayerId());
        }

        // Convertimos el ENUM GameStatus a String para la base de datos
        if (gameState.getStatus() != null) {
            entity.setStatus(gameState.getStatus().name());
        }

        return entity;
    }

    /**
     * Modifica la entidad existente para reflejar el UPDATE final al terminar la partida
     * (GameLifecycleService.finalize()).
     */
    public static GameEntity toFinishedEntity(GameEntity entity, String winnerId, int turnsPlayed) {
        if (entity == null) {
            return null;
        }

        entity.setWinnerId(winnerId);
        entity.setTurnsPlayed(turnsPlayed);

        // Aseguramos que pase a estado FINISHED o el string correspondiente en tu GameStatus
        entity.setStatus("FINISHED");

        return entity;
    }
}
