package es.game.blindsector.snapshot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class SnapshotDTO {

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
    private List<PositionDTO> impactAreaReceived;
    private String hitOnMe;
    private Integer damageReceived;

    // Mi ataque
    private List<PositionDTO> myAttackArea;
    private String hitOnEnemy;

    // Fin de partida: null si continúa, "draw" si empate, o playerId del ganador
    private String winnerId;
}
