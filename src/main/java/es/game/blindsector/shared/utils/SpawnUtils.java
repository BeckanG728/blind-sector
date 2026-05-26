package es.game.blindsector.shared.utils;

import es.game.blindsector.game.domain.Position;
import es.game.blindsector.shared.constants.SpawnConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilidad para asignar posiciones de spawn aleatorias y sin repetición.
 *
 * <p>Uso:
 * <pre>{@code
 *   List<Position> spawns = SpawnUtils.pickTwoSpawns();
 *   Position spawnA = spawns.get(0); // playerA
 *   Position spawnB = spawns.get(1); // playerB
 * }</pre>
 */
public final class SpawnUtils {

    private SpawnUtils() { /* utilidad estática */ }

    /**
     * Selecciona dos spawns distintos al azar de {@link SpawnConstants#ALL_SPAWNS}.
     *
     * @return lista de 2 posiciones únicas en orden aleatorio.
     */
    public static List<Position> pickTwoSpawns() {
        List<Position> shuffled = new ArrayList<>(SpawnConstants.ALL_SPAWNS);
        Collections.shuffle(shuffled);
        return List.of(shuffled.get(0), shuffled.get(1));
    }
}
