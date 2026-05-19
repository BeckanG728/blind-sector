package es.game.blindsector.game.dto;

/**
 * Body del {@code POST /api/turn/submit}.
 *
 * <p>Todos los campos son requeridos por el contrato de la actividad P3-04.
 * La deserialización la realiza Jackson; si falta algún campo el handler
 * recibirá {@code null} y la validación de negocio en {@code TurnSubmissionService}
 * lanzará la {@link es.game.blindsector.shared.exception.GameException} correspondiente.</p>
 */
public record SubmitActionRequest(
        String gameId,
        String playerId,
        Integer turn,
        Integer moveToCol,
        Integer moveToRow,
        Integer attackCol,
        Integer attackRow
) {
}
