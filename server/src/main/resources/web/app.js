/** Architecture Toolbox interactive graph UI. */

const state = {
  depth: 2,
  scope: "",
  group: "package",
  expanded: new Set(),
  hidden: new Set(),
  overlay: "none",
  cy: null,
  positions: {},
  graphRequest: 0,
  graphFingerprint: "",
};

const cyEl = document.getElementById("cy");
const tooltip = document.getElementById("tooltip");
const modifierKeys = { d: false };

function isTypingTarget(target) {
  return target instanceof HTMLElement && target.matches("input, select, textarea");
}

function bindKeyboard() {
  document.addEventListener("keydown", (e) => {
    if (isTypingTarget(e.target)) return;
    if (e.key === "d" || e.key === "D") modifierKeys.d = true;
  });
  document.addEventListener("keyup", (e) => {
    if (e.key === "d" || e.key === "D") modifierKeys.d = false;
  });
  window.addEventListener("blur", () => {
    modifierKeys.d = false;
  });
}

function filterGraphData(data) {
  if (state.hidden.size === 0) return data;
  const nodes = data.nodes.filter((n) => !state.hidden.has(elementData(n).id));
  const visibleIds = new Set(nodes.map((n) => elementData(n).id));
  const edges = data.edges.filter((e) => {
    const d = elementData(e);
    return visibleIds.has(d.source) && visibleIds.has(d.target);
  });
  const couplingEdges = (data.couplingEdges ?? []).filter((e) => {
    const d = elementData(e);
    return visibleIds.has(d.source) && visibleIds.has(d.target);
  });
  return { ...data, nodes, edges, couplingEdges };
}

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
    selector: "edge[kind = 'coupling']",
    style: {
      "curve-style": "bezier",
      "line-style": "dashed",
      "line-dash-pattern": [6, 4],
      "line-color": "#9333ea",
      width: (ele) => 1 + (ele.data("confidence") ?? 0) * 3,
      opacity: 0.5,
      "target-arrow-shape": "none",
      "z-index-compare": "manual",
      "z-index": 0,
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
      "z-index-compare": "manual",
      "z-index": 10,
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

async function loadGraph(options = {}) {
  const requestId = ++state.graphRequest;
  const scopeParam = state.scope.trim() ? `&scope=${encodeURIComponent(state.scope.trim())}` : "";
  const url = `/api/graph?depth=${state.depth}&expanded=${expandedParam()}&overlay=${state.overlay}&group=${encodeURIComponent(state.group)}${scopeParam}`;
  const data = filterGraphData(await api(url));
  if (requestId !== state.graphRequest) return;
  renderGraph(data, options);
}

function hideNode(nodeId) {
  if (state.hidden.has(nodeId)) return;
  capturePositions();
  state.hidden.add(nodeId);
  delete state.positions[nodeId];
  if (!state.cy) return;
  state.cy.getElementById(nodeId).remove();
  state.graphFingerprint = "";
  showGraph();
}

const instabilityScale = chroma.scale("RdYlGn").domain([1, 0]);

function stabilityColor(instability) {
  if (instability == null) return "#94a3b8";
  return instabilityScale(Math.max(0, Math.min(1, instability))).hex();
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
  return stabilityColor(data.instability ?? null);
}

function nodeSize(data) {
  const base = data.loc > 0 ? Math.sqrt(data.loc) : Math.sqrt(data.classCount || 1);
  return Math.max(28, Math.min(72, base * 4));
}

function elementData(element) {
  return element.data ?? element;
}

function couplingElements(edges) {
  return (edges ?? []).flatMap((e) => {
    const d = elementData(e);
    return [
      { group: "edges", data: { ...d, id: `${d.id}:fwd`, source: d.source, target: d.target } },
      { group: "edges", data: { ...d, id: `${d.id}:rev`, source: d.target, target: d.source } },
    ];
  });
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
    ...couplingElements(data.couplingEdges),
  ];
}

