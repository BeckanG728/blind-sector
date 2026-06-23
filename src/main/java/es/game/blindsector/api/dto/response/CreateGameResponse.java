package es.game.blindsector.api.dto.response;

import es.game.blindsector.shared.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateGameResponse {
    private String gameId;
    private String playerAId;
    private GameStatus status;
    /**
     * Columna inicial asignada aleatoriamente a playerA.
     */
    private Integer spawnCol;
    /**
     * Fila inicial asignada aleatoriamente a playerA.
     */
    private Integer spawnRow;
}
