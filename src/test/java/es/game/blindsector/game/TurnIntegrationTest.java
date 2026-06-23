package es.game.blindsector.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.game.blindsector.api.dto.request.SubmitActionRequest;
import es.game.blindsector.domain.game.GameState;
import es.game.blindsector.domain.player.PlayerState;
import es.game.blindsector.infrastructure.memory.ActiveGamesRegistry;
import es.game.blindsector.infrastructure.persistence.repository.GameRepository;
import es.game.blindsector.shared.enums.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-09 · Tests de integración de turno.
 *
 * <p>Arranca el contexto completo de Spring con {@code @SpringBootTest} +
 * {@code @AutoConfigureMockMvc}. {@link GameRepository} se mockea para
 * eliminar la dependencia de MySQL; el resto de beans (memoria, lock,
 * validadores, motor) usan su implementación real.</p>
 *
 * <h3>Partida preexistente</h3>
 * <p>Cada test parte de un {@link GameState} inyectado directamente en
 * {@link ActiveGamesRegistry} con {@code status = ACTIVE} y {@code turn = 1},
 * evitando así pasar por el flujo de lobby.</p>
 *
 * <h3>Jugadores y posiciones</h3>
 * <ul>
 *   <li>Player A: {@code player-a} — columna 0, fila 0 (esquina superior izq.)</li>
 *   <li>Player B: {@code player-b} — columna 9, fila 9 (esquina inferior der.)</li>
 * </ul>
 * <p>Las acciones usan destinos válidos adyacentes para no disparar
 * {@code INVALID_MOVE} / {@code OUT_OF_BOUNDS} salvo en los tests de error.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("P3-09 · Turno — tests de integración")
class TurnIntegrationTest {

    // ── Constantes de partida ─────────────────────────────────────────────────

    private static final String GAME_ID = "game-turn-integration";
    private static final String PLAYER_A = "player-a";
    private static final String PLAYER_B = "player-b";

    /**
     * Movimiento válido para Player A (posición inicial col=0, row=0):
     * avanza una casilla hacia la derecha.
     */
    private static final int A_MOVE_COL = 1;
    private static final int A_MOVE_ROW = 0;

    /**
     * Ataque de Player A: apunta a una celda existente del tablero (col=9, row=9)
     * donde se encuentra Player B al inicio.
     */
    private static final int A_ATK_COL = 9;
    private static final int A_ATK_ROW = 9;

    /**
     * Movimiento válido para Player B (posición inicial col=9, row=9):
     * retrocede una casilla a la izquierda.
     */
    private static final int B_MOVE_COL = 8;
    private static final int B_MOVE_ROW = 9;

    /**
     * Ataque de Player B: apunta a la posición inicial de Player A.
     */
    private static final int B_ATK_COL = 0;
    private static final int B_ATK_ROW = 0;

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActiveGamesRegistry activeGamesRegistry;

    @MockitoBean
    private GameRepository gameRepository;

    // ── Fixture ───────────────────────────────────────────────────────────────

    @BeforeEach
    void prepararPartidaActiva() {
        // Limpia cualquier estado residual de tests anteriores
        activeGamesRegistry.getAllActive()
                .stream()
                .map(GameState::getGameId)
                .toList()
                .forEach(activeGamesRegistry::remove);

        // GameRepository.save() debe devolver el argumento (contrato JPA)
        when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Inyecta directamente una partida ACTIVE en turno 1
        PlayerState playerA = new PlayerState(PLAYER_A, 0, 0);
        PlayerState playerB = new PlayerState(PLAYER_B, 9, 9);
        GameState game = new GameState(GAME_ID, GameStatus.ACTIVE, 1, playerA, playerB);

        activeGamesRegistry.save(game);
    }

    // ── 1. submit_primera_accion ──────────────────────────────────────────────

