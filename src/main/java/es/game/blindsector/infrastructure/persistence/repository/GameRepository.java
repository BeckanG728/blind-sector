package es.game.blindsector.infrastructure.persistence.repository;

import es.game.blindsector.infrastructure.persistence.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<GameEntity, String> {
    // No requiere métodos adicionales
}