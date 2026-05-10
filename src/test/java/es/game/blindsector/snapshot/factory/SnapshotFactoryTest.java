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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Tests unitarios para SnapshotFactory.
 * No requiere contexto Spring — dominio puro.
 */
class SnapshotFactoryTest {

    private SnapshotFactory factory;
    private GameState game;
    private TurnResolutionResult result;

    @BeforeEach
    void setUp() {
        factory = new SnapshotFactory();

        // playerA en (2,2) — región A1
        PlayerState playerA = new PlayerState();
        playerA.setPlayerId("playerA");
        playerA.setPosCol(2);
        playerA.setPosRow(2);
        playerA.setHp(75);

        // playerB en (12,12) — región C3
        PlayerState playerB = new PlayerState();
        playerB.setPlayerId("playerB");
        playerB.setPosCol(12);
        playerB.setPosRow(12);
        playerB.setHp(50);

        game = new GameState();
        game.setGameId("game-test-001");
        game.setStatus(GameStatus.ACTIVE);
        game.setTurnNumber(3);
        game.setPlayerA(playerA);
        game.setPlayerB(playerB);

        // Área de impacto de A (atacó alrededor de 12,12)
        ImpactArea impactAreaA = new ImpactArea(List.of(
                new Position(11, 11), new Position(12, 11), new Position(13, 11),
                new Position(11, 12), new Position(12, 12), new Position(13, 12),
                new Position(11, 13), new Position(12, 13), new Position(13, 13)
        ));

        // Área de impacto de B (atacó alrededor de 2,2)
        ImpactArea impactAreaB = new ImpactArea(List.of(
                new Position(1, 1), new Position(2, 1), new Position(3, 1),
                new Position(1, 2), new Position(2, 2), new Position(3, 2),
                new Position(1, 3), new Position(2, 3), new Position(3, 3)
        ));

        result = TurnResolutionResult.builder()
                .damageToA(25)
                .damageToB(35)
                .hitResultA(HitResult.HIT)           // B impactó a A
                .hitResultB(HitResult.DIRECT_HIT)    // A impactó a B en celda central
                .finalPositionA(new Position(2, 2))
                .finalPositionB(new Position(12, 12))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(impactAreaA)
                .impactAreaOfB(impactAreaB)
                .gameOver(false)
                .winnerId(null)
                .build();
    }

    // ── Test 1: perspectiva de playerA ──────────────────────────────────────

    @Test
    void snapshot_perspectiva_playerA() {
        SnapshotDTO snapshot = factory.buildSnapshot(game, result, "playerA");

        assertThat(snapshot.getGameId()).isEqualTo("game-test-001");
        assertThat(snapshot.getTurn()).isEqualTo(3);

        assertThat(game.getPlayerA().getHp()).isEqualTo(75);
        assertThat(game.getPlayerB().getHp()).isEqualTo(50);

        // Datos propios de A
        assertThat(snapshot.getMyHp()).isEqualTo(75);
        assertThat(snapshot.getMyCol()).isEqualTo(2);
        assertThat(snapshot.getMyRow()).isEqualTo(2);
        assertThat(snapshot.getMyRegion()).isEqualTo("A1");

        // Solo región del rival, nunca coordenadas
        assertThat(snapshot.getEnemyRegion()).isEqualTo("C3");

        // Impacto recibido = área de B sobre A
        assertThat(snapshot.getImpactAreaReceived()).contains(new PositionDTO(2, 2));

        // Resultado del impacto sobre A
        assertThat(snapshot.getHitOnMe()).isEqualTo("DIRECT_HIT");
        assertThat(snapshot.getDamageReceived()).isEqualTo(25);

        // Impacto lanzado por A
        assertThat(snapshot.getMyAttackArea()).contains(new PositionDTO(12, 12));
        assertThat(snapshot.getHitOnEnemy()).isEqualTo("HIT");

        // Partida no terminada
        assertThat(snapshot.getWinnerId()).isNull();
    }

    // ── Test 2: perspectiva de playerB ──────────────────────────────────────

