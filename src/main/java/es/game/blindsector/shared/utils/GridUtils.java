package es.game.blindsector.shared.utils;

import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.game.domain.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades estáticas para cálculos espaciales del tablero 15x15 (índices 0-14).
 * El tablero se divide en 9 regiones de 5x5 celdas (3 columnas × 3 filas de regiones).
 */
public final class GridUtils {

    public static final int BOARD_SIZE = 15;
    public static final int REGION_SIZE = 5;

    private GridUtils() {
    }

    /**
     * Distancia de Chebyshev entre dos celdas.
     * max(|dc|, |dr|)
     */
    public static Integer chebyshevDistance(int col1, int row1, int col2, int row2) {
        return Math.max(Math.abs(col1 - col2), Math.abs(row1 - row2));
    }

    /**
     * Devuelve la Region a la que pertenece la celda (col, row).
     * El tablero tiene 3 columnas y 3 filas de regiones, cada una de 5x5 celdas.
     */
    public static Region resolveRegion(int col, int row) {
        int regionCol = col / REGION_SIZE;
        int regionRow = row / REGION_SIZE;
        return new Region(regionCol, regionRow);
    }

    /**
     * Devuelve true si la celda (col, row) está dentro del tablero [0, 14].
     */
    public static Boolean isInBounds(int col, int row) {
        return col >= 0 && col < BOARD_SIZE && row >= 0 && row < BOARD_SIZE;
    }

    /**
     * Devuelve el área de impacto 3×3 centrada en (centerCol, centerRow),
     * filtrando las celdas que caen fuera del tablero.
     */
    public static ImpactArea computeImpactArea(int centerCol, int centerRow) {
        List<Position> positions = new ArrayList<>();
        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                int c = centerCol + dc;
                int r = centerRow + dr;
                if (isInBounds(c, r)) {
                    positions.add(new Position(c, r));
                }
            }
        }
        return new ImpactArea(positions);
    }
}
