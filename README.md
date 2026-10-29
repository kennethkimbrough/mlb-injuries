# MLB injury tracker

[![Build](https://github.com/kennethkimbrough/mlb-injuries/actions/workflows/build.yml/badge.svg)](https://github.com/kennethkimbrough/mlb-injuries/actions/workflows/build.yml)

A command-line Java program that lists every player currently on an MLB
injured list, records a snapshot of the lists each day, and reports who has
been out longest and which teams have been hit hardest.

Data comes from MLB's public Stats API (`statsapi.mlb.com`). No API key or
account is needed.

## What it does

`fetch` prints today's injured lists:

```
Team  Player         Pos  List       Injury               Placed
----  -------------  ---  ---------  -------------------  ----------
BOS   Blake Fixture  LF   15-day IL  right wrist sprain   2026-08-20
NYY   Alex Example   P    60-day IL  Right elbow surgery  2026-04-12
NYY   Casey Sample   C    10-day IL                       2026-09-02
...
```

(Output rendered from the test fixtures, which use invented players.)

`fetch --save` also adds today's lists to `data/injuries.csv`, one row per
player per day. Re-running on the same day replaces that day's rows rather
than duplicating them.

`report` reads the history and prints:

- the players who have appeared in the most daily snapshots, and whether they
  are still listed;
- each team's total player-days on the lists, distinct players, and how many
  are listed in the latest snapshot;
- who came off and who was added since the previous snapshot.

A scheduled GitHub Actions workflow runs `fetch --save` every morning and
commits the updated CSV, so the history builds up on its own.

### Where the data comes from

| Request | Used for |
|---|---|
| `/api/v1/teams?sportId=1` | the 30 teams and their abbreviations |
| `/api/v1/teams/{id}/roster?rosterType=40Man` and `fullSeason` | players whose roster status is an injured list (codes `D10`, `D15`, `D60`, ...). Players on the 60-day list come off the 40-man roster, so both roster types are read and merged by player id. |
| `/api/v1/transactions?sportId=1&startDate=...&endDate=...` | the date each player was placed on the list and the injury named in the transaction ("... on the 15-day injured list. Right elbow inflammation.") |

If the roster requests fail, the fetch fails rather than saving a partial
list. If only the transactions request fails, the list is still saved with
the placement date and injury left blank.

### The offseason

MLB only uses the injured lists from spring training through the
postseason; players are reinstated after the World Series. Between November
and February the lists are empty, so the first empty day is recorded (as a
row holding only the date) and nothing more is written until players appear
again.

## Run it

Requires Java 17+ and Maven.

```sh
mvn -B package                                   # builds target/mlb-injuries.jar and runs the tests
java -jar target/mlb-injuries.jar fetch --save
java -jar target/mlb-injuries.jar report --top 15
```

Options: `--history FILE` changes where the CSV lives (default
`data/injuries.csv`); `--top N` sets how many rows the report shows.

## Tests

```sh
mvn test
```

The tests run without network access. A fake API serves sample responses
shaped like the real ones (`src/test/resources/mlb/`, invented players),
which cover merging the two roster types, ignoring traded and optioned
players, reading placements and transfers from transactions, and losing only
the details when transactions are unavailable. The history file is tested
against a temporary directory, including the empty-day rules, and the
statistics against hand-built snapshots.

## Layout

```
src/main/java/mlbinjuries/
  Main.java             command-line entry point (fetch, report)
  MlbStatsClient.java   requests to the MLB Stats API and merging of the results
  MlbParser.java        teams, rosters and transactions JSON -> InjuredPlayer records
  InjuredPlayer.java    one injured player
  TablePrinter.java     aligned text table
  InjuryHistory.java    daily snapshots in a CSV file
  InjuryStats.java      days listed, team totals, diffs between snapshots
  Csv.java              minimal CSV quoting
src/test/               JUnit 5 tests and sample API responses
data/injuries.csv       the history, written by fetch --save (and the daily workflow)
legacy/                 the original single-file SportsData.io version and its sample CSV
.github/workflows/      build.yml (tests on every push), daily-snapshot.yml (scheduled fetch)
```

## Tech stack

Java 17, `java.net.http`, Jackson, JUnit 5, Maven (shade plugin for the
runnable jar), GitHub Actions.

## Data

MLB data is copyright MLB Advanced Media and is used here for personal,
non-commercial purposes.

## License

MIT (code only)
