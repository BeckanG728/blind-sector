package es.game.blindsector.snapshot.factory;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.utils.GridUtils;
import es.game.blindsector.snapshot.dto.PositionDTO;
import es.game.blindsector.snapshot.dto.SnapshotDTO;
import es.game.blindsector.turn.domain.TurnResolutionResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SnapshotFactory {

    /**
     * Construye el SnapshotDTO personalizado para el jugador solicitante.
     *
     * @param game               estado de la partida en memoria
     * @param result             resultado del turno resuelto
     * @param requestingPlayerId jugador para quien se genera el snapshot
     * @return SnapshotDTO con la perspectiva de ese jugador
     */
    public SnapshotDTO buildSnapshot(
            GameState game,
            TurnResolutionResult result,
            String requestingPlayerId
    ) {

        boolean isPlayerA = requestingPlayerId.equals(game.getPlayerA().getPlayerId());

        PlayerState me = isPlayerA ? game.getPlayerA() : game.getPlayerB();
        PlayerState enemy = isPlayerA ? game.getPlayerB() : game.getPlayerA();

        // Datos propios — se exponen con exactitud
        int myHp = me.getHp();
        int myCol = me.getPosCol();
        int myRow = me.getPosRow();
        String myRegion = GridUtils.resolveRegion(myCol, myRow).toLabel();

        // Región del rival — NUNCA coordenadas exactas
        String enemyRegion = GridUtils.resolveRegion(enemy.getPosCol(), enemy.getPosRow()).toLabel();

        // Daño y resultado de impacto desde la perspectiva de este jugador
        int damageReceived = isPlayerA ? result.getDamageToA() : result.getDamageToB();
        String hitOnMe = isPlayerA ? result.getHitResultB().name() : result.getHitResultA().name();
        String hitOnEnemy = isPlayerA ? result.getHitResultA().name() : result.getHitResultB().name();

        // Área de impacto que el enemigo lanzó sobre mí (celdas que me cubrieron)
        List<PositionDTO> impactAreaReceived = isPlayerA
                ? toPositionDTOList(result.getImpactAreaOfB())
                : toPositionDTOList(result.getImpactAreaOfA());

        // Área de impacto que yo lancé (útil para animaciones propias)
        List<PositionDTO> myAttackArea = isPlayerA
                ? toPositionDTOList(result.getImpactAreaOfA())
                : toPositionDTOList(result.getImpactAreaOfB());

        // Ganador — null si la partida sigue, "draw" si empate, playerId si hay ganador
        String winnerId = result.isGameOver()
                ? (result.getWinnerId() == null ? "draw" : result.getWinnerId())
                : null;

        return SnapshotDTO.builder()
                .gameId(game.getGameId())
                .turn(game.getTurnNumber())
                .status(game.getStatus().name())
                .myHp(myHp)
                .myCol(myCol)
                .myRow(myRow)
                .myRegion(myRegion)
                .enemyRegion(enemyRegion)
                .impactAreaReceived(impactAreaReceived)
                .hitOnMe(hitOnMe)
                .damageReceived(damageReceived)
                .myAttackArea(myAttackArea)
                .hitOnEnemy(hitOnEnemy)
                .winnerId(winnerId)
                .build();
    }

    // ── helpers privados ────────────────────────────────────────────────────
    private List<PositionDTO> toPositionDTOList(ImpactArea area) {
        if (area == null) return List.of();
        return area.getPositions().stream()
                .map(p -> new PositionDTO(p.col(), p.row()))
                .toList();
    }
}
