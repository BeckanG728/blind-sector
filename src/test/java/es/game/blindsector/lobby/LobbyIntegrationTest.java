package es.game.blindsector.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.game.blindsector.infrastructure.memory.ActiveGamesRegistry;
import es.game.blindsector.lobby.dto.request.CreateGameRequest;
import es.game.blindsector.lobby.dto.request.JoinGameRequest;
import es.game.blindsector.lobby.dto.request.StartGameRequest;
import es.game.blindsector.persistence.repository.GameRepository;
import es.game.blindsector.shared.enums.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3-08 · Tests de integración del lobby.
 *
 * <p>Arranca el contexto completo de Spring con {@code @SpringBootTest} y
 * {@code @AutoConfigureMockMvc}. Cada test ejecuta peticiones HTTP reales a
 * {@code LobbyController} vía {@code MockMvc}.
 *
 * <p>{@link GameRepository} se mockea con {@code @MockitoBean} para evitar
 * la dependencia de una base de datos MySQL real en tests. El resto de beans
 * ({@link es.game.blindsector.infrastructure.memory.GameMemoryStore},
 * {@link ActiveGamesRegistry}) se usan con su implementación real en memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("P3-08 · Lobby — tests de integración")
class LobbyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ActiveGamesRegistry activeGamesRegistry;

    /**
     * Mockeamos GameRepository para que el contexto arranque sin MySQL.
     * El comportamiento por defecto de Mockito (devolver null / empty)
     * es suficiente: LobbyService llama a save() pero no usa el retorno.
     */
    @MockitoBean
    private GameRepository gameRepository;

    @BeforeEach
    void limpiarRegistro() {
        // Limpia el mapa en memoria entre tests para evitar contaminación
        activeGamesRegistry.getAllActive()
                .stream()
                .map(g -> g.getGameId())
                .toList()
                .forEach(activeGamesRegistry::remove);

        // GameRepository.save() debe devolver el argumento (contrato de JpaRepository)
        when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 1. crear_partida ──────────────────────────────────────────────────────

    @Test
    @DisplayName("crear_partida: POST /api/lobby/create devuelve 200 con gameId y status=WAITING")
    void crear_partida() throws Exception {
        CreateGameRequest request = new CreateGameRequest("player-1");

        MvcResult result = mockMvc.perform(post("/api/lobby/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").isNotEmpty())
                .andExpect(jsonPath("$.status").value(GameStatus.WAITING.name()))
                .andReturn();

        // Verifica que el estado en memoria tiene status=WAITING
        String gameId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("gameId").asText();

        assertThat(activeGamesRegistry.findById(gameId))
                .isPresent()
                .get()
                .satisfies(g -> assertThat(g.getStatus()).isEqualTo(GameStatus.WAITING));
    }

    // ── 2. join_exitoso ───────────────────────────────────────────────────────

    @Test
    @DisplayName("join_exitoso: POST /api/lobby/join con gameId válido devuelve 200 con status=WAITING")
    void join_exitoso() throws Exception {
        // Precondición: crear una partida
        String gameId = crearPartida("player-A");

        JoinGameRequest request = new JoinGameRequest(gameId, "player-B");

        mockMvc.perform(post("/api/lobby/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value(GameStatus.WAITING.name()));
    }

    // ── 3. join_partida_inexistente ───────────────────────────────────────────

    @Test
    @DisplayName("join_partida_inexistente: POST /api/lobby/join con gameId desconocido devuelve 404")
    void join_partida_inexistente() throws Exception {
        JoinGameRequest request = new JoinGameRequest("game-no-existe", "player-B");

        mockMvc.perform(post("/api/lobby/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── 4. join_partida_llena ─────────────────────────────────────────────────

    @Test
    @DisplayName("join_partida_llena: POST /api/lobby/join cuando ya hay dos jugadores devuelve 409")
    void join_partida_llena() throws Exception {
        String gameId = crearPartida("player-A");
        unirse(gameId, "player-B");   // llena la sala

        JoinGameRequest request = new JoinGameRequest(gameId, "player-C");

        mockMvc.perform(post("/api/lobby/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ── 5. start_exitoso ──────────────────────────────────────────────────────

    @Test
    @DisplayName("start_exitoso: POST /api/lobby/start devuelve 200 con status=ACTIVE y turn=1")
    void start_exitoso() throws Exception {
        String gameId = crearPartida("player-A");
        unirse(gameId, "player-B");

        StartGameRequest request = new StartGameRequest(gameId);

        mockMvc.perform(post("/api/lobby/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(GameStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.turnNumber").value(1));

        // Verifica el estado en memoria
        assertThat(activeGamesRegistry.findById(gameId))
                .isPresent()
                .get()
                .satisfies(g -> {
                    assertThat(g.getStatus()).isEqualTo(GameStatus.ACTIVE);
                    assertThat(g.getTurnNumber()).isEqualTo(1);
                });
    }

    // ── 6. start_sin_dos_jugadores ────────────────────────────────────────────

    @Test
    @DisplayName("start_sin_dos_jugadores: POST /api/lobby/start sin playerB devuelve 409")
    void start_sin_dos_jugadores() throws Exception {
        // LobbyService.start() lanza GAME_NOT_ACTIVE si status != WAITING,
        // pero aquí la partida está WAITING con un solo jugador.
        // Según la spec, start con un solo jugador debe devolver 409.
        // LobbyService lanza GAME_NOT_ACTIVE (→ 409) cuando status != WAITING;
        // no hay validación de "dos jugadores" explícita en P3-05, por lo que
        // este test verifica que start() sobre una partida sin playerB
        // también devuelve 409 (el servicio lanzará GAME_NOT_ACTIVE porque
        // el estado sigue siendo WAITING pero con solo un jugador — el handler
        // debe mapear correctamente).
        //
        // NOTA: si LobbyService en su implementación actual no valida playerB==null
        // y transiciona igualmente, este test fallará hasta que P3-05 añada esa
        // validación. El test documenta el comportamiento esperado por el ticket.
        String gameId = crearPartida("player-A");
        // NO se une playerB

        StartGameRequest request = new StartGameRequest(gameId);

        mockMvc.perform(post("/api/lobby/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Crea una partida vía HTTP y devuelve el gameId generado.
     */
    private String crearPartida(String playerId) throws Exception {
        CreateGameRequest request = new CreateGameRequest(playerId);

        MvcResult result = mockMvc.perform(post("/api/lobby/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("gameId").asText();
    }

    /**
     * Une a {@code playerId} a la partida {@code gameId} vía HTTP.
     */
    private void unirse(String gameId, String playerId) throws Exception {
        JoinGameRequest request = new JoinGameRequest(gameId, playerId);

        mockMvc.perform(post("/api/lobby/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
