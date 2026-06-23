package es.game.blindsector.api.controller;

import es.game.blindsector.api.dto.request.SubmitActionRequest;
import es.game.blindsector.api.dto.response.SnapshotResponse;
import es.game.blindsector.application.service.TurnSubmissionService;
import es.game.blindsector.domain.game.GameState;
import es.game.blindsector.domain.turn.TurnAction;
import es.game.blindsector.domain.turn.TurnCoordinatorResult;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.snapshot.factory.SnapshotFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P3-04 · TurnController — POST /api/turn/submit
 *
 * <p>Recibe la acción de turno de un jugador, la delega a
 * {@link TurnSubmissionService} y construye la respuesta adecuada:
 * <ul>
 *   <li>Si el rival aún no ha actuado → {@code 200 { "received": true, "waiting": true }}</li>
 *   <li>Si ambos han actuado y el turno se resolvió → {@code 200} con el
 *       {@link SnapshotResponse} del jugador que envió la acción.</li>
 * </ul>
 *
 * <p>Este controller no contiene lógica de negocio. Toda validación y
 * coordinación ocurre en {@code TurnSubmissionService} (P2-08).
 * Las {@link es.game.blindsector.shared.exception.GameException} se propagan
 * sin capturar hasta {@code GlobalExceptionHandler} (P1-09).</p>
 */
@RestController
@RequestMapping("/api/turn")
public class TurnController {

    private final TurnSubmissionService turnSubmissionService;
    private final SnapshotFactory snapshotFactory;
    private final GameMemoryStore gameMemoryStore;

    public TurnController(TurnSubmissionService turnSubmissionService,
                          SnapshotFactory snapshotFactory,
                          GameMemoryStore gameMemoryStore) {
        this.turnSubmissionService = turnSubmissionService;
        this.snapshotFactory = snapshotFactory;
        this.gameMemoryStore = gameMemoryStore;
    }

    /**
     * Recibe: {@code { gameId, playerId, turn, moveToCol, moveToRow, attackCol, attackRow }}
     *
     * @param request body deserializado por Jackson
     * @return 200 con cuerpo de espera o con {@link SnapshotResponse} del jugador
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody SubmitActionRequest request) {

        TurnAction action = new TurnAction(
                request.playerId(),
                request.turn(),
                request.moveToCol(),
                request.moveToRow(),
                request.attackCol(),
                request.attackRow()
        );

        TurnCoordinatorResult result = turnSubmissionService.submit(request.gameId(), action);

        if (Boolean.TRUE.equals(result.getWaiting())) {
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "waiting", true
            ));
        }

        // El turno fue resuelto: construimos el snapshot para este jugador
        GameState game = gameMemoryStore.getOrThrow(request.gameId());
        SnapshotResponse snapshot = snapshotFactory.buildSnapshot(
                game,
                result.getResolutionResult(),
                request.playerId()
        );

        return ResponseEntity.ok(snapshot);
    }
}
