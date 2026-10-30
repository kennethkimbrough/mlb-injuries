import { bodyPart, countBy, diff, isOffseason, isPitcher, longestOut, parseCsv, snapshots } from "./injuries.js";

const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const fmtDate = (iso) => new Date(`${iso}T12:00:00Z`).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" });

let text;
try {
  const res = await fetch("injuries.csv", { cache: "no-cache" });
  if (!res.ok) throw new Error(res.statusText);
  text = await res.text();
} catch (e) {
  $("#status").textContent = `Could not load the history (${e.message}).`;
  throw e;
}

const days = snapshots(parseCsv(text));
const latest = days[days.length - 1];
const withPlayers = days.filter((d) => d.players.length);
const shown = withPlayers[withPlayers.length - 1];

if (!shown) {
  $("#status").textContent = "No players recorded yet.";
} else {
  render();
}

function render() {
  const players = shown.players;
  const pitchers = players.filter(isPitcher).length;
  $("#status").textContent = `Snapshot of ${fmtDate(shown.date)} · ${days.length} day${days.length === 1 ? "" : "s"} of history since ${fmtDate(days[0].date)}`;

  if (latest !== shown) {
    $("#banner").innerHTML = `<p class="banner">The injured lists have been empty since ${fmtDate(latest.date)}: players are reinstated after the World Series and the lists stay empty until spring training. Below is the last day with players listed, ${fmtDate(shown.date)}.</p>`;
  } else if (isOffseason(shown.date)) {
    $("#banner").innerHTML = `<p class="banner">It's the offseason: after the World Series, players are reinstated and the lists empty out until spring training, so expect this list to shrink and then stop.</p>`;
  }

  // Tiles
  const parts = countBy(players, (p) => bodyPart(p.injury)).filter(([k]) => k !== "Not stated" && k !== "Other");
  const longest = longestOut(shown);
  $("#tiles").innerHTML = [
    [players.length, "players on an injured list"],
    [`${Math.round((pitchers / players.length) * 100)}%`, `are pitchers (${pitchers})`],
    [parts[0] ? parts[0][0] : "–", parts[0] ? `most common injury site (${parts[0][1]})` : "most common injury site"],
    [longest[0] ? `${longest[0].days}d` : "–", longest[0] ? `longest current stint: ${esc(longest[0].name)}` : "longest current stint"],
  ].map(([v, k]) => `<div class="tile"><div class="v">${v}</div><div class="k">${k}</div></div>`).join("");

  // Body parts, split pitchers / position players
  const allParts = countBy(players, (p) => bodyPart(p.injury));
  const max = allParts[0][1];
  $("#parts").innerHTML = allParts.map(([part, n]) => {
    const p = players.filter((x) => bodyPart(x.injury) === part && isPitcher(x)).length;
    return `<div class="bar"><span class="name">${esc(part)}</span><span class="track"><span class="p" style="width:${(p / max) * 100}%"></span><span class="h" style="width:${((n - p) / max) * 100}%"></span></span><span class="n">${n}</span></div>`;
  }).join("");

  // Teams
  const teams = countBy(players, (p) => p.team);
  const tmax = teams[0][1];
  $("#teams").innerHTML = teams.map(([team, n]) =>
    `<div class="bar"><button type="button" data-team="${esc(team)}" aria-label="Show ${esc(team)} in the full list">${esc(team)}</button><span class="track"><span class="h" style="width:${(n / tmax) * 100}%;background:var(--navy)"></span></span><span class="n">${n}</span></div>`).join("");
  $("#teams").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-team]"); if (!b) return;
    $("#team").value = b.dataset.team; renderRows();
    $("#q").closest("section").scrollIntoView({ behavior: "smooth", block: "start" });
  });

  // Longest out
  $("#longest").innerHTML = longest.slice(0, 10).map((p) =>
    `<li><strong>${esc(p.name)}</strong> <span class="muted">${esc(p.team)} ${esc(p.position)}</span> · ${esc(p.injury || "injury not stated")} <span class="days">${p.days} days</span></li>`).join("");

  // Changes since the previous snapshot
  const i = days.indexOf(shown);
  if (i > 0) {
    const prev = days[i - 1];
    const d = diff(prev, shown);
    $("#changes-sub").textContent = `${fmtDate(prev.date)} to ${fmtDate(shown.date)}.`;
    const list = (ps, empty) => ps.length ? `<ul>${ps.map((p) => `<li>${esc(p.name)} <span class="muted">${esc(p.team)} ${esc(p.position)}${p.injury ? ` · ${esc(p.injury)}` : ""}</span></li>`).join("")}</ul>` : `<p class="muted">${empty}</p>`;
    $("#changes").innerHTML = `<div><h3>Went on (${d.added.length})</h3>${list(d.added, "Nobody.")}</div><div><h3>Came off (${d.removed.length})</h3>${list(d.removed, "Nobody.")}</div>`;
  } else {
    $("#changes-sub").textContent = "The history starts with this snapshot, so there is no earlier day to compare with yet. This fills in from the next daily run.";
  }

  // History
  renderHistory();

  // Table
  for (const [team] of [...teams].sort((a, b) => a[0].localeCompare(b[0]))) {
    const o = document.createElement("option"); o.value = team; o.textContent = team; $("#team").append(o);
  }
  for (const id of ["#q", "#team", "#pos"]) $(id).addEventListener("input", renderRows);
  renderRows();
}

