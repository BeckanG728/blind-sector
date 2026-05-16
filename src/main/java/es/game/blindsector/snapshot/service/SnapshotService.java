package es.game.blindsector.snapshot.service;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.infrastructure.memory.GameMemoryStore;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.snapshot.dto.SnapshotDTO;
import es.game.blindsector.snapshot.factory.SnapshotFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servicio de consulta de snapshot para clientes en modo polling.
 *
 * <p>No modifica el {@link GameState}; solo lo lee para determinar si el turno
 * ya fue resuelto y, en ese caso, construir el {@link SnapshotDTO} personalizado
 * para el jugador solicitante.</p>
 *
 * <h3>Lógica de pendingActions</h3>
 * <ul>
 *   <li>{@code pendingActions.size() == 0} → turno resuelto, se puede devolver snapshot.</li>
 *   <li>{@code pendingActions.size() == 1} → un jugador ya actuó, turno aún pendiente.</li>
 * </ul>
 *
 * <p>Esta distinción es posible porque {@link es.game.blindsector.turn.service.TurnCoordinator}
 * limpia {@code pendingActions} al finalizar la resolución y persiste el resultado
 * en {@link GameState#getLastResolutionResult()}.</p>
 */
@Service
public class SnapshotService {

    private final GameMemoryStore gameMemoryStore;
    private final SnapshotFactory snapshotFactory;

    public SnapshotService(GameMemoryStore gameMemoryStore,
                           SnapshotFactory snapshotFactory) {
        this.gameMemoryStore = gameMemoryStore;
        this.snapshotFactory = snapshotFactory;
    }

    /**
     * Devuelve el snapshot del turno resuelto para el jugador indicado,
     * o {@link Optional#empty()} si el turno todavía está en curso.
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Recupera el {@link GameState} o lanza
     *       {@link es.game.blindsector.shared.exception.GameException}
     *       con {@link es.game.blindsector.shared.enums.GameErrorCode#GAME_NOT_FOUND}
     *       si la partida no existe.</li>
     *   <li>Si el estado es {@link GameStatus#ACTIVE} y no hay acciones pendientes
     *       → el turno fue resuelto → retorna {@code Optional<SnapshotDTO>} con valor.</li>
     *   <li>Si hay exactamente una acción pendiente → el turno sigue en curso
     *       → retorna {@link Optional#empty()}.</li>
     *   <li>Si el estado no es {@code ACTIVE} (p.ej. {@code FINISHED}) y no hay
     *       acciones pendientes → también retorna el snapshot del último turno.</li>
     * </ol>
     *
     * @param gameId   identificador de la partida
     * @param playerId jugador que consulta (determina la perspectiva del snapshot)
     * @return {@code Optional<SnapshotDTO>} con el snapshot si el turno fue resuelto,
     * o {@code Optional.empty()} si aún está pendiente
     * @throws es.game.blindsector.shared.exception.GameException si la partida no existe
     */
    public Optional<SnapshotDTO> getSnapshot(String gameId, String playerId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        // Turno pendiente: un jugador ya actuó pero el otro no ha llegado todavía
        if (game.getPendingActions().size() == 1) {
            return Optional.empty();
        }

        // Turno resuelto (pendingActions vacío) — construir snapshot con el último resultado
        SnapshotDTO snapshot = snapshotFactory.buildSnapshot(
                game,
                game.getLastResolutionResult(),
                playerId
        );
        return Optional.of(snapshot);
    }

    /**
     * Devuelve el último {@link SnapshotDTO} resuelto para el endpoint de reconexión,
     * independientemente de si el turno actual está en curso o no.
     *
     * <p>A diferencia de {@link #getSnapshot}, este método ignora el tamaño de
     * {@code pendingActions} y accede directamente a {@code lastResolutionResult}.
     * Si aún no se ha resuelto ningún turno ({@code lastResolutionResult == null}),
     * retorna {@link Optional#empty()} para que el controller responda con el
     * cuerpo de espera inicial.</p>
     *
     * @param gameId   identificador de la partida
     * @param playerId jugador que consulta (perspectiva del snapshot)
     * @return {@code Optional<SnapshotDTO>} con el último snapshot si existe,
     *         o {@code Optional.empty()} si no hay ningún turno resuelto aún
     * @throws es.game.blindsector.shared.exception.GameException si la partida no existe
     */
    public Optional<SnapshotDTO> getLastSnapshot(String gameId, String playerId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);

        if (game.getLastResolutionResult() == null) {
            return Optional.empty();
        }

        SnapshotDTO snapshot = snapshotFactory.buildSnapshot(
                game,
                game.getLastResolutionResult(),
                playerId
        );
        return Optional.of(snapshot);
    }

    /**
     * Devuelve el {@link GameStatus} de la partida sin construir el SnapshotDTO completo.
     *
     * <p>Útil para comprobaciones ligeras (p.ej. saber si la partida ya terminó)
     * antes de hacer una llamada más costosa a {@link #getSnapshot}.</p>
     *
     * @param gameId identificador de la partida
     * @return estado actual de la partida
     * @throws es.game.blindsector.shared.exception.GameException si la partida no existe
     */
    public GameStatus getGameStatus(String gameId) {
        GameState game = gameMemoryStore.getOrThrow(gameId);
        return game.getStatus();
    }
}
