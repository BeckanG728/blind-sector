package es.game.blindsector.game.domain;

public record Region(
        Integer regionCol,
        Integer regionRow
) {
    /**
     * Devuelve una etiqueta legible como "A1", "B2", "C3".
     * La columna de región (0→A, 1→B, 2→C) y la fila (0→1, 1→2, 2→3).
     */
    public String toLabel() {
        return "" + (char) ('A' + regionCol) + (regionRow + 1);
    }
}
