/** Architecture Toolbox interactive graph UI. */
cytoscape.use(cytoscapeFcose);

const state = {
  depth: 2,
  expanded: new Set(),
  overlay: "none",
  cy: null,
  positions: {},
};

const cyEl = document.getElementById("cy");
const tooltip = document.getElementById("tooltip");

async function api(path) {
  const res = await fetch(path);
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function waitReady() {
  for (;;) {
    const s = await api("/api/status");
    if (s.state === "ready") return;
    if (s.state === "error") throw new Error(s.error || "Analysis failed");
    await new Promise((r) => setTimeout(r, 500));
  }
}

function expandedParam() {
  return [...state.expanded].join(",");
}

async function loadGraph() {
  const url = `/api/graph?depth=${state.depth}&expanded=${expandedParam()}&overlay=${state.overlay}`;
  const data = await api(url);
  renderGraph(data);
}

function nodeColor(data) {
  if (state.overlay === "hotspot" && data.hotspotScore != null) {
    const t = data.hotspotScore;
    const r = Math.round(255 * t + 200 * (1 - t));
    const g = Math.round(200 * (1 - t));
    const b = Math.round(200 * (1 - t));
    return `rgb(${r},${g},${b})`;
  }
  if (state.overlay === "busfactor" && data.busFactor != null) {
    if (data.busFactor >= 3) return "#44dd88";
    if (data.busFactor === 2) return "#ffb347";
    return "#ff4466";
  }
  const hue = (hashStr(data.id) % 360);
  return `hsl(${hue}, 55%, 55%)`;
}

function hashStr(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

function nodeSize(data) {
  const base = data.loc > 0 ? Math.sqrt(data.loc) : Math.sqrt(data.classCount || 1);
  return Math.max(24, Math.min(80, base * 4));
}

function renderGraph(data) {
  const elements = [
    ...data.nodes.map((n) => ({ group: "nodes", data: n.data, position: state.positions[n.data.id] })),
    ...data.edges.map((e) => ({ group: "edges", data: e.data })),
  ];

  if (state.cy) {
    state.cy.destroy();
  }

  state.cy = cytoscape({
    container: cyEl,
    elements,
    minZoom: 0.2,
    maxZoom: 4,
    style: [
      {
        selector: "node",
        style: {
          label: "data(label)",
          "text-valign": "center",
          "text-halign": "center",
          "font-size": 9,
          color: "#fff",
          "text-outline-width": 2,
          "text-outline-color": "#000",
          width: (ele) => nodeSize(ele.data()),
          height: (ele) => nodeSize(ele.data()),
          "background-color": (ele) => nodeColor(ele.data()),
        },
      },
      {
        selector: "node.highlight",
        style: { "border-width": 3, "border-color": "#4cc9f0" },
      },
      {
        selector: "edge",
        style: {
          width: (ele) => Math.log(ele.data("weight") + 1) * 2 + 1,
          "line-color": (ele) => (ele.data("cyclic") ? "#ff4466" : "#666688"),
          "target-arrow-color": (ele) => (ele.data("cyclic") ? "#ff4466" : "#666688"),
          "target-arrow-shape": "triangle",
          "curve-style": "bezier",
          opacity: 0.85,
        },
      },
      {
        selector: "edge.highlight",
        style: { "line-color": "#4cc9f0", width: 4 },
      },
    ],
    layout: { name: "fcose", animate: true, randomize: false, quality: "default" },
  });

  state.cy.on("dragfree", "node", (evt) => {
    const id = evt.target.id();
    state.positions[id] = evt.target.position();
  });

  state.cy.on("mouseover", "node", (evt) => showTooltip(evt, evt.target.data()));
  state.cy.on("mouseout", "node", hideTooltip);

  state.cy.on("tap", "edge", (evt) => {
    const w = evt.target.data("weight");
    tooltip.style.display = "block";
    tooltip.style.left = "50%";
    tooltip.style.top = "60px";
    tooltip.textContent = `${evt.target.data("source")} → ${evt.target.data("target")}: ${w} class-level deps`;
  });

  state.cy.on("dbltap", "node", async (evt) => {
    const id = evt.target.id();
    if (state.expanded.has(id)) state.expanded.delete(id);
    else state.expanded.add(id);
    await loadGraph();
  });
}

function showTooltip(evt, d) {
  tooltip.style.display = "block";
  tooltip.style.left = evt.originalEvent.clientX + 12 + "px";
  tooltip.style.top = evt.originalEvent.clientY + 12 + "px";
  tooltip.innerHTML = `
    <strong>${d.label}</strong><br/>
    Classes: ${d.classCount} · LOC: ${d.loc}<br/>
    In: ${d.inDegree} · Out: ${d.outDegree}<br/>
    ${d.hotspotScore != null ? `Hotspot: ${d.hotspotScore.toFixed(2)}<br/>` : ""}
    ${d.busFactor != null ? `Bus factor: ${d.busFactor}` : ""}
  `;
}

function hideTooltip() {
  tooltip.style.display = "none";
}

function focusNode(id) {
  const node = state.cy.getElementById(id);
  if (node.length) {
    state.cy.elements().removeClass("highlight");
    node.addClass("highlight");
    state.cy.animate({ center: { eles: node }, zoom: 1.5 }, { duration: 400 });
  }
}

async function loadMetrics() {
  const [hotspots, coupling, busfactor] = await Promise.all([
    api("/api/metrics/hotspots?limit=50"),
    api("/api/metrics/coupling?limit=50"),
    api("/api/metrics/busfactor"),
  ]);
  renderTable("tab-hotspots", hotspots, (h) => [
    h.path.split("/").pop(),
    h.score.toFixed(2),
    h.revisions,
  ], (h) => pathToNodeId(h.path));
  renderTable("tab-coupling", coupling, (c) => [
    `${c.a.split("/").pop()} ↔ ${c.b.split("/").pop()}`,
    c.confidence.toFixed(2),
    c.coChanges,
  ], (c) => pathToNodeId(c.a));
  renderTable("tab-busfactor", busfactor, (b) => [
    b.component,
    b.busFactor,
    b.topAuthors.map((a) => a[0]).join(", "),
  ], (b) => b.component);
}

function pathToNodeId(path) {
  const parts = path.replace(/^.*src\/main\/java\//, "").replace(/\.java$/, "").split("/");
  if (parts.length <= state.depth) return parts.join(".");
  return parts.slice(0, state.depth).join(".");
}

function renderTable(containerId, rows, cols, nodeIdFn) {
  const el = document.getElementById(containerId);
  if (!rows.length) {
    el.innerHTML = "<p style='color:#888;padding:0.5rem'>No data</p>";
    return;
  }
  el.innerHTML = `<table><thead><tr>${cols(rows[0]).map(() => "<th></th>").join("")}</tr></thead><tbody></tbody></table>`;
  const tbody = el.querySelector("tbody");
  rows.forEach((row) => {
    const tr = document.createElement("tr");
    cols(row).forEach((c) => {
      const td = document.createElement("td");
      td.textContent = c;
      tr.appendChild(td);
    });
    tr.addEventListener("click", () => focusNode(nodeIdFn(row)));
    tbody.appendChild(tr);
  });
}

document.getElementById("depth").addEventListener("input", async (e) => {
  state.depth = parseInt(e.target.value, 10);
  await loadGraph();
});

document.getElementById("overlay").addEventListener("change", async (e) => {
  state.overlay = e.target.value;
  await loadGraph();
});

document.getElementById("search").addEventListener("input", (e) => {
  const q = e.target.value.toLowerCase();
  state.cy.elements().removeClass("highlight");
  if (!q) return;
  state.cy.nodes().forEach((n) => {
    if (n.data("label").toLowerCase().includes(q)) n.addClass("highlight");
  });
});

document.querySelectorAll(".tabs button").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tabs button").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(`tab-${btn.dataset.tab}`).classList.add("active");
  });
});

(async () => {
  await waitReady();
  await loadGraph();
  await loadMetrics();
})();
