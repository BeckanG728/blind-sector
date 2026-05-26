package es.game.blindsector.shared.constants;

import es.game.blindsector.game.domain.Position;

import java.util.List;

/**
 * Coordenadas de spawn para el tablero 15×15 (índices 0–14).
 *
 * <p>Las cuatro esquinas son:
 * <ul>
 *   <li>TOP_LEFT     — (col=0,  row=0)</li>
 *   <li>TOP_RIGHT    — (col=14, row=0)</li>
 *   <li>BOTTOM_LEFT  — (col=0,  row=14)</li>
 *   <li>BOTTOM_RIGHT — (col=14, row=14)</li>
 * </ul>
 */
public final class SpawnConstants {

    private SpawnConstants() { /* utilidad estática */ }

    public static final Position SPAWN_TOP_LEFT = new Position(0, 0);
    public static final Position SPAWN_TOP_RIGHT = new Position(14, 0);
    public static final Position SPAWN_BOTTOM_LEFT = new Position(0, 14);
    public static final Position SPAWN_BOTTOM_RIGHT = new Position(14, 14);

    /**
     * Lista inmutable con las cuatro esquinas; orden fijo para el sorteo.
     */
    public static final List<Position> ALL_SPAWNS = List.of(
            SPAWN_TOP_LEFT,
            SPAWN_TOP_RIGHT,
            SPAWN_BOTTOM_LEFT,
            SPAWN_BOTTOM_RIGHT
    );
}
