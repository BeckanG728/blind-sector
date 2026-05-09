package es.game.blindsector.shared.exception;

import es.game.blindsector.shared.enums.GameErrorCode;
import lombok.Getter;

@Getter
public class GameException extends RuntimeException {

    private final GameErrorCode errorCode;

    public GameException(GameErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
