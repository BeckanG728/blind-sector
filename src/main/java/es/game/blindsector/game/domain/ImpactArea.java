package es.game.blindsector.game.domain;

import lombok.Getter;

import java.util.List;

@Getter
public class ImpactArea {

    private final List<Position> positions;

    public ImpactArea(List<Position> positions) {
        this.positions = List.copyOf(positions);
    }

    public boolean contains(Position position) {
        return positions.contains(position);
    }
}
