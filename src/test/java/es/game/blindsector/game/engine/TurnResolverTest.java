package es.game.blindsector.game.engine;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.enums.HitResult;
import es.game.blindsector.turn.domain.TurnAction;
import es.game.blindsector.turn.domain.TurnResolutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de TurnResolver.
 * Sin Spring context — instancias directas de todas las dependencias.

 * Cubre los 8 escenarios del criterio de aceptación P2-09 más
 * verificaciones de determinismo e incremento de turno.
 */
class TurnResolverTest {

    private TurnResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TurnResolver(
                new MovementEngine(),
                new ImpactResolver(),
                new DamageCalculator()
        );
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Crea un GameState con los jugadores en las posiciones indicadas.
     * Estado ACTIVE, turno 1.
     */
    private GameState buildGame(int aCol, int aRow, int bCol, int bRow) {
        PlayerState playerA = new PlayerState("player-a", aCol, aRow);
        PlayerState playerB = new PlayerState("player-b", bCol, bRow);
        GameState game = new GameState("game-test",GameStatus.ACTIVE,1, playerA, playerB);
        return game;
    }


    /**
     * Construye una TurnAction completa.
     */
    private TurnAction action(String playerId, int turn,
                              int moveToCol, int moveToRow,
                              int attackCol, int attackRow) {
        TurnAction a = new TurnAction();
        a.setPlayerId(playerId);
        a.setTurn(turn);
        a.setMoveToCol(moveToCol);
        a.setMoveToRow(moveToRow);
        a.setAttackCol(attackCol);
        a.setAttackRow(attackRow);
        a.setSubmittedAt(System.currentTimeMillis());
        return a;
    }

