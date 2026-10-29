package mlbinjuries;

import java.time.LocalDate;

/** One player on an injury list, as reported by SportsData.io. */
public record InjuredPlayer(
        String name,
        String team,
        String position,
        String status,
        String bodyPart,
        LocalDate startDate,
        String notes) {

    /** Key used to match the same player across daily snapshots. */
    public String key() {
        return name + "|" + team;
    }
}
