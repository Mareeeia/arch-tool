/** Architecture Toolbox interactive graph UI. */

const state = {
  depth: 2,
  scope: "",
  group: "package",
  expanded: new Set(),
  overlay: "none",
  cy: null,
  positions: {},
  graphRequest: 0,
  graphFingerprint: "",
};

const cyEl = document.getElementById("cy");
const tooltip = document.getElementById("tooltip");

const layoutOpts = {
  name: "cose",
  animate: false,
  padding: 40,
  nodeRepulsion: 8000,
  idealEdgeLength: 100,
  numIter: 500,
  refresh: 30,
};

const cyStyles = [
  {
    selector: "node",
    style: {
      shape: "ellipse",
      label: "data(label)",
      "text-valign": "center",
      "text-halign": "center",
      "font-size": 9,
      "font-family": "Segoe UI, system-ui, sans-serif",
      color: "#ffffff",
      "text-outline-width": 1.5,
      "text-outline-color": "#1a1a1a",
      "text-wrap": "wrap",
      "text-max-width": 80,
      width: (ele) => nodeSize(ele.data()),
      height: (ele) => nodeSize(ele.data()),
      "background-color": (ele) => nodeColor(ele.data()),
      "border-width": 1,
      "border-color": "#1a1a1a",
    },
  },
  {
    selector: "node.highlight",
    style: {
      "border-width": 2.5,
      "border-color": "#2563eb",
    },
  },
  {
    selector: "edge",
    style: {
      width: (ele) => Math.max(1, Math.log(ele.data("weight") + 1) * 1.2),
      "line-color": (ele) => (ele.data("cyclic") ? "#dc2626" : "#374151"),
      "target-arrow-color": (ele) => (ele.data("cyclic") ? "#dc2626" : "#374151"),
      "target-arrow-shape": "triangle",
      "arrow-scale": 0.8,
      "curve-style": "bezier",
      opacity: 0.9,
    },
  },
  {
    selector: "edge.highlight",
    style: { "line-color": "#2563eb", width: 2.5 },
  },
];

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

let loadGraphTimer = null;
function scheduleLoadGraph() {
  clearTimeout(loadGraphTimer);
  loadGraphTimer = setTimeout(() => {
    loadGraph().catch((err) => console.error("Graph load failed:", err));
  }, 200);
}

async function loadScopes() {
  const scopes = await api("/api/scopes");
  const select = document.getElementById("scope");
  const current = state.scope;
  select.replaceChildren();
  const all = document.createElement("option");
  all.value = "";
  all.textContent = "All packages";
  select.appendChild(all);
  scopes.forEach((scope) => {
    const option = document.createElement("option");
    option.value = scope;
    option.textContent = scope;
    select.appendChild(option);
  });
  select.value = scopes.includes(current) ? current : "";
  state.scope = select.value;
}

async function loadGraph() {
  const requestId = ++state.graphRequest;
  const scopeParam = state.scope.trim() ? `&scope=${encodeURIComponent(state.scope.trim())}` : "";
  const url = `/api/graph?depth=${state.depth}&expanded=${expandedParam()}&overlay=${state.overlay}&group=${encodeURIComponent(state.group)}${scopeParam}`;
  const data = await api(url);
  if (requestId !== state.graphRequest) return;
  renderGraph(data);
}

function nodeColor(data) {
  if (state.overlay === "hotspot" && data.hotspotScore != null) {
    const t = data.hotspotScore;
    const r = Math.round(255 * t + 120 * (1 - t));
    const g = Math.round(80 * (1 - t));
    const b = Math.round(80 * (1 - t));
    return `rgb(${r},${g},${b})`;
  }
  if (state.overlay === "busfactor" && data.busFactor != null) {
    if (data.busFactor >= 3) return "#059669";
    if (data.busFactor === 2) return "#d97706";
    return "#dc2626";
  }
  const hue = hashStr(data.id) % 360;
  return `hsl(${hue}, 58%, 52%)`;
}

