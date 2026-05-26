package es.game.blindsector.lobby.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.lobby.dto.response.CreateGameResponse;
import es.game.blindsector.lobby.dto.response.JoinGameResponse;
import es.game.blindsector.lobby.dto.response.StartGameResponse;
import es.game.blindsector.persistence.mapper.GameMapper;
import es.game.blindsector.persistence.repository.GameRepository;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameErrorCode;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.exception.GameException;
import es.game.blindsector.shared.utils.SpawnUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * P3-05 · LobbyService — crear, unirse e iniciar partida.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>{@link #create(String)}  — genera gameId, instancia GameState con status=WAITING y
 *       playerA en (2,2) 100 HP, persiste en memoria y en MySQL.</li>
 *   <li>{@link #join(String, String)} — asigna playerB en (12,12) 100 HP con validaciones
 *       de existencia, sala llena y auto-unión.</li>
 *   <li>{@link #start(String)} — transiciona status a ACTIVE y fija turnNumber=1.</li>
 * </ul>
 *
 * <p>Este servicio no contiene lógica de turno ni de resolución.
 */
@Service
public class LobbyService {

    private final GameMemoryStore gameMemoryStore;
    private final GameRepository gameRepository;

    public LobbyService(GameMemoryStore gameMemoryStore,
                        GameRepository gameRepository) {
        this.gameMemoryStore = gameMemoryStore;
        this.gameRepository = gameRepository;
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    /**
     * Crea una nueva partida para {@code playerId}.
     *
     * <ol>
     *   <li>Genera un {@code gameId} único con {@link UUID#randomUUID()}.</li>
     *   <li>Instancia {@link GameState} con {@code status=WAITING}, {@code turnNumber=0}
     *       y {@code playerA} en posición (col=2, row=2) con 100 HP.</li>
     *   <li>Guarda el estado en {@link GameMemoryStore} (caché en memoria).</li>
     *   <li>Hace el INSERT en MySQL vía {@link GameRepository}.</li>
     * </ol>
     *
     * @param playerId identificador del jugador que crea la partida.
     * @return {@link CreateGameResponse} con el {@code gameId}, {@code playerAId} y el status.
     */
    public CreateGameResponse create(String playerId) {
        String gameId = UUID.randomUUID().toString();

        // Sortear las dos posiciones de spawn para esta partida;
        // playerA recibe la primera, playerB recibirá la segunda al unirse.
        List<Position> spawns = SpawnUtils.pickTwoSpawns();
        Position spawnA = spawns.get(0);
        Position spawnB = spawns.get(1);

        PlayerState playerA = new PlayerState(playerId, spawnA.col(), spawnA.row());

        GameState game = new GameState(gameId, GameStatus.WAITING, 0, playerA, null);
        // Guardamos el spawn de B en el estado para recuperarlo cuando playerB haga join
        game.setPendingSpawnB(spawnB);

        gameMemoryStore.save(game);

        return new CreateGameResponse(gameId, playerId, GameStatus.WAITING,
                spawnA.col(), spawnA.row());
    }

    // -------------------------------------------------------------------------
    // join
    // -------------------------------------------------------------------------

    /**
     * Une a {@code playerId} como {@code playerB} en la partida {@code gameId}.
     *
     * <p>Validaciones (en orden):
     * <ol>
     *   <li>{@code GAME_NOT_FOUND}  — la partida no existe en memoria.</li>
     *   <li>{@code GAME_FULL}       — ya hay dos jugadores ({@code playerB != null}).</li>
     *   <li>{@code SELF_JOIN}       — {@code playerId} es el mismo que {@code playerA.playerId}.</li>
     * </ol>
     *
     * @param gameId   identificador de la partida.
     * @param playerId identificador del jugador que se une.
     * @return {@link JoinGameResponse} con los IDs de ambos jugadores y el status actual.
     */
    public JoinGameResponse join(String gameId, String playerId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        if (game.getPlayerB() != null) {
            throw new GameException(GameErrorCode.GAME_FULL,
                    "La partida " + gameId + " ya tiene dos jugadores.");
        }

        if (playerId.equals(game.getPlayerA().getPlayerId())) {
            throw new GameException(GameErrorCode.SELF_JOIN,
                    "El jugador " + playerId + " ya es playerA en esta partida.");
        }

        // Recuperar el spawn reservado para playerB al momento de crear la partida
        Position spawnB = game.getPendingSpawnB();

        PlayerState playerB = new PlayerState(playerId, spawnB.col(), spawnB.row());
        game.setPlayerB(playerB);

        gameMemoryStore.save(game);

        return new JoinGameResponse(
                game.getGameId(),
                game.getPlayerA().getPlayerId(),
                game.getPlayerB().getPlayerId(),
                game.getStatus(),
                spawnB.col(),
                spawnB.row()
        );
    }

    // -------------------------------------------------------------------------
    // start
    // -------------------------------------------------------------------------

    /**
     * Inicia la partida {@code gameId}: cambia {@code status} a {@link GameStatus#ACTIVE}
     * y fija {@code turnNumber=1}.
     *
     * <p>Lanza {@code GAME_NOT_ACTIVE} si el status actual no es {@link GameStatus#WAITING}.
     *
     * @param gameId identificador de la partida a iniciar.
     * @return {@link StartGameResponse} con el nuevo status y el número de turno.
     */
    public StartGameResponse start(String gameId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        if (game.getStatus() != GameStatus.WAITING) {
            throw new GameException(GameErrorCode.GAME_NOT_ACTIVE,
                    "La partida " + gameId + " no está en estado WAITING (estado actual: "
                    + game.getStatus() + ").");
        }

        if (game.getPlayerB() == null) {
            throw new GameException(GameErrorCode.GAME_NOT_ACTIVE,
                    "La partida " + gameId + " no puede iniciarse: falta el segundo jugador.");
        }

        game.setStatus(GameStatus.ACTIVE);
        game.setTurnNumber(1);

        gameMemoryStore.save(game);

        // INSERT en MySQL ahora que ambos jugadores existen y la partida está activa
        gameRepository.save(GameMapper.toEntity(game));

        return new StartGameResponse(game.getGameId(), GameStatus.ACTIVE, 1);
    }
}
