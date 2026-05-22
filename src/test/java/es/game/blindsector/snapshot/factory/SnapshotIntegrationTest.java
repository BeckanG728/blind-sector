package es.game.blindsector.snapshot.factory;

import es.game.blindsector.game.domain.GameState;
import es.game.blindsector.game.domain.ImpactArea;
import es.game.blindsector.game.domain.Position;
import es.game.blindsector.player.domain.PlayerState;
import es.game.blindsector.shared.enums.GameStatus;
import es.game.blindsector.shared.enums.HitResult;
import es.game.blindsector.snapshot.dto.PositionDTO;
import es.game.blindsector.snapshot.dto.SnapshotDTO;
import es.game.blindsector.turn.domain.TurnResolutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-07 · Tests de snapshot — SnapshotIntegrationTest
 *
 * <p>Verifica que {@link SnapshotFactory} construye correctamente la perspectiva
 * de cada jugador y que la información del rival está correctamente restringida.
 *
 * <p>No requiere contexto Spring — dominio puro.
 */
@DisplayName("P3-07 · SnapshotFactory — perspectiva y restricción de datos")
class SnapshotIntegrationTest {

    // ── Constantes de los jugadores ──────────────────────────────────────────

    private static final String PLAYER_A_ID = "player-alpha";
    private static final String PLAYER_B_ID = "player-beta";

    // playerA en (3, 3) → región A1  (col 3 / 5 = 0, row 3 / 5 = 0)
    private static final int A_COL = 3;
    private static final int A_ROW = 3;
    private static final int A_HP = 80;

    // playerB en (10, 10) → región C3  (col 10 / 5 = 2, row 10 / 5 = 2)
    private static final int B_COL = 10;
    private static final int B_ROW = 10;
    private static final int B_HP = 60;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private SnapshotFactory factory;
    private GameState game;
    private TurnResolutionResult result;

