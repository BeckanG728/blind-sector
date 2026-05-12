package es.game.blindsector.game.engine;

import es.game.blindsector.player.domain.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de DamageCalculator.
 * Sin Spring context — instancia directa.

 * Escenarios cubiertos (según criterios de aceptación P2-03):
 *  - calculateDamage: daño estándar (25) cuando el jugador se movió
 *  - calculateDamage: bono Sniper (35) cuando el jugador no se movió
 *  - applyDamage: reducción normal de HP
 *  - applyDamage: reducción que lleva el HP exactamente a 0
 *  - applyDamage: reducción que sobrepasa 0 → clamp a 0
 *  - applyDamage: daño 0 no modifica el HP
 */
class DamageCalculatorTest {

    private DamageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DamageCalculator();
    }

    // ------------------------------------------------------------------ //
    //  calculateDamage                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("calculateDamage — selección de daño")
    class CalculateDamage {

        @Test
        @DisplayName("Daño estándar (25) cuando el jugador se movió")
        void danoEstandarCuandoSeMovio() {
            int damage = calculator.calculateDamage(true);
            assertThat(damage).isEqualTo(25);
        }

        @Test
        @DisplayName("Bono Sniper (35) cuando el jugador no se movió")
        void bonusSniperCuandoNoSeMovio() {
            int damage = calculator.calculateDamage(false);
            assertThat(damage).isEqualTo(35);
        }

        @Test
        @DisplayName("El diferencial entre Sniper y estándar es exactamente 10 HP")
        void diferencialSniperEsDiezPuntos() {
            int moved    = calculator.calculateDamage(true);
            int notMoved = calculator.calculateDamage(false);
            assertThat(notMoved - moved).isEqualTo(10);
        }
    }

    // ------------------------------------------------------------------ //
    //  applyDamage                                                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("applyDamage — aplicación de daño con clamp")
    class ApplyDamage {

        @Test
        @DisplayName("Reducción normal — HP queda entre 1 y 99")
        void reduccionNormal() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 25);
            assertThat(target.getHp()).isEqualTo(75);
        }

        @Test
        @DisplayName("Bono Sniper aplicado — HP pasa de 100 a 65")
        void bonusSniperAplicado() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 35);
            assertThat(target.getHp()).isEqualTo(65);
        }

        @Test
        @DisplayName("Reducción que lleva el HP exactamente a 0")
        void reduccionExactaACero() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 25);
            assertThat(target.getHp()).isEqualTo(0);
        }

        @Test
        @DisplayName("Reducción que sobrepasa 0 → clamp a 0, no negativo")
        void clampCuandoSobrepasaCero() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 35);
            assertThat(target.getHp()).isEqualTo(0);
        }

        @Test
        @DisplayName("Daño mayor que el HP máximo → clamp a 0")
        void danoMayorQueHpMaximo() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 100);
            assertThat(target.getHp()).isEqualTo(0);
        }

        @Test
        @DisplayName("Daño 0 — HP no se modifica")
        void danoCeroNoModificaHp() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 0);
            assertThat(target.getHp()).isEqualTo(75);
        }

        @Test
        @DisplayName("Aplicación acumulada — dos golpes consecutivos")
        void aplicacionAcumulada() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 25);
            calculator.applyDamage(target, 35);
            assertThat(target.getHp()).isEqualTo(40);
        }

        @Test
        @DisplayName("HP ya en 0 — applyDamage no produce negativos")
        void hpEnCeroSeMantieneEnCero() {
            PlayerState target = new PlayerState("p2", 7, 7);
            calculator.applyDamage(target, 25);
            assertThat(target.getHp()).isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------ //
    //  Integración calculateDamage + applyDamage                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Flujo completo — calculateDamage + applyDamage")
    class FlujCompleto {

        @Test
        @DisplayName("Jugador se movió → golpe estándar → 75 HP restantes")
        void golpeEstandarCompleto() {
            PlayerState target = new PlayerState("p2", 7, 7);
            int damage = calculator.calculateDamage(true);
            calculator.applyDamage(target, damage);
            assertThat(target.getHp()).isEqualTo(75);
        }

        @Test
        @DisplayName("Jugador quieto → bono Sniper → 65 HP restantes")
        void bonusSniperCompleto() {
            PlayerState target = new PlayerState("p2", 7, 7);
            int damage = calculator.calculateDamage(false);
            calculator.applyDamage(target, damage);
            assertThat(target.getHp()).isEqualTo(65);
        }

        @Test
        @DisplayName("Sniper sobre target con 30 HP → eliminación (clamp a 0)")
        void sniperEliminaTarget() {
            PlayerState target = new PlayerState("p2", 7, 7);
            int damage = calculator.calculateDamage(false);
            calculator.applyDamage(target, damage);
            assertThat(target.getHp()).isEqualTo(0);
        }
    }
}