function capturePositions() {
  if (!state.cy) return;
  state.cy.nodes().forEach((n) => {
    state.positions[n.id()] = { ...n.position() };
  });
}

function placeNewNodes(nodeIds, anchorPos) {
  if (!anchorPos) return;
  const radius = 60 + nodeIds.length * 8;
  nodeIds.forEach((id, i) => {
    if (state.positions[id]) return;
    const angle = (2 * Math.PI * i) / Math.max(nodeIds.length, 1);
    state.positions[id] = {
      x: anchorPos.x + radius * Math.cos(angle),
      y: anchorPos.y + radius * Math.sin(angle),
    };
  });
}

function applySavedPositions() {
  if (!state.cy) return;
  state.cy.nodes().forEach((n) => {
    const pos = state.positions[n.id()];
    if (pos) n.position(pos);
  });
}

function showGraph() {
  requestAnimationFrame(() => {
    cyEl.style.visibility = "visible";
  });
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
  const coupling = (data.couplingEdges ?? [])
    .map((e) => {
      const d = elementData(e);
      return `${d.source}\t${d.target}\t${d.confidence}`;
    })
    .sort()
    .join("\0");
  return `${nodes}|${edges}|${coupling}`;
}

let explodeInFlight = false;

async function explodeNode(nodeId) {
  if (explodeInFlight) return;
  explodeInFlight = true;
  try {
    capturePositions();
    const parent = state.cy?.getElementById(nodeId);
    const parentPos = state.positions[nodeId] ?? (parent?.nonempty() ? { ...parent.position() } : null);

    const scopeParam = state.scope.trim() ? `&scope=${encodeURIComponent(state.scope.trim())}` : "";
    const url =
      `/api/graph/nodes/${encodeURIComponent(nodeId)}/children?depth=${state.depth}` +
      `&expanded=${expandedParam()}&overlay=${state.overlay}` +
      `&group=${encodeURIComponent(state.group)}${scopeParam}`;
    const data = await api(url);
    if (!data.nodes?.length) return;

    const childIds = data.nodes.map((n) => elementData(n).id);
    state.expanded.add(nodeId);
    delete state.positions[nodeId];
    await loadGraph({ relayout: "none", anchorPos: parentPos, newNodeIds: childIds });
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
    const d = evt.target.data();
    tooltip.style.display = "block";
    tooltip.style.left = "50%";
    tooltip.style.top = "60px";
    if (d.kind === "coupling") {
      tooltip.textContent =
        `Temporal coupling ${d.source} ↔ ${d.target}: ${(d.confidence * 100).toFixed(0)}%` +
        ` (${d.coChanges} co-changes, ${d.filePairs} file pairs)`;
      return;
    }
    const w = d.weight;
    tooltip.textContent = `${d.source} → ${d.target}: ${w} class-level deps`;
  });

  state.cy.on("tap", "node", (evt) => {
    const nodeId = evt.target.id();
    if (modifierKeys.d) {
      hideNode(nodeId);
      return;
    }
    const oe = evt.originalEvent;
    if (!oe.metaKey && !oe.ctrlKey) return;
    explodeNode(nodeId).catch((err) => console.error("Explode failed:", err));
  });
}

