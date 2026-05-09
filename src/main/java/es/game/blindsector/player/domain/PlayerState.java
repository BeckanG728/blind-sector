package es.game.blindsector.player.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerState {

    private String playerId;
    private Integer posCol;
    private Integer posRow;
    private Integer hp;

    public PlayerState() {
        this.hp = 100;
    }

    public PlayerState(String playerId, int posCol, int posRow) {
        this.playerId = playerId;
        this.posCol = posCol;
        this.posRow = posRow;
        this.hp = 100;
    }
}
