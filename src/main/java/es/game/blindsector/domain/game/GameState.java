package es.game.blindsector.domain.game;

import es.game.blindsector.domain.player.PlayerState;
import es.game.blindsector.domain.turn.TurnAction;
import es.game.blindsector.domain.turn.TurnResolutionResult;
import es.game.blindsector.shared.enums.GameStatus;
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

    /**
     * Spawn reservado para playerB en el momento en que playerA crea la partida.
     * Se consume cuando playerB hace join y se puede dejar null tras ese momento.
     */
    private Position pendingSpawnB;

    // No expuesto con setter para evitar reemplazos accidentales
    private final ConcurrentHashMap<String, TurnAction> pendingActions = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private Long firstActionReceivedAt;

    /**
     * Resultado del último turno resuelto. Persiste en memoria para que
     * SnapshotService pueda construir el SnapshotResponse en modo polling,
     * sin necesidad de recalcular nada. Se sobreescribe en cada resolución.
     */
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