function runLayout({ randomize = false, capture = true } = {}) {
  if (!state.cy || state.cy.nodes().length === 0) {
    showGraph();
    return;
  }
  cyEl.style.visibility = "hidden";
  state.cy.stop();
  const layout = state.cy.layout({ ...layoutOpts, randomize });
  layout.one("layoutstop", () => {
    if (capture) capturePositions();
    showGraph();
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

function renderGraph(data, { relayout = "auto", anchorPos = null, newNodeIds = [] } = {}) {
  const fingerprint = graphFingerprint(data);
  const structureChanged = fingerprint !== state.graphFingerprint;
  state.graphFingerprint = fingerprint;

  if (state.cy && !structureChanged) {
    updateGraphData(data);
    return;
  }

  if (relayout === "none") {
    placeNewNodes(newNodeIds, anchorPos);
  }

  const nodeIds = new Set(data.nodes.map((n) => elementData(n).id));
  Object.keys(state.positions).forEach((id) => {
    if (!nodeIds.has(id)) delete state.positions[id];
  });

  const elements = buildElements(data);
  const positionedCount = data.nodes.filter((n) => state.positions[elementData(n).id]).length;
  const randomize = relayout === "full" || (relayout === "auto" && positionedCount < data.nodes.length * 0.5);

  if (!state.cy) {
    state.cy = cytoscape({
      container: cyEl,
      elements,
      minZoom: 0.2,
      maxZoom: 4,
      style: cyStyles,
    });
    bindCyEvents();
    if (relayout === "none") {
      applySavedPositions();
      showGraph();
    } else {
      runLayout({ randomize: true });
    }
    return;
  }

  state.cy.stop();
  cyEl.style.visibility = "hidden";
  state.cy.batch(() => {
    state.cy.elements().remove();
    state.cy.add(elements);
  });

  if (relayout === "none") {
    applySavedPositions();
    showGraph();
    return;
  }

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
    ${d.instability != null ? `Instability: ${d.instability.toFixed(2)}<br/>` : "No dependencies<br/>"}
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
  state.hidden.clear();
  state.positions = {};
  scheduleLoadGraph();
});

document.getElementById("overlay").addEventListener("change", (e) => {
  state.overlay = e.target.value;
  scheduleLoadGraph();
});

document.getElementById("group").addEventListener("change", (e) => {
  state.group = e.target.value;
  state.expanded.clear();
  state.hidden.clear();
  state.positions = {};
  scheduleLoadGraph();
});

document.getElementById("scope").addEventListener("change", (e) => {
  state.scope = e.target.value;
  state.expanded.clear();
  state.hidden.clear();
  state.positions = {};
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

const PANEL_MIN = 200;
const PANEL_MAX = 560;
const workspace = document.getElementById("workspace");
const panelResizer = document.getElementById("panel-resizer");
const panelOpen = document.getElementById("panel-open");
const panelClose = document.getElementById("panel-close");

function resizeCy() {
  if (state.cy) state.cy.resize();
}

function panelWidthPx() {
  return parseInt(getComputedStyle(workspace).getPropertyValue("--panel-width"), 10) || 280;
}

function setPanelWidth(width) {
  const w = Math.max(PANEL_MIN, Math.min(PANEL_MAX, width));
  workspace.style.setProperty("--panel-width", `${w}px`);
  resizeCy();
}

function setPanelCollapsed(collapsed) {
  workspace.classList.toggle("panel-collapsed", collapsed);
  panelOpen.hidden = !collapsed;
  resizeCy();
}

function bindPanelControls() {
  panelClose.addEventListener("click", () => setPanelCollapsed(true));
  panelOpen.addEventListener("click", () => setPanelCollapsed(false));

  panelResizer.addEventListener("mousedown", (e) => {
    if (workspace.classList.contains("panel-collapsed")) return;
    e.preventDefault();
    const startX = e.clientX;
    const startW = panelWidthPx();
    panelResizer.classList.add("dragging");
    document.body.classList.add("panel-resizing");

    function onMove(ev) {
      setPanelWidth(startW + (startX - ev.clientX));
    }

    function onUp() {
      panelResizer.classList.remove("dragging");
      document.body.classList.remove("panel-resizing");
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
      resizeCy();
    }

    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  });

  window.addEventListener("resize", () => resizeCy());
}

(async () => {
  try {
    bindKeyboard();
    bindPanelControls();
    await waitReady();
    await loadScopes();
    await loadGraph();
    await loadMetrics();
  } catch (err) {
    console.error("UI init failed:", err);
  }
})();
