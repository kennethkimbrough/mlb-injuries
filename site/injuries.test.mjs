import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { bodyPart, countBy, daysBetween, diff, isOffseason, longestOut, parseCsv, snapshots } from "./injuries.js";

const csv = `snapshot_date,player_id,name,team,position,status,injury,start_date,notes
2026-09-01,1,Alex Example,NYY,P,60-day IL,Right elbow UCL sprain,2026-04-12,"Yankees placed RHP Alex Example on the 60-day injured list. Right elbow, ""UCL"" sprain."
2026-09-01,2,Blake Fixture,BOS,LF,10-day IL,,2026-08-30,
2026-09-02,1,Alex Example,NYY,P,60-day IL,Right elbow UCL sprain,2026-04-12,
2026-09-02,3,Casey Sample,BOS,C,15-day IL,Left hamstring strain,2026-09-02,
2026-11-05,,,,,,,,
`;

test("parses quoted fields, including commas and doubled quotes", () => {
  const rows = parseCsv(csv);
  assert.equal(rows.length, 5);
  assert.equal(rows[0].notes, 'Yankees placed RHP Alex Example on the 60-day injured list. Right elbow, "UCL" sprain.');
  assert.equal(rows[1].injury, "");
});

test("groups rows into snapshots and keeps empty offseason days", () => {
  const s = snapshots(parseCsv(csv));
  assert.deepEqual(s.map((x) => [x.date, x.players.length]), [["2026-09-01", 2], ["2026-09-02", 2], ["2026-11-05", 0]]);
});

test("diff finds who came off and who was added", () => {
  const [a, b] = snapshots(parseCsv(csv));
  const d = diff(a, b);
  assert.deepEqual(d.added.map((p) => p.name), ["Casey Sample"]);
  assert.deepEqual(d.removed.map((p) => p.name), ["Blake Fixture"]);
});

test("longest out counts days to the snapshot date and skips missing dates", () => {
  const [a] = snapshots(parseCsv(csv));
  const l = longestOut(a);
  assert.equal(l[0].name, "Alex Example");
  assert.equal(l[0].days, daysBetween("2026-04-12", "2026-09-01"));
  assert.equal(l.length, 2);
});

test("body parts", () => {
  assert.equal(bodyPart("Right elbow UCL sprain"), "Elbow");
  assert.equal(bodyPart("Right Shoulder Labral Repair"), "Shoulder");
  assert.equal(bodyPart("Recovery from left patellar tendon repair"), "Knee");
  assert.equal(bodyPart("Lumbar degenerative disk disease"), "Back");
  assert.equal(bodyPart("Left rib cage stress reaction"), "Oblique and core");
  assert.equal(bodyPart("Left orbital fracture"), "Head and neck");
  assert.equal(bodyPart("Fractured right thumb"), "Hand and wrist");
  assert.equal(bodyPart(""), "Not stated");
  assert.equal(bodyPart("Illness"), "Other");
});

test("countBy sorts by count, then key", () => {
  assert.deepEqual(countBy(["b", "a", "b", "c", "a", "b"], (x) => x), [["b", 3], ["a", 2], ["c", 1]]);
});

test("offseason months", () => {
  assert.equal(isOffseason("2026-11-05"), true);
  assert.equal(isOffseason("2027-02-28"), true);
  assert.equal(isOffseason("2027-03-01"), false);
});

test("the real history parses, and every snapshot date is a date", () => {
  const rows = parseCsv(readFileSync(new URL("../data/injuries.csv", import.meta.url), "utf8"));
  const s = snapshots(rows);
  assert.ok(s.length >= 1);
  for (const x of s) assert.match(x.date, /^\d{4}-\d{2}-\d{2}$/);
  // Most real injuries should map to a named body part, not "Other".
  const latest = s[s.length - 1];
  if (latest.players.length) {
    const other = latest.players.filter((p) => bodyPart(p.injury) === "Other").length;
    assert.ok(other / latest.players.length < 0.15, `${other} of ${latest.players.length} injuries unclassified`);
  }
});
