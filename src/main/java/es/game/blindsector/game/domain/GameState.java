package es.game.blindsector.game.domain;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.domain.TurnAction;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Setter
@NoArgsConstructor
public class GameState {

    private String gameId;
    private GameStatus status;
    private Integer turnNumber;
    private PlayerState playerA;
    private PlayerState playerB;

    // No expuesto con setter para evitar reemplazos accidentales
    private final ConcurrentHashMap<String, TurnAction> pendingActions = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private Long firstActionReceivedAt;
    
    public GameState(String gameId, GameStatus status, int turnNumber,
                     PlayerState playerA, PlayerState playerB) {
        this.gameId = gameId;
        this.status = status;
        this.turnNumber = turnNumber;
        this.playerA = playerA;
        this.playerB = playerB;
    }
}
