package es.game.blindsector.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Uniform error response body returned by GlobalExceptionHandler.
 *
 * Minimal form  → { "error": "...", "code": "..." }
 * Validation    → { "error": "...", "fields": [ { "field": "...", "message": "..." } ] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String error;
    private final String code;
    private final List<FieldError> fields;

    // ── constructors ──────────────────────────────────────────────────────────

    /** Used for GameException and generic 500 responses. */
    public ErrorResponse(String error, String code) {
        this.error  = error;
        this.code   = code;
        this.fields = null;
    }

    /** Used for MethodArgumentNotValidException (validation errors). */
    public ErrorResponse(String error, List<FieldError> fields) {
        this.error  = error;
        this.code   = null;
        this.fields = fields;
    }

    // ── getters ───────────────────────────────────────────────────────────────

    public String getError()          { return error;  }
    public String getCode()           { return code;   }
    public List<FieldError> getFields() { return fields; }

    // ── nested ────────────────────────────────────────────────────────────────

    public static class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field   = field;
            this.message = message;
        }

        public String getField()   { return field;   }
        public String getMessage() { return message; }
    }
}