    // ------------------------------------------------------------------ //
    //  Escenario 1 — hit_estandar                                         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("hit_estandar: A se mueve y su ataque impacta a B (no en centro) → damageToB=25, hitResultA=HIT")
    void hitEstandar() {
        // A en (2,2), B en (8,8)
        // A se mueve a (3,3) → se movió → daño estándar 25
        // A ataca en (7,7) → B está en (8,8) que está en el área 3×3 de (7,7)
        GameState game = buildGame(2, 2, 8, 8);

        TurnAction actionA = action("player-a", 1, 3, 3, 7, 7);
        TurnAction actionB = action("player-b", 1, 8, 8, 0, 0); // B se queda quieto, ataca lejos

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.getDamageToB()).isEqualTo(25);
        assertThat(result.getHitResultB()).isEqualTo(HitResult.HIT);
        assertThat(result.getDamageToA()).isEqualTo(0);
        assertThat(result.getHitResultA()).isEqualTo(HitResult.MISS);
    }

    // ------------------------------------------------------------------ //
    //  Escenario 2 — direct_hit                                           //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("direct_hit: A ataca exactamente donde está B → hitResultA=DIRECT_HIT")
    void directHit() {
        // B se queda en (10,10), A ataca exactamente (10,10)
        GameState game = buildGame(2, 2, 10, 10);

        TurnAction actionA = action("player-a", 1, 2, 2, 10, 10); // A quieto, ataca el centro exacto de B
        TurnAction actionB = action("player-b", 1, 10, 10, 0, 0); // B quieto

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.getHitResultB()).isEqualTo(HitResult.DIRECT_HIT);
        assertThat(result.getDamageToB()).isEqualTo(35); // A quieto → bono Sniper
    }

    // ------------------------------------------------------------------ //
    //  Escenario 3 — sniper_bonus                                         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("sniper_bonus: A no se mueve y su ataque impacta → damageToB=35")
    void sniperBonus() {
        // A en (7,7) quieto, B en (8,8) — dentro del área de ataque (7,7)
        GameState game = buildGame(7, 7, 8, 8);

        TurnAction actionA = action("player-a", 1, 7, 7, 7, 7); // quieto, ataca su propia celda
        TurnAction actionB = action("player-b", 1, 8, 8, 0, 0); // B quieto, ataca lejos

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.getDamageToB()).isEqualTo(35);
        assertThat(result.getHitResultB()).isIn(HitResult.HIT, HitResult.DIRECT_HIT);
    }

    // ------------------------------------------------------------------ //
    //  Escenario 4 — miss                                                  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("miss: A ataca pero B no está en el área → damageToB=0, hitResultA=MISS")
    void miss() {
        // B en (12,12), A ataca (0,0) — completamente fuera
        GameState game = buildGame(2, 2, 12, 12);

        TurnAction actionA = action("player-a", 1, 2, 2, 0, 0);
        TurnAction actionB = action("player-b", 1, 12, 12, 0, 0);

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.getDamageToB()).isEqualTo(0);
        assertThat(result.getHitResultB()).isEqualTo(HitResult.MISS);
    }

    // ------------------------------------------------------------------ //
    //  Escenario 5 — empate_mutuo                                         //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("empate_mutuo: ambos quedan en 0 HP en el mismo turno → gameOver=true, winnerId=null")
    void empateMutuo() {
        // Ambos con 25 HP, ambos se mueven y se impactan → daño estándar 25 → ambos a 0
        GameState game = buildGame(2, 2, 25, 12);

        // A ataca (12,12) donde está B; B ataca (2,2) donde está A
        // Ambos se mueven dentro del rango de ataque del rival
        TurnAction actionA = action("player-a", 1, 3, 3, 12, 12);
        TurnAction actionB = action("player-b", 1, 11, 11, 3, 3);

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.isGameOver()).isTrue();
        assertThat(result.getWinnerId()).isNull();
    }

    // ------------------------------------------------------------------ //
    //  Escenario 6 — victoria_clara                                        //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("victoria_clara: B queda en 0 HP, A no → winnerId=player-a")
    void victoriaClara() {
        // B con 25 HP, A lo impacta moviéndose → daño 25 → B a 0
        GameState game = buildGame(2, 2, 100, 8);

        TurnAction actionA = action("player-a", 1, 3, 3, 8, 8); // A se mueve, ataca donde está B
        TurnAction actionB = action("player-b", 1, 8, 8, 0, 0); // B quieto, ataca lejos

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        assertThat(result.isGameOver()).isTrue();
        assertThat(result.getWinnerId()).isEqualTo("player-a");
        assertThat(game.getPlayerB().getHp()).isEqualTo(0);
        assertThat(game.getPlayerA().getHp()).isEqualTo(100);
    }

    // ------------------------------------------------------------------ //
    //  Escenario 7 — impacto_en_esquina                                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("impacto_en_esquina: ataque en (0,0) genera área parcial sin error")
    void impactoEnEsquina() {
        GameState game = buildGame(2, 2, 1, 1);

        // A ataca la esquina (0,0), B se mueve a (1,1) — dentro del área recortada
        TurnAction actionA = action("player-a", 1, 2, 2, 0, 0);
        TurnAction actionB = action("player-b", 1, 1, 1, 14, 14);

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        // No debe lanzar excepción; el resultado debe ser HIT o DIRECT_HIT
        assertThat(result.getHitResultB()).isIn(HitResult.HIT, HitResult.DIRECT_HIT);
        assertThat(result.getImpactAreaOfA().getPositions()).hasSizeLessThanOrEqualTo(9);
    }

    // ------------------------------------------------------------------ //
    //  Escenario 8 — determinismo                                          //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("determinismo: mismo input siempre produce mismo output")
    void determinismo() {
        TurnAction actionA = action("player-a", 1, 3, 3, 8, 8);
        TurnAction actionB = action("player-b", 1, 8, 8, 0, 0);

        GameState game1 = buildGame(2, 2, 8, 8);
        TurnResolutionResult result1 = resolver.resolve(game1, actionA, actionB);

        GameState game2 = buildGame(2, 2, 8, 8);
        TurnResolutionResult result2 = resolver.resolve(game2, actionA, actionB);

        assertThat(result1.getDamageToA()).isEqualTo(result2.getDamageToA());
        assertThat(result1.getDamageToB()).isEqualTo(result2.getDamageToB());
        assertThat(result1.getHitResultA()).isEqualTo(result2.getHitResultA());
        assertThat(result1.getHitResultB()).isEqualTo(result2.getHitResultB());
        assertThat(result1.isGameOver()).isEqualTo(result2.isGameOver());
        assertThat(result1.getWinnerId()).isEqualTo(result2.getWinnerId());
    }

    // ------------------------------------------------------------------ //
    //  Incremento de turno                                                 //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("El turno se incrementa en 1 tras la resolución")
    void turnoSeIncrementa() {
        GameState game = buildGame(2, 2, 12, 12);
        assertThat(game.getTurnNumber()).isEqualTo(1);

        TurnAction actionA = action("player-a", 1, 2, 2, 0, 0);
        TurnAction actionB = action("player-b", 1, 12, 12, 14, 14);

        resolver.resolve(game, actionA, actionB);

        assertThat(game.getTurnNumber()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ //
    //  Regiones reveladas                                                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Las regiones del rival se calculan con la posición final tras el movimiento")
    void regionesConPosicionFinal() {
        // B empieza en región A1 (cols 0-4, rows 0-4) y se mueve a región C3 (cols 10-14, rows 10-14)
        GameState game = buildGame(2, 2, 2, 2);

        TurnAction actionA = action("player-a", 1, 2, 2, 0, 0);
        TurnAction actionB = action("player-b", 1, 12, 12, 14, 14); // B se mueve a (12,12) → región C3

        TurnResolutionResult result = resolver.resolve(game, actionA, actionB);

        // La región de B vista por A debe reflejar su posición FINAL (12,12)
        assertThat(result.getRegionOfBSeenByA()).isEqualTo("C3");
    }
}