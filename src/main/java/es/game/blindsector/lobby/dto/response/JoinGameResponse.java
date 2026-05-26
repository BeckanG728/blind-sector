package es.game.blindsector.lobby.dto.response;

import es.game.blindsector.shared.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinGameResponse {
    private String gameId;
    private String playerAId;
    private String playerBId;
    private GameStatus status;
    /** Columna inicial asignada aleatoriamente a playerB. */
    private Integer spawnCol;
    /** Fila inicial asignada aleatoriamente a playerB. */
    private Integer spawnRow;
}
