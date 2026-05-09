package es.game.blindsector.turn.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnAction {

    private String playerId;
    private Integer turn;
    private Integer moveToCol;
    private Integer moveToRow;
    private Integer attackCol;
    private Integer attackRow;
    private Long submittedAt;

    public TurnAction() {
    }

    public TurnAction(String playerId, Integer turn,
                      Integer moveToCol, Integer moveToRow,
                      Integer attackCol, Integer attackRow) {
        this.playerId = playerId;
        this.turn = turn;
        this.moveToCol = moveToCol;
        this.moveToRow = moveToRow;
        this.attackCol = attackCol;
        this.attackRow = attackRow;
        this.submittedAt = System.currentTimeMillis();
    }
}
