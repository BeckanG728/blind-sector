package es.game.blindsector.shared.exception;

import es.game.blindsector.shared.enums.GameErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Minimal integration test that boots only the web layer (no full context).
 *
 * Covered cases
 * ─────────────
 *  1. GameException(GAME_NOT_FOUND)  → 404  + JSON body with "code"
 *  2. GameException(GAME_FULL)       → 409  + JSON body with "code"
 *  3. GameException(INVALID_MOVE)    → 400  + JSON body with "code"
 *  4. MethodArgumentNotValidException → 400 + "fields" list
 *  5. Unexpected RuntimeException    → 500  + generic message, no stack trace
 */
@WebMvcTest
@ContextConfiguration(classes = {
        GlobalExceptionHandler.class,
        GlobalExceptionHandlerTest.FakeController.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    // ── GameException cases ───────────────────────────────────────────────────

    @Test
    void gameNotFound_returns404() throws Exception {
        mockMvc.perform(get("/fake/game-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void gameFull_returns409() throws Exception {
        mockMvc.perform(get("/fake/game-full"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_FULL"));
    }

    @Test
    void invalidMove_returns400() throws Exception {
        mockMvc.perform(get("/fake/invalid-move"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOVE"));
    }

    // ── Validation case ───────────────────────────────────────────────────────

    @Test
    void validationError_returns400WithFieldList() throws Exception {
        // Send empty JSON → @NotBlank on "name" fires
        mockMvc.perform(post("/fake/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(jsonPath("$.fields[0].field").value("name"));
    }

    // ── Generic 500 case ──────────────────────────────────────────────────────

    @Test
    void unexpectedException_returns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"))
                // Make sure no Java package name leaks into the response
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    // =========================================================================
    // Minimal fake controller — only lives inside this test class
    // =========================================================================

    @RestController
    @RequestMapping("/fake")
    @Validated
    static class FakeController {

        @GetMapping("/game-not-found")
        void gameNotFound() {
            throw new GameException(GameErrorCode.GAME_NOT_FOUND, "Partida no encontrada");
        }

        @GetMapping("/game-full")
        void gameFull() {
            throw new GameException(GameErrorCode.GAME_FULL, "La partida está llena");
        }

        @GetMapping("/invalid-move")
        void invalidMove() {
            throw new GameException(GameErrorCode.INVALID_MOVE, "Movimiento inválido");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody FakeRequest req) { }

        @GetMapping("/boom")
        void boom() {
            throw new RuntimeException("Something went very wrong internally");
        }
    }

    /** Request DTO used only to trigger bean-validation. */
    record FakeRequest(@NotBlank String name) {}

    // ── Minimal Spring config so @WebMvcTest finds what it needs ─────────────

    @Configuration
    static class TestConfig {
        // GlobalExceptionHandler is picked up via @Import above.
        // FakeController is registered automatically by @WebMvcTest.
    }
}