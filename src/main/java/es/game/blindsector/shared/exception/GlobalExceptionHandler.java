package es.game.blindsector.shared.exception;

import es.game.blindsector.shared.enums.GameErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

// Assumes F0-01 and F0-02 are already on the classpath:
//   shared.exception.GameException        (has getErrorCode(): GameErrorCode, getMessage())
//   shared.exception.GameErrorCode        (enum with the values below)

/**
 * Central error handler: converts every exception thrown inside any
 * @RestController into a uniform JSON body.
 *
 * <pre>
 *   { "error": "...",  "code": "GAME_ERROR_CODE" }   ← GameException
 *   { "error": "...",  "fields": [ … ] }              ← validation
 *   { "error": "Error interno del servidor" }          ← anything else
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 1. Domain exceptions (GameException) ─────────────────────────────────

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ErrorResponse> handleGameException(GameException ex) {

        HttpStatus status = resolveStatus(ex.getErrorCode());

        ErrorResponse body = new ErrorResponse(ex.getMessage(), ex.getErrorCode().name());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Maps each GameErrorCode to the correct HTTP status.
     * No hard-coded Strings — only enum constants.
     */
    private HttpStatus resolveStatus(GameErrorCode code) {
        return switch (code) {
            case GAME_NOT_FOUND                      -> HttpStatus.NOT_FOUND;            // 404
            case INVALID_MOVE,
                 OUT_OF_BOUNDS,
                 INVALID_ATTACK,
                 STALE_TURN,
                 DUPLICATE_ACTION,
                 SELF_JOIN                           -> HttpStatus.BAD_REQUEST;          // 400
            case GAME_NOT_ACTIVE,
                 GAME_FULL                           -> HttpStatus.CONFLICT;             // 409
        };
    }

    // ── 2. Bean-validation errors (@Valid / @Validated request bodies) ────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        BindingResult br = ex.getBindingResult();

        List<ErrorResponse.FieldError> fieldErrors = br.getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponse body = new ErrorResponse("Errores de validación en la solicitud", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 3. Recursos estáticos no encontrados (favicon.ico, etc.) ─────────────
    // NoResourceFoundException es una excepción de infraestructura de Spring,
    // no un error de negocio. Se devuelve 404 sin loguear nada.

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    // ── 4. Catch-all — never expose stack traces ──────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {

        // Log the real cause server-side; the client gets only a generic message.
        log.error("Unhandled exception", ex);

        ErrorResponse body = new ErrorResponse("Error interno del servidor", (String) null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
