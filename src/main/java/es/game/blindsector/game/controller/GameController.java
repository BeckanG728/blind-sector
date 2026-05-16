package es.game.blindsector.game.controller;

import es.game.blindsector.snapshot.dto.SnapshotDTO;
import es.game.blindsector.snapshot.service.SnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * P3-03 · GameController — polling y reconexión.
 *
 * <p>Expone dos endpoints REST de solo lectura que delegan completamente
 * en {@link SnapshotService}. Cualquier {@link es.game.blindsector.shared.exception.GameException}
 * propagada por el servicio es capturada por {@code GlobalExceptionHandler} (P1-09).</p>
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final SnapshotService snapshotService;

    public GameController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * Polling del estado del turno actual.
     *
     * <ul>
     *   <li>Si el turno ya fue resuelto → 200 con {@link SnapshotDTO}.</li>
     *   <li>Si el turno sigue pendiente → 200 con {@code { "waiting": true, "status": "ACTIVE" }}.</li>
     *   <li>Si la partida no existe → 404 (via GlobalExceptionHandler).</li>
     *   <li>Si {@code X-Player-Id} está ausente → 400 (Spring MVC lanza
     *       {@code MissingRequestHeaderException}, capturada por GlobalExceptionHandler).</li>
     * </ul>
     *
     * @param gameId   identificador de la partida
     * @param playerId jugador que consulta (leído del header {@code X-Player-Id})
     * @return 200 con SnapshotDTO o con cuerpo de espera
     */
    @GetMapping("/{gameId}/state")
    public ResponseEntity<?> getState(
            @PathVariable String gameId,
            @RequestHeader("X-Player-Id") String playerId) {

        Optional<SnapshotDTO> snapshot = snapshotService.getSnapshot(gameId, playerId);

        if (snapshot.isPresent()) {
            return ResponseEntity.ok(snapshot.get());
        }

        return ResponseEntity.ok(Map.of(
                "waiting", true,
                "status", "ACTIVE"
        ));
    }

    /**
     * Reconexión: devuelve el último snapshot disponible independientemente
     * del estado del turno actual.
     *
     * <ul>
     *   <li>Si hay al menos un turno resuelto → 200 con {@link SnapshotDTO}.</li>
     *   <li>Si no hay ningún turno resuelto aún → 200 con
     *       {@code { "status": "WAITING", "turn": 0 }}.</li>
     *   <li>Si la partida no existe → 404 (via GlobalExceptionHandler).</li>
     * </ul>
     *
     * <p>Delega completamente en {@link SnapshotService#getLastSnapshot(String, String)},
     * que accede a {@code lastResolutionResult} ignorando el estado de {@code pendingActions}.</p>
     *
     * @param gameId identificador de la partida
     * @return 200 con el último snapshot o con cuerpo de espera inicial
     */
    @GetMapping("/{gameId}/snapshot/last")
    public ResponseEntity<?> getLastSnapshot(
            @PathVariable String gameId,
            @RequestHeader("X-Player-Id") String playerId) {

        Optional<SnapshotDTO> snapshot = snapshotService.getLastSnapshot(gameId, playerId);

        if (snapshot.isPresent()) {
            return ResponseEntity.ok(snapshot.get());
        }

        return ResponseEntity.ok(Map.of(
                "status", "WAITING",
                "turn", 0
        ));
    }
}