    @Test
    @DisplayName("submit_primera_accion: POST /api/turn/submit con una sola acción devuelve 200 con waiting=true")
    void submit_primera_accion() throws Exception {
        SubmitActionRequest request = accionDeA(1);

        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true));
    }

    // ── 2. submit_segunda_accion_resuelve ─────────────────────────────────────

    @Test
    @DisplayName("submit_segunda_accion_resuelve: la segunda acción resuelve el turno y devuelve SnapshotResponse completo")
    void submit_segunda_accion_resuelve() throws Exception {
        // Primera acción de A (queda en espera)
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeA(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true));

        // Segunda acción de B → debe resolver el turno y devolver SnapshotResponse
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeB(1))))
                .andExpect(status().isOk())
                // Campos obligatorios de SnapshotResponse (F0-07)
                .andExpect(jsonPath("$.gameId").value(GAME_ID))
                .andExpect(jsonPath("$.turn").isNumber())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.myHp").isNumber())
                .andExpect(jsonPath("$.myCol").isNumber())
                .andExpect(jsonPath("$.myRow").isNumber())
                .andExpect(jsonPath("$.myRegion").isString())
                .andExpect(jsonPath("$.enemyRegion").isString())
                .andExpect(jsonPath("$.hitOnMe").isString())
                .andExpect(jsonPath("$.hitOnEnemy").isString())
                .andExpect(jsonPath("$.damageReceived").isNumber());
    }

    // ── 3. submit_accion_duplicada ────────────────────────────────────────────

    @Test
    @DisplayName("submit_accion_duplicada: enviar dos veces la acción del mismo jugador devuelve 400 con code=DUPLICATE_ACTION")
    void submit_accion_duplicada() throws Exception {
        // Primera acción de A → aceptada
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeA(1))))
                .andExpect(status().isOk());

        // Segunda acción de A en el mismo turno → duplicada
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeA(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTION"));
    }

    // ── 4. submit_turno_incorrecto ────────────────────────────────────────────

    @Test
    @DisplayName("submit_turno_incorrecto: enviar acción con turn equivocado devuelve 400 con code=STALE_TURN")
    void submit_turno_incorrecto() throws Exception {
        // La partida está en turn=1, enviamos turn=99
        SubmitActionRequest request = new SubmitActionRequest(
                GAME_ID, PLAYER_A, 99,
                A_MOVE_COL, A_MOVE_ROW,
                A_ATK_COL, A_ATK_ROW
        );

        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STALE_TURN"));
    }

    // ── 5. polling_turno_pendiente ────────────────────────────────────────────

    @Test
    @DisplayName("polling_turno_pendiente: GET /api/game/{gameId}/state mientras se espera la segunda acción devuelve waiting=true")
    void polling_turno_pendiente() throws Exception {
        // Registramos una sola acción para que haya exactamente 1 pendingAction
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeA(1))))
                .andExpect(status().isOk());

        // Polling: el turno sigue pendiente
        mockMvc.perform(get("/api/game/{gameId}/state", GAME_ID)
                        .header("X-Player-Id", PLAYER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waiting").value(true));
    }

    // ── 6. polling_turno_resuelto ─────────────────────────────────────────────

    @Test
    @DisplayName("polling_turno_resuelto: GET /api/game/{gameId}/state después de resolver devuelve SnapshotResponse")
    void polling_turno_resuelto() throws Exception {
        // Enviamos ambas acciones para resolver el turno
        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeA(1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/turn/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(accionDeB(1))))
                .andExpect(status().isOk());

        // Polling tras la resolución: debe devolver SnapshotResponse con los campos mínimos
        mockMvc.perform(get("/api/game/{gameId}/state", GAME_ID)
                        .header("X-Player-Id", PLAYER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(GAME_ID))
                .andExpect(jsonPath("$.turn").isNumber())
                .andExpect(jsonPath("$.myHp").isNumber())
                .andExpect(jsonPath("$.enemyRegion").isString());
    }

    // ── Helpers de construcción de request ───────────────────────────────────

    /**
     * Construye una {@link SubmitActionRequest} válida para Player A en el turno indicado.
     */
    private SubmitActionRequest accionDeA(int turn) {
        return new SubmitActionRequest(
                GAME_ID, PLAYER_A, turn,
                A_MOVE_COL, A_MOVE_ROW,
                A_ATK_COL, A_ATK_ROW
        );
    }

    /**
     * Construye una {@link SubmitActionRequest} válida para Player B en el turno indicado.
     */
    private SubmitActionRequest accionDeB(int turn) {
        return new SubmitActionRequest(
                GAME_ID, PLAYER_B, turn,
                B_MOVE_COL, B_MOVE_ROW,
                B_ATK_COL, B_ATK_ROW
        );
    }
}