function hashStr(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

function nodeSize(data) {
  const base = data.loc > 0 ? Math.sqrt(data.loc) : Math.sqrt(data.classCount || 1);
  return Math.max(28, Math.min(72, base * 4));
}

function elementData(element) {
  return element.data ?? element;
}

function buildElements(data) {
  return [
    ...data.nodes.map((n) => {
      const nodeData = elementData(n);
      const element = { group: "nodes", data: nodeData };
      const saved = state.positions[nodeData.id];
      if (saved) element.position = saved;
      return element;
    }),
    ...data.edges.map((e) => ({ group: "edges", data: elementData(e) })),
  ];
}

function graphFingerprint(data) {
  const nodes = data.nodes.map((n) => elementData(n).id).sort().join("\0");
  const edges = data.edges
    .map((e) => {
      const d = elementData(e);
      return `${d.source}\t${d.target}`;
    })
    .sort()
    .join("\0");
  return `${nodes}|${edges}`;
}

let explodeInFlight = false;

async function explodeNode(nodeId) {
  if (explodeInFlight) return;
  explodeInFlight = true;
  try {
    const scopeParam = state.scope.trim() ? `&scope=${encodeURIComponent(state.scope.trim())}` : "";
    const url =
      `/api/graph/nodes/${encodeURIComponent(nodeId)}/children?depth=${state.depth}` +
      `&expanded=${expandedParam()}&overlay=${state.overlay}` +
      `&group=${encodeURIComponent(state.group)}${scopeParam}`;
    const data = await api(url);
    if (!data.nodes?.length) return;
    state.expanded.add(nodeId);
    await loadGraph();
  } finally {
    explodeInFlight = false;
  }
}

function bindCyEvents() {
  state.cy.on("dragfree", "node", (evt) => {
    state.positions[evt.target.id()] = evt.target.position();
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

  state.cy.on("tap", "node", (evt) => {
    const oe = evt.originalEvent;
    if (!oe.metaKey && !oe.ctrlKey) return;
    explodeNode(evt.target.id()).catch((err) => console.error("Explode failed:", err));
  });
}

function runLayout({ randomize = false } = {}) {
  if (!state.cy || state.cy.nodes().length === 0) {
    cyEl.style.visibility = "visible";
    return;
  }
  cyEl.style.visibility = "hidden";
  state.cy.stop();
  const layout = state.cy.layout({ ...layoutOpts, randomize });
  layout.one("layoutstop", () => {
    requestAnimationFrame(() => {
      cyEl.style.visibility = "visible";
    });
  });
  layout.run();
}

function updateGraphData(data) {
  state.cy.batch(() => {
    data.nodes.forEach((n) => {
      const d = elementData(n);
      const node = state.cy.getElementById(d.id);
      if (node.nonempty()) node.data(d);
    });
    data.edges.forEach((e) => {
      const d = elementData(e);
      const edge = state.cy.getElementById(d.id);
      if (edge.nonempty()) edge.data(d);
    });
  });
}

function renderGraph(data) {
  const fingerprint = graphFingerprint(data);
  const structureChanged = fingerprint !== state.graphFingerprint;
  state.graphFingerprint = fingerprint;

  if (state.cy && !structureChanged) {
    updateGraphData(data);
    return;
  }

  const elements = buildElements(data);
  const positionedCount = data.nodes.filter((n) => state.positions[elementData(n).id]).length;
  const randomize = positionedCount < data.nodes.length * 0.5;

  if (!state.cy) {
    state.cy = cytoscape({
      container: cyEl,
      elements,
      minZoom: 0.2,
      maxZoom: 4,
      style: cyStyles,
    });
    bindCyEvents();
    runLayout({ randomize: true });
    return;
  }

  state.cy.stop();
  state.cy.batch(() => {
    state.cy.elements().remove();
    state.cy.add(elements);
  });
  runLayout({ randomize });
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
  if (!state.cy) return;
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
    el.innerHTML = "<p class='empty'>No data</p>";
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

document.getElementById("depth").addEventListener("input", (e) => {
  state.depth = parseInt(e.target.value, 10);
  state.expanded.clear();
  scheduleLoadGraph();
});

document.getElementById("overlay").addEventListener("change", (e) => {
  state.overlay = e.target.value;
  scheduleLoadGraph();
});

document.getElementById("group").addEventListener("change", (e) => {
  state.group = e.target.value;
  state.expanded.clear();
  scheduleLoadGraph();
});

document.getElementById("scope").addEventListener("change", (e) => {
  state.scope = e.target.value;
  state.expanded.clear();
  scheduleLoadGraph();
});

document.getElementById("search").addEventListener("input", (e) => {
  if (!state.cy) return;
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
  try {
    await waitReady();
    await loadScopes();
    await loadGraph();
    await loadMetrics();
  } catch (err) {
    console.error("UI init failed:", err);
  }
})();