    @BeforeEach
    void setUp() {
        factory = new SnapshotFactory();

        PlayerState playerA = new PlayerState(PLAYER_A_ID, A_COL, A_ROW);
        playerA.setHp(A_HP);

        PlayerState playerB = new PlayerState(PLAYER_B_ID, B_COL, B_ROW);
        playerB.setHp(B_HP);

        game = new GameState("game-p307", GameStatus.ACTIVE, 2, playerA, playerB);

        // A atacó alrededor de (10,10); B atacó alrededor de (3,3)
        ImpactArea impactAreaA = new ImpactArea(List.of(
                new Position(9, 9), new Position(10, 9), new Position(11, 9),
                new Position(9, 10), new Position(10, 10), new Position(11, 10),
                new Position(9, 11), new Position(10, 11), new Position(11, 11)
        ));
        ImpactArea impactAreaB = new ImpactArea(List.of(
                new Position(2, 2), new Position(3, 2), new Position(4, 2),
                new Position(2, 3), new Position(3, 3), new Position(4, 3),
                new Position(2, 4), new Position(3, 4), new Position(4, 4)
        ));

        result = TurnResolutionResult.builder()
                .damageToA(20)               // B infligió 20 a A
                .damageToB(30)               // A infligió 30 a B
                .hitResultA(HitResult.HIT)           // ataque de A sobre B: HIT
                .hitResultB(HitResult.DIRECT_HIT)    // ataque de B sobre A: DIRECT_HIT
                .finalPositionA(new Position(A_COL, A_ROW))
                .finalPositionB(new Position(B_COL, B_ROW))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(impactAreaA)
                .impactAreaOfB(impactAreaB)
                .gameOver(false)
                .winnerId(null)
                .build();
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_perspectiva_playerA: datos propios y región rival correctos")
    void snapshot_perspectiva_playerA() {
        SnapshotDTO snapshot = factory.buildSnapshot(game, result, PLAYER_A_ID);

        // Metadatos de partida
        assertThat(snapshot.getGameId()).isEqualTo("game-p307");
        assertThat(snapshot.getTurn()).isEqualTo(2);
        assertThat(snapshot.getStatus()).isEqualTo("ACTIVE");

        // Posición y HP del jugador solicitante (A)
        assertThat(snapshot.getMyCol()).isEqualTo(A_COL);
        assertThat(snapshot.getMyRow()).isEqualTo(A_ROW);
        assertThat(snapshot.getMyHp()).isEqualTo(A_HP);
        assertThat(snapshot.getMyRegion()).isEqualTo("A1");

        // Región del rival — correcta pero sin coordenadas exactas
        assertThat(snapshot.getEnemyRegion()).isEqualTo("C3");

        // Daño e impacto recibidos por A (causados por B)
        assertThat(snapshot.getDamageReceived()).isEqualTo(20);
        assertThat(snapshot.getHitOnMe()).isEqualTo("DIRECT_HIT");
        assertThat(snapshot.getImpactAreaReceived()).contains(new PositionDTO(A_COL, A_ROW));

        // Mi ataque (lanzado por A sobre B)
        assertThat(snapshot.getHitOnEnemy()).isEqualTo("HIT");
        assertThat(snapshot.getMyAttackArea()).contains(new PositionDTO(B_COL, B_ROW));

        // Partida no terminada → winnerId debe ser null
        assertThat(snapshot.getWinnerId()).isNull();
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_perspectiva_playerB: datos propios y región rival correctos")
    void snapshot_perspectiva_playerB() {
        SnapshotDTO snapshot = factory.buildSnapshot(game, result, PLAYER_B_ID);

        // Posición y HP del jugador solicitante (B)
        assertThat(snapshot.getMyCol()).isEqualTo(B_COL);
        assertThat(snapshot.getMyRow()).isEqualTo(B_ROW);
        assertThat(snapshot.getMyHp()).isEqualTo(B_HP);
        assertThat(snapshot.getMyRegion()).isEqualTo("C3");

        // Región del rival (A)
        assertThat(snapshot.getEnemyRegion()).isEqualTo("A1");

        // Daño e impacto recibidos por B (causados por A)
        assertThat(snapshot.getDamageReceived()).isEqualTo(30);
        assertThat(snapshot.getHitOnMe()).isEqualTo("HIT");
        assertThat(snapshot.getImpactAreaReceived()).contains(new PositionDTO(B_COL, B_ROW));

        // Mi ataque (lanzado por B sobre A)
        assertThat(snapshot.getHitOnEnemy()).isEqualTo("DIRECT_HIT");
        assertThat(snapshot.getMyAttackArea()).contains(new PositionDTO(A_COL, A_ROW));
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_no_filtra_posicion_enemiga: ningún campo directo expone coords del rival")
    void snapshot_no_filtra_posicion_enemiga() {
        SnapshotDTO snapshotA = factory.buildSnapshot(game, result, PLAYER_A_ID);

        // myCol/myRow de A no deben ser las coordenadas de B
        assertThat(snapshotA.getMyCol()).isNotEqualTo(B_COL);
        assertThat(snapshotA.getMyRow()).isNotEqualTo(B_ROW);

        // enemyRegion es una etiqueta de región, nunca un número de coordenada
        assertThat(snapshotA.getEnemyRegion()).doesNotContain(String.valueOf(B_COL));
        assertThat(snapshotA.getEnemyRegion()).doesNotContain(String.valueOf(B_ROW));

        // Verificación simétrica desde perspectiva de B
        SnapshotDTO snapshotB = factory.buildSnapshot(game, result, PLAYER_B_ID);

        assertThat(snapshotB.getMyCol()).isNotEqualTo(A_COL);
        assertThat(snapshotB.getMyRow()).isNotEqualTo(A_ROW);
        assertThat(snapshotB.getEnemyRegion()).doesNotContain(String.valueOf(A_COL));
        assertThat(snapshotB.getEnemyRegion()).doesNotContain(String.valueOf(A_ROW));
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_partida_terminada: winnerId presente cuando gameOver == true")
    void snapshot_partida_terminada() {
        TurnResolutionResult finishedResult = TurnResolutionResult.builder()
                .damageToA(0)
                .damageToB(60)
                .hitResultA(HitResult.HIT)
                .hitResultB(HitResult.MISS)
                .finalPositionA(new Position(A_COL, A_ROW))
                .finalPositionB(new Position(B_COL, B_ROW))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(new ImpactArea(List.of(new Position(B_COL, B_ROW))))
                .impactAreaOfB(new ImpactArea(List.of()))
                .gameOver(true)
                .winnerId(PLAYER_A_ID)
                .build();

        game.setStatus(GameStatus.FINISHED);

        SnapshotDTO snapshotA = factory.buildSnapshot(game, finishedResult, PLAYER_A_ID);
        SnapshotDTO snapshotB = factory.buildSnapshot(game, finishedResult, PLAYER_B_ID);

        // Ambos jugadores ven el mismo winnerId
        assertThat(snapshotA.getWinnerId()).isEqualTo(PLAYER_A_ID);
        assertThat(snapshotB.getWinnerId()).isEqualTo(PLAYER_A_ID);
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot_empate: winnerId == \"draw\" cuando gameOver == true y winnerId == null")
    void snapshot_empate() {
        TurnResolutionResult drawResult = TurnResolutionResult.builder()
                .damageToA(80)
                .damageToB(60)
                .hitResultA(HitResult.HIT)
                .hitResultB(HitResult.HIT)
                .finalPositionA(new Position(A_COL, A_ROW))
                .finalPositionB(new Position(B_COL, B_ROW))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(new ImpactArea(List.of(new Position(B_COL, B_ROW))))
                .impactAreaOfB(new ImpactArea(List.of(new Position(A_COL, A_ROW))))
                .gameOver(true)
                .winnerId(null)   // null → empate
                .build();

        game.setStatus(GameStatus.FINISHED);

        SnapshotDTO snapshotA = factory.buildSnapshot(game, drawResult, PLAYER_A_ID);
        SnapshotDTO snapshotB = factory.buildSnapshot(game, drawResult, PLAYER_B_ID);

        assertThat(snapshotA.getWinnerId()).isEqualTo("draw");
        assertThat(snapshotB.getWinnerId()).isEqualTo("draw");
    }
}