function renderRows() {
  const q = $("#q").value.trim().toLowerCase(), team = $("#team").value, pos = $("#pos").value;
  const byDays = new Map(longestOut(shown).map((p) => [p.player_id, p.days]));
  const rows = shown.players
    .filter((p) => !team || p.team === team)
    .filter((p) => !pos || (pos === "P") === isPitcher(p))
    .filter((p) => !q || `${p.name} ${p.injury}`.toLowerCase().includes(q))
    .sort((a, b) => a.team.localeCompare(b.team) || a.name.localeCompare(b.name));
  $("#count").textContent = `${rows.length} of ${shown.players.length} players`;
  $("#rows").innerHTML = rows.map((p) => `<tr><td class="team">${esc(p.team)}</td><td>${esc(p.name)}</td><td>${esc(p.position)}</td><td>${esc(p.status)}</td>` +
    `<td>${p.injury ? esc(p.injury) : '<span class="muted">not stated</span>'}</td><td class="num">${esc(p.start_date)}</td><td class="num">${byDays.get(p.player_id) ?? ""}</td></tr>`).join("");
}

function renderHistory() {
  if (days.length < 2) {
    $("#history-sub").textContent = `One snapshot so far (${fmtDate(days[0].date)}). A line appears here once there are two.`;
    return;
  }
  $("#history-sub").textContent = `${days.length} daily snapshots. Empty days are the offseason.`;
  const W = 900, H = 200, L = 34, R = 10, T = 10, B = 24;
  const t0 = Date.parse(days[0].date), t1 = Date.parse(latest.date);
  const ymax = Math.max(...days.map((d) => d.players.length), 10);
  const x = (d) => L + ((Date.parse(d) - t0) / Math.max(t1 - t0, 1)) * (W - L - R);
  const y = (n) => T + (1 - n / ymax) * (H - T - B);
  const pts = days.map((d) => `${x(d.date).toFixed(1)},${y(d.players.length).toFixed(1)}`).join(" ");
  const ticks = [0, Math.round(ymax / 2), ymax];
  $("#history").innerHTML = `<svg viewBox="0 0 ${W} ${H}" width="100%" role="img" aria-label="Players on injured lists per day">
    ${ticks.map((n) => `<line x1="${L}" x2="${W - R}" y1="${y(n)}" y2="${y(n)}" stroke="var(--line)"/><text x="${L - 6}" y="${y(n) + 4}" text-anchor="end">${n}</text>`).join("")}
    <polyline points="${pts}" fill="none" stroke="var(--accent)" stroke-width="2.5" stroke-linejoin="round"/>
    <text x="${L}" y="${H - 6}">${fmtDate(days[0].date)}</text><text x="${W - R}" y="${H - 6}" text-anchor="end">${fmtDate(latest.date)}</text>
  </svg>`;
}
