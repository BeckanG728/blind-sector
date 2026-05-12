package es.game.blindsector.game.engine;

import es.game.blindsector.player.domain.PlayerState;
import org.springframework.stereotype.Component;

@Component
public class DamageCalculator {

    private static final int DAMAGE_MOVED  = 25;
    private static final int DAMAGE_SNIPER = 35;

    /**
     * @param playerMoved {@code true} si el jugador se desplazó, {@code false} si permaneció quieto
     * @return 25 si se movió, 35 si no (bono Sniper)
     */
    public int calculateDamage(boolean playerMoved) {
        return playerMoved ? DAMAGE_MOVED : DAMAGE_SNIPER;
    }

    /**
     * @param target jugador que recibe el daño (HP modificado in-place)
     * @param damage cantidad de daño a aplicar (debe ser ≥ 0)
     */
    public void applyDamage(PlayerState target, int damage) {
        int newHp = target.getHp() - damage;
        target.setHp(Math.max(0, newHp));
    }
}
