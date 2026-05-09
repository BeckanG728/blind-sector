package es.game.blindsector.shared.exception;

import lombok.Getter;

@Getter
public class GameException extends RuntimeException {

    private final String errorCode;

    public GameException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
