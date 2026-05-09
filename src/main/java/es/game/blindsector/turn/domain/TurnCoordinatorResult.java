package es.game.blindsector.turn.domain;

import lombok.Getter;

@Getter
public class TurnCoordinatorResult {

    private final Boolean resolved;
    private final Boolean waiting;
    private final TurnResolutionResult resolutionResult; // null cuando waiting == true

    private TurnCoordinatorResult(boolean resolved, boolean waiting,
                                  TurnResolutionResult resolutionResult) {
        this.resolved = resolved;
        this.waiting = waiting;
        this.resolutionResult = resolutionResult;
    }

    public static TurnCoordinatorResult waiting() {
        return new TurnCoordinatorResult(false, true, null);
    }

    public static TurnCoordinatorResult resolved(TurnResolutionResult result) {
        return new TurnCoordinatorResult(true, false, result);
    }
}
