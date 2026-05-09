package es.game.blindsector.shared.enums;

public enum GameErrorCode {
    // 404
    GAME_NOT_FOUND,
    
    // 400
    INVALID_MOVE,
    OUT_OF_BOUNDS,
    INVALID_ATTACK,
    STALE_TURN,
    DUPLICATE_ACTION,
    SELF_JOIN,

    // 409
    GAME_NOT_ACTIVE,
    GAME_FULL;
}
