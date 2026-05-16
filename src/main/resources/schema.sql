CREATE TABLE IF NOT EXISTS games (
    game_id VARCHAR(50) NOT NULL,
    player_a_id VARCHAR(50) NOT NULL,
    player_b_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    winner_id VARCHAR(50) NULL,
    turns_played INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (game_id)
);