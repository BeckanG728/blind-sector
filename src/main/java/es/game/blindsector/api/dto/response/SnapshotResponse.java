package es.game.blindsector.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import es.game.blindsector.domain.game.Position;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Snapshot del estado del turno desde la perspectiva de UN jugador.
 * Nunca expone la posición exacta del rival, solo su región (enemyRegion).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapshotResponse {

    private String gameId;
    private Integer turn;
    private String status;

    // Estado propio
    private Integer myHp;
    private Integer myCol;
    private Integer myRow;
    private String myRegion;

    // Información del rival — SOLO región, nunca coordenadas exactas
    private String enemyRegion;

    // Ataque enemigo recibido
    private List<Position> impactAreaReceived;
    private String hitOnMe;
    private Integer damageReceived;

    // Mi ataque
    private List<Position> myAttackArea;
    private String hitOnEnemy;

    // Fin de partida: null si continúa, "draw" si empate, o playerId del ganador
    private String winnerId;
}
