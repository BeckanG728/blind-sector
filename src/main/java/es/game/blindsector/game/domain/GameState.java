package es.game.blindsector.game.domain;

import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnResolutionResult;
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

    /** Resultado del último turno resuelto. Persiste en memoria para que
     *  SnapshotService pueda construir el SnapshotDTO en modo polling,
     *  sin necesidad de recalcular nada. Se sobreescribe en cada resolución. */
    private TurnResolutionResult lastResolutionResult;
    
    public GameState(String gameId, GameStatus status, int turnNumber,
                     PlayerState playerA, PlayerState playerB) {
        this.gameId = gameId;
        this.status = status;
        this.turnNumber = turnNumber;
        this.playerA = playerA;
        this.playerB = playerB;
    }
}
