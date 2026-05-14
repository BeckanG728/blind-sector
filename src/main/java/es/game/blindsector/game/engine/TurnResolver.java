package es.game.blindsector.game.engine;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.HitResult;
import es.game.blindsector.shared.utils.GridUtils;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnResolutionResult;
import org.springframework.stereotype.Component;

/**
 * Motor de resolución simultánea y determinista de un turno completo.

 * Contrato de determinismo:
 *   El mismo GameState + las mismas dos TurnAction siempre producen
 *   el mismo TurnResolutionResult. No hay aleatoriedad ni estado externo.

 * Orden de resolución (según diseño técnico §7):
 *   1. Aplicar movimiento de ambos jugadores
 *   2. Calcular área de impacto de cada ataque
 *   3. Detectar si cada jugador es impactado por el rival
 *   4. Calcular daño (con bono Sniper si no hubo movimiento)
 *   5. Aplicar daño a cada PlayerState
 *   6. Incrementar game.turnNumber
 *   7. Determinar gameOver y winnerId
 *   8. Construir y retornar TurnResolutionResult

 * Responsabilidades explícitamente fuera del scope de esta clase:
 *   - Validación de acciones (MovementValidator, AttackValidator, TurnValidator)
 *   - Gestión de pendingActions (TurnCoordinator)
 *   - Persistencia (GameLifecycleService)
 *   - Locking (LockExecutor / TurnCoordinator)
 */
@Component
public class TurnResolver {

    private final MovementEngine  movementEngine;
    private final ImpactResolver  impactResolver;
    private final DamageCalculator damageCalculator;

    public TurnResolver(MovementEngine movementEngine,
                        ImpactResolver impactResolver,
                        DamageCalculator damageCalculator) {
        this.movementEngine   = movementEngine;
        this.impactResolver   = impactResolver;
        this.damageCalculator = damageCalculator;
    }

    /**
     * Resuelve un turno completo de forma determinista.
     *
     * @param game    estado mutable de la partida (posiciones y HP se modifican in-place)
     * @param actionA acción del jugador A para este turno
     * @param actionB acción del jugador B para este turno
     * @return resultado completo del turno, listo para construir snapshots
     */
    public TurnResolutionResult resolve(GameState game,
                                        TurnAction actionA,
                                        TurnAction actionB) {

        PlayerState playerA = game.getPlayerA();
        PlayerState playerB = game.getPlayerB();

        // ── Paso 1: posiciones originales para detectar movimiento ──────
        int originACol = playerA.getPosCol();
        int originARow = playerA.getPosRow();
        int originBCol = playerB.getPosCol();
        int originBRow = playerB.getPosRow();

        // ── Paso 2: aplicar movimientos ──────────────────────────────────
        movementEngine.applyMovement(playerA, actionA.getMoveToCol(), actionA.getMoveToRow());
        movementEngine.applyMovement(playerB, actionB.getMoveToCol(), actionB.getMoveToRow());

        boolean aMovio = (playerA.getPosCol() != originACol || playerA.getPosRow() != originARow);
        boolean bMovio = (playerB.getPosCol() != originBCol || playerB.getPosRow() != originBRow);

        // ── Paso 3: calcular áreas de impacto ────────────────────────────
        ImpactArea areaAtaqueA = impactResolver.computeImpactArea(actionA.getAttackCol(), actionA.getAttackRow());
        ImpactArea areaAtaqueB = impactResolver.computeImpactArea(actionB.getAttackCol(), actionB.getAttackRow());

        // ── Paso 4: detectar impactos (tras aplicar movimientos) ─────────
        HitResult hitDeA = impactResolver.resolveImpact(actionA.getAttackCol(), actionA.getAttackRow(), playerB);
        HitResult hitDeB = impactResolver.resolveImpact(actionB.getAttackCol(), actionB.getAttackRow(), playerA);

        // ── Paso 5: calcular daño ────────────────────────────────────────
        int damageDeA = hitDeA != HitResult.MISS ? damageCalculator.calculateDamage(aMovio) : 0;
        int damageDeB = hitDeB != HitResult.MISS ? damageCalculator.calculateDamage(bMovio) : 0;

        // ── Paso 6: aplicar daño ─────────────────────────────────────────
        if (damageDeA > 0) damageCalculator.applyDamage(playerB, damageDeA);
        if (damageDeB > 0) damageCalculator.applyDamage(playerA, damageDeB);

        // ── Paso 7: incrementar turno ────────────────────────────────────
        game.setTurnNumber(game.getTurnNumber() + 1);

        // ── Paso 8: determinar resultado final ───────────────────────────
        boolean aElim = playerA.getHp() <= 0;
        boolean bElim = playerB.getHp() <= 0;

        boolean gameOver = aElim || bElim;
        String  winnerId = resolveWinner(aElim, bElim, playerA.getPlayerId(), playerB.getPlayerId());

        // ── Paso 9: construir resultado ──────────────────────────────────
        return TurnResolutionResult.builder()
                .damageToA(damageDeB)
                .damageToB(damageDeA)
                .hitResultA(hitDeB)
                .hitResultB(hitDeA)
                .finalPositionA(new Position(playerA.getPosCol(), playerA.getPosRow()))
                .finalPositionB(new Position(playerB.getPosCol(), playerB.getPosRow()))
                .regionOfBSeenByA(GridUtils.resolveRegion(playerB.getPosCol(), playerB.getPosRow()).toLabel())
                .regionOfASeenByB(GridUtils.resolveRegion(playerA.getPosCol(), playerA.getPosRow()).toLabel())
                .impactAreaOfA(areaAtaqueA)
                .impactAreaOfB(areaAtaqueB)
                .gameOver(gameOver)
                .winnerId(winnerId)
                .build();
    }

    // ── Helpers privados ─────────────────────────────────────────────────

    /**
     * Determina el ganador tras aplicar el daño.
     *
     * @return null si ambos sobreviven, "draw" si eliminación mutua,
     *         el playerId del ganador en caso de victoria simple.
     */
    private String resolveWinner(boolean aElim, boolean bElim,
                                 String playerAId, String playerBId) {
        if (!aElim && !bElim) return null;
        if (aElim && bElim)   return "draw";
        if (bElim)            return playerAId;
        return playerBId;
    }
}