// Pure functions over data/injuries.csv: parsing, snapshots, groupings and diffs.
// No DOM here, so the same code runs in the page and under node:test.

/** Parse CSV text (RFC 4180 quoting) into an array of objects keyed by the header row. */
export function parseCsv(text) {
  const rows = [];
  let row = [], field = "", quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (ch === '"') quoted = false;
      else field += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ",") { row.push(field); field = ""; }
    else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i++;
      row.push(field); rows.push(row); row = []; field = "";
    } else field += ch;
  }
  if (field !== "" || row.length) { row.push(field); rows.push(row); }
  const [header, ...body] = rows.filter((r) => r.length > 1 || r[0] !== "");
  if (!header) return [];
  return body.map((r) => Object.fromEntries(header.map((h, i) => [h, r[i] ?? ""])));
}

/**
 * Group rows into daily snapshots, oldest first. A row with only a date marks a day on
 * which the lists were empty (the offseason), so that snapshot has no players.
 */
export function snapshots(rows) {
  const byDate = new Map();
  for (const r of rows) {
    if (!r.snapshot_date) continue;
    if (!byDate.has(r.snapshot_date)) byDate.set(r.snapshot_date, []);
    if (r.player_id) byDate.get(r.snapshot_date).push(r);
  }
  return [...byDate.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, players]) => ({ date, players }));
}

const BODY_PARTS = [
  ["Elbow", /\belbow|\bucl\b|tommy john|flexor/i],
  ["Shoulder", /shoulder|labr(um|al)|rotator|thoracic outlet/i],
  ["Forearm", /forearm/i],
  ["Knee", /knee|acl\b|mcl\b|meniscus|patell/i],
  ["Hamstring", /hamstring/i],
  ["Back", /\bback\b|lumbar|spine|disk|disc/i],
  ["Oblique and core", /oblique|abdominal|core|rib|intercostal/i],
  ["Hip and groin", /\bhip\b|groin|adductor|hernia/i],
  ["Foot and ankle", /foot|ankle|achilles|toe\b|heel|plantar/i],
  ["Hand and wrist", /hand|wrist|finger|thumb|hamate|radius|ulna|blister/i],
  ["Leg", /calf|quad|thigh|shin|fibula|tibia/i],
  ["Arm", /triceps|biceps|lat\b|teres|arm\b/i],
  ["Head and neck", /concussion|head|neck|orbital|face|jaw/i],
];

/** A coarse body part for an injury description, or "Not stated" / "Other". */
export function bodyPart(injury) {
  if (!injury || !injury.trim()) return "Not stated";
  for (const [name, re] of BODY_PARTS) if (re.test(injury)) return name;
  return "Other";
}

export const isPitcher = (p) => p.position === "P" || p.position === "TWP";

/** Count items by key, largest first, ties by key. */
export function countBy(items, keyFn) {
  const m = new Map();
  for (const it of items) { const k = keyFn(it); m.set(k, (m.get(k) ?? 0) + 1); }
  return [...m.entries()].sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
}

/** Whole days between two ISO dates (b - a). */
export function daysBetween(a, b) {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);
}

/** Players in a snapshot sorted by how long they have been on the list, with the day count. */
export function longestOut(snapshot) {
  return snapshot.players
    .filter((p) => /^\d{4}-\d{2}-\d{2}$/.test(p.start_date))
    .map((p) => ({ ...p, days: daysBetween(p.start_date, snapshot.date) }))
    .sort((a, b) => b.days - a.days || a.name.localeCompare(b.name));
}

/** Who was added and who came off between two snapshots, by player id. */
export function diff(prev, curr) {
  const before = new Map(prev.players.map((p) => [p.player_id, p]));
  const after = new Map(curr.players.map((p) => [p.player_id, p]));
  return {
    added: curr.players.filter((p) => !before.has(p.player_id)),
    removed: prev.players.filter((p) => !after.has(p.player_id)),
  };
}

/** True between November and February, when MLB does not use the injured lists. */
export function isOffseason(isoDate) {
  const month = Number(isoDate.slice(5, 7));
  return month >= 11 || month <= 2;
}
