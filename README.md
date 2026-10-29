# MLB injury tracker

[![Build](https://github.com/kennethkimbrough/mlb-injuries/actions/workflows/build.yml/badge.svg)](https://github.com/kennethkimbrough/mlb-injuries/actions/workflows/build.yml)

A command-line Java program that lists the players currently on MLB injury
lists, records a snapshot of that list each day, and reports who has been out
longest and which teams have been hit hardest.

Data comes from the [SportsData.io](https://sportsdata.io/) MLB API, which
requires an API key.

## What it does

```
$ java -jar target/mlb-injuries.jar fetch
Team  Player         Pos  Status      Body part  Since
----  -------------  ---  ----------  ---------  ----------
BOS   Casey Sample   C    Day-To-Day  Hamstring  2026-05-03
NYY   Alex Example   SP   Out         Elbow      2026-04-12
...
```

`fetch --save` also appends today's list to `data/injuries.csv`, one row per
player per day. Re-running on the same day replaces that day's rows rather
than duplicating them.

`report` reads the history and prints:

- the players who have appeared in the most daily snapshots, and whether they
  are still listed;
- each team's total player-days on the list, distinct players, and how many
  are listed in the latest snapshot;
- who came off the list and who was added since the previous snapshot.

A scheduled GitHub Actions workflow runs `fetch --save` every morning and
commits the updated CSV, so the history grows on its own once the
`SPORTSDATA_API_KEY` secret is set on the repository. Until it is, the
workflow skips with a note.

## Run it

Requires Java 17+ and Maven.

```sh
export SPORTSDATA_API_KEY=your_key   # from sportsdata.io

mvn -B package                       # builds target/mlb-injuries.jar and runs the tests
java -jar target/mlb-injuries.jar fetch --save
java -jar target/mlb-injuries.jar report --top 15
```

Options: `--history FILE` changes where the CSV lives (default
`data/injuries.csv`); `--top N` sets how many rows the report shows.

The key is read only from the environment variable and sent as a request
header, so it never appears in a URL, the history file or a log.

## Tests

```sh
mvn test
```

The tests run without a key or network: the parser is tested against a saved
sample response (`src/test/resources/injured-players-sample.json`, invented
players with the feed's real field names), the history file against a
temporary directory, and the statistics against a hand-built set of
snapshots.

## Layout

```
src/main/java/mlbinjuries/
  Main.java               command-line entry point (fetch, report)
  SportsDataClient.java   HTTP call to the InjuredPlayers endpoint
  InjuryParser.java       JSON array -> InjuredPlayer records, tolerant of missing fields
  InjuredPlayer.java      one row of the feed
  TablePrinter.java       aligned text table
  InjuryHistory.java      daily snapshots in a CSV file
  InjuryStats.java        days listed, team totals, diffs between snapshots
  Csv.java                minimal CSV quoting
src/test/                 JUnit 5 tests and the sample response
data/injuries.csv         the history, written by fetch --save (and the daily workflow)
legacy/                   the original single-file version and its hand-written sample CSV
.github/workflows/        build.yml (tests on every push), daily-snapshot.yml (scheduled fetch)
```

## Tech stack

Java 17, `java.net.http`, Jackson, JUnit 5, Maven (shade plugin for the
runnable jar), GitHub Actions.

## License

MIT
