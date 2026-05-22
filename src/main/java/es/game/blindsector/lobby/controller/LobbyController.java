package es.game.blindsector.lobby.controller;

import es.game.blindsector.lobby.dto.request.CreateGameRequest;
import es.game.blindsector.lobby.dto.request.JoinGameRequest;
import es.game.blindsector.lobby.dto.request.StartGameRequest;
import es.game.blindsector.lobby.dto.response.CreateGameResponse;
import es.game.blindsector.lobby.dto.response.JoinGameResponse;
import es.game.blindsector.lobby.dto.response.StartGameResponse;
import es.game.blindsector.lobby.service.LobbyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * P3-06 · LobbyController — endpoints de lobby.
 *
 * <p>Expone tres endpoints REST que delegan completamente en {@link LobbyService}.
 * No contiene lógica propia ni bloques try-catch: cualquier
 * {@link es.game.blindsector.shared.exception.GameException} lanzada por el servicio
 * es capturada y mapeada a HTTP por {@code GlobalExceptionHandler} (P1-09).</p>
 */
@RestController
@RequestMapping("/api/lobby")
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    /**
     * Crea una nueva partida.
     *
     * @param request contiene el {@code playerId} del jugador que crea la partida.
     * @return {@link CreateGameResponse} con el {@code gameId}, {@code playerAId} y el status.
     */
    @PostMapping("/create")
    public ResponseEntity<CreateGameResponse> create(@RequestBody CreateGameRequest request) {
        return ResponseEntity.ok(lobbyService.create(request.getPlayerId()));
    }

    /**
     * Une a un jugador a una partida existente.
     *
     * @param request contiene el {@code gameId} y el {@code playerId} del jugador que se une.
     * @return {@link JoinGameResponse} con los IDs de ambos jugadores y el status actual.
     */
    @PostMapping("/join")
    public ResponseEntity<JoinGameResponse> join(@RequestBody JoinGameRequest request) {
        return ResponseEntity.ok(lobbyService.join(request.getGameId(), request.getPlayerId()));
    }

    /**
     * Inicia una partida en estado WAITING.
     *
     * @param request contiene el {@code gameId} de la partida a iniciar.
     * @return {@link StartGameResponse} con el nuevo status y el número de turno.
     */
    @PostMapping("/start")
    public ResponseEntity<StartGameResponse> start(@RequestBody StartGameRequest request) {
        return ResponseEntity.ok(lobbyService.start(request.getGameId()));
    }
}

