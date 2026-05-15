package es.game.blindsector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "games")
public class GameEntity {

    @Id
    @Column(name = "game_id", length = 50)
    private String gameId;

    @Column(name = "player_a_id", nullable = false, length = 50)
    private String playerAId;

    @Column(name = "player_b_id", nullable = false, length = 50)
    private String playerBId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "winner_id", length = 50)
    private String winnerId;

    @Column(name = "turns_played", nullable = false)
    private int turnsPlayed = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // --- Constructores ---
    public GameEntity() {
    }

    // --- Getters y Setters ---
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getPlayerAId() { return playerAId; }
    public void setPlayerAId(String playerAId) { this.playerAId = playerAId; }

    public String getPlayerBId() { return playerBId; }
    public void setHeaderBId(String playerBId) { this.playerBId = playerBId; } // O setPlayerBId según tu estilo
    public void setPlayerBId(String playerBId) { this.playerBId = playerBId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }

    public int getTurnsPlayed() { return turnsPlayed; }
    public void setTurnsPlayed(int turnsPlayed) { this.turnsPlayed = turnsPlayed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
