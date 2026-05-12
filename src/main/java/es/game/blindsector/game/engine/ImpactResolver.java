package es.game.blindsector.game.engine;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.HitResult;
import es.game.blindsector.shared.utils.GridUtils;
import org.springframework.stereotype.Component;

@Component
public class ImpactResolver {

    public ImpactArea computeImpactArea(int centerCol, int centerRow) {
        return GridUtils.computeImpactArea(centerCol, centerRow);
    }

    public HitResult resolveImpact(int attackCol, int attackRow, PlayerState target) {
        Position center = new Position(attackCol, attackRow);
        Position targetPos = new Position(target.getPosCol(), target.getPosRow());

        if (targetPos.equals(center)) {
            return HitResult.DIRECT_HIT;
        }

        ImpactArea area = computeImpactArea(attackCol, attackRow);
        if (area.contains(targetPos)) {
            return HitResult.HIT;
        }

        return HitResult.MISS;
    }
}