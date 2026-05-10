package es.game.blindsector.turn.domain;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.shared.enums.HitResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TurnResolutionResult {

    private Integer damageToA;
    private Integer damageToB;
    private HitResult hitResultA;   // resultado del ataque de A sobre B
    private HitResult hitResultB;   // resultado del ataque de B sobre A
    private Position finalPositionA;
    private Position finalPositionB;
    private String regionOfBSeenByA;
    private String regionOfASeenByB;
    private ImpactArea impactAreaOfA;   // área del ataque de A
    private ImpactArea impactAreaOfB;   // área del ataque de B
    private boolean gameOver;
    private String winnerId; // null si empate o si la partida continúa
}
