package es.game.blindsector.application.service;

import es.game.blindsector.api.dto.response.SnapshotResponse;
import es.game.blindsector.domain.game.GameState;
import es.game.blindsector.domain.game.ImpactArea;
import es.game.blindsector.domain.game.Position;
import es.game.blindsector.domain.player.PlayerState;
import es.game.blindsector.domain.turn.TurnResolutionResult;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.utils.GridUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SnapshotService {

    private final GameMemoryStore gameMemoryStore;

    public SnapshotService(GameMemoryStore gameMemoryStore) {
        this.gameMemoryStore = gameMemoryStore;
    }

    public Optional<SnapshotResponse> getSnapshot(String gameId, String playerId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        if (game.getPendingActions().size() == 1) {
            return Optional.empty();
        }

        if (game.getLastResolutionResult() == null) {
            return Optional.empty();
        }

        return Optional.of(buildSnapshot(game, game.getLastResolutionResult(), playerId));
    }

    public Optional<SnapshotResponse> getLastSnapshot(String gameId, String playerId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        if (game.getLastResolutionResult() == null) {
            return Optional.empty();
        }

        return Optional.of(buildSnapshot(game, game.getLastResolutionResult(), playerId));
    }

    public GameStatus getGameStatus(String gameId) {
        return gameMemoryStore.getOrThrow(gameId).getStatus();
    }

    private SnapshotResponse buildSnapshot(GameState game, TurnResolutionResult result, String requestingPlayerId) {
        boolean isPlayerA = requestingPlayerId.equals(game.getPlayerA().getPlayerId());

        PlayerState me = isPlayerA ? game.getPlayerA() : game.getPlayerB();
        PlayerState enemy = isPlayerA ? game.getPlayerB() : game.getPlayerA();

        String myRegion = GridUtils.resolveRegion(me.getPosCol(), me.getPosRow()).toLabel();
        String enemyRegion = GridUtils.resolveRegion(enemy.getPosCol(), enemy.getPosRow()).toLabel();

        int damageReceived = isPlayerA ? result.getDamageToA() : result.getDamageToB();
        String hitOnMe = isPlayerA ? result.getHitResultB().name() : result.getHitResultA().name();
        String hitOnEnemy = isPlayerA ? result.getHitResultA().name() : result.getHitResultB().name();

        List<Position> impactAreaReceived = isPlayerA
                ? toPositionDTOList(result.getImpactAreaOfB())
                : toPositionDTOList(result.getImpactAreaOfA());

        List<Position> myAttackArea = isPlayerA
                ? toPositionDTOList(result.getImpactAreaOfA())
                : toPositionDTOList(result.getImpactAreaOfB());

        String winnerId = result.isGameOver()
                ? (result.getWinnerId() == null ? "draw" : result.getWinnerId())
                : null;

        return SnapshotResponse.builder()
                .gameId(game.getGameId())
                .turn(game.getTurnNumber())
                .status(game.getStatus().name())
                .myHp(me.getHp())
                .myCol(me.getPosCol())
                .myRow(me.getPosRow())
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

    private List<Position> toPositionDTOList(ImpactArea area) {
        if (area == null) return List.of();
        return area.getPositions().stream()
                .map(p -> new Position(p.col(), p.row()))
                .toList();
    }
}
