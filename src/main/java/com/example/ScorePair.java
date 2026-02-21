// Step 3: Add new record src/main/java/com/example/ScorePair.java
package com.example;

/**
 * Simple pair holder for team scores during fights: team1 vs team2 totals.
 */
public record ScorePair(int team1Score, int team2Score) {}