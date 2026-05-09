package es.game.blindsector.turn.domain;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.game.domain.Region;
import es.game.blindsector.shared.enums.HitResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnResolutionResult {

    private Integer damageToA;
    private Integer damageToB;
    private HitResult hitResultA;   // resultado del ataque de A sobre B
    private HitResult hitResultB;   // resultado del ataque de B sobre A
    private Position finalPositionA;
    private Position finalPositionB;
    private Region regionOfBSeenByA;
    private Region regionOfASeenByB;
    private ImpactArea impactAreaOfA;   // área del ataque de A
    private ImpactArea impactAreaOfB;   // área del ataque de B
    private Boolean gameOver;
    private String winnerId; // null si empate o si la partida continúa

    public TurnResolutionResult() {
    }
}
