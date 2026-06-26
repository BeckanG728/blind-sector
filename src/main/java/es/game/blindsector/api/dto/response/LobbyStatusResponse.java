package es.game.blindsector.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import es.game.blindsector.shared.enums.GameStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LobbyStatusResponse {
    private String gameId;
    private String playerAId;
    private String playerBId;   // null hasta que alguien se una
    private GameStatus status;
}