    @Test
    void snapshot_perspectiva_playerB() {
        SnapshotDTO snapshot = factory.buildSnapshot(game, result, "playerB");

        // Datos propios de B
        assertThat(snapshot.getMyHp()).isEqualTo(50);
        assertThat(snapshot.getMyCol()).isEqualTo(12);
        assertThat(snapshot.getMyRow()).isEqualTo(12);
        assertThat(snapshot.getMyRegion()).isEqualTo("C3");

        assertThat(game.getPlayerA().getHp()).isEqualTo(75);
        assertThat(game.getPlayerB().getHp()).isEqualTo(50);

        // Solo región del rival (A)
        assertThat(snapshot.getEnemyRegion()).isEqualTo("A1");

        // Impacto recibido = área de A sobre B
        assertThat(snapshot.getImpactAreaReceived()).contains(new PositionDTO(12, 12));
        assertThat(snapshot.getHitOnMe()).isEqualTo("HIT");
        assertThat(snapshot.getDamageReceived()).isEqualTo(35);

        // Impacto lanzado por B
        assertThat(snapshot.getMyAttackArea()).contains(new PositionDTO(2, 2));
        assertThat(snapshot.getHitOnEnemy()).isEqualTo("DIRECT_HIT");
    }

    // ── Test 3: el snapshot nunca expone coordenadas exactas del rival ──────

    @Test
    void snapshot_no_filtra_posicion_enemiga() {
        SnapshotDTO snapshotA = factory.buildSnapshot(game, result, "playerA");

        // Coordenadas de B son (12, 12) — no deben aparecer en ningún campo directo
        assertThat(snapshotA.getMyCol()).isNotEqualTo(12);
        assertThat(snapshotA.getMyRow()).isNotEqualTo(12);

        // El snapshot de A no tiene ningún campo con posición exacta del rival
        // Solo tiene enemyRegion (String) — verificar que no es una coordenada
        assertThat(snapshotA.getEnemyRegion()).doesNotContain("12");

        // Repetir desde perspectiva de B
        SnapshotDTO snapshotB = factory.buildSnapshot(game, result, "playerB");
        assertThat(snapshotB.getMyCol()).isNotEqualTo(2);
        assertThat(snapshotB.getMyRow()).isNotEqualTo(2);
        assertThat(snapshotB.getEnemyRegion()).doesNotContain("2");
    }

    // ── Test 4: partida terminada con ganador ────────────────────────────────

    @Test
    void snapshot_partida_terminada() {
        TurnResolutionResult finishedResult = TurnResolutionResult.builder()
                .damageToA(0)
                .damageToB(100)
                .hitResultA(HitResult.MISS)
                .hitResultB(HitResult.DIRECT_HIT)
                .finalPositionA(new Position(2, 2))
                .finalPositionB(new Position(12, 12))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(new ImpactArea(List.of()))
                .impactAreaOfB(new ImpactArea(List.of(new Position(2, 2))))
                .gameOver(true)
                .winnerId("playerA")
                .build();

        game.setStatus(GameStatus.FINISHED);

        SnapshotDTO snapshot = factory.buildSnapshot(game, finishedResult, "playerA");

        assertThat(snapshot.getWinnerId()).isEqualTo("playerA");
    }

    // ── Test 5: empate mutuo ─────────────────────────────────────────────────

    @Test
    void snapshot_empate() {
        TurnResolutionResult drawResult = TurnResolutionResult.builder()
                .damageToA(100)
                .damageToB(100)
                .hitResultA(HitResult.HIT)
                .hitResultB(HitResult.HIT)
                .finalPositionA(new Position(2, 2))
                .finalPositionB(new Position(12, 12))
                .regionOfBSeenByA("C3")
                .regionOfASeenByB("A1")
                .impactAreaOfA(new ImpactArea(List.of(new Position(12, 12))))
                .impactAreaOfB(new ImpactArea(List.of(new Position(2, 2))))
                .gameOver(true)
                .winnerId(null)   // null → empate
                .build();

        game.setStatus(GameStatus.FINISHED);

        SnapshotDTO snapshotA = factory.buildSnapshot(game, drawResult, "playerA");
        SnapshotDTO snapshotB = factory.buildSnapshot(game, drawResult, "playerB");

        assertThat(snapshotA.getWinnerId()).isEqualTo("draw");
        assertThat(snapshotB.getWinnerId()).isEqualTo("draw");
    }

    // ── Test 6: simetría — áreas de impacto están correctamente asignadas ───

    @Test
    void snapshot_areas_de_impacto_son_simetricas() {
        SnapshotDTO snapshotA = factory.buildSnapshot(game, result, "playerA");
        SnapshotDTO snapshotB = factory.buildSnapshot(game, result, "playerB");

        // El área recibida de A es el área de ataque de B (y viceversa)
        assertThat(snapshotA.getImpactAreaReceived()).isEqualTo(snapshotB.getMyAttackArea());
        assertThat(snapshotB.getImpactAreaReceived()).isEqualTo(snapshotA.getMyAttackArea());
    }
}
