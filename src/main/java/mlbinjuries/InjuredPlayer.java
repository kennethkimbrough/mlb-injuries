package mlbinjuries;

import java.time.LocalDate;

/**
 * One player on an MLB injured list.
 *
 * @param playerId  MLB person id (0 if unknown)
 * @param name      full name
 * @param team      team abbreviation, e.g. NYY
 * @param position  position abbreviation, e.g. SP, RF
 * @param status    which list, e.g. "10-day IL", "60-day IL"
 * @param injury    the injury as reported in the transaction, e.g. "Right elbow inflammation" (may be empty)
 * @param startDate date the player was placed on the list (null if not found)
 * @param notes     the full transaction text (may be empty)
 */
public record InjuredPlayer(
        long playerId,
        String name,
        String team,
        String position,
        String status,
        String injury,
        LocalDate startDate,
        String notes) {

    /** Key used to match the same player across daily snapshots; the MLB id survives trades and name changes. */
    public String key() {
        return playerId > 0 ? "id:" + playerId : name + "|" + team;
    }
}
