(function () {
  "use strict";
  var initialView = document.body.getAttribute("data-view") || "today";
  var state = {
    view: initialView,
    projectId: document.body.getAttribute("data-project-id") || "",
    tasks: [],
    projects: [],
    counts: {},
    selected: null,
    proposal: null,
    pollTimer: null,
    mode: initialView === "all" || initialView === "projects" ? "board" : "list",
    query: "",
    dragTaskId: null,
    suppressClick: false
  };
  var $ = function (sel, root) { return (root || document).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  function esc(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  function api(path, opts) {
    opts = opts || {};
    var headers = Object.assign({ "X-GTM-Request": "1", "Accept": "application/json" }, opts.headers || {});
    if (opts.body && typeof opts.body !== "string") {
      headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(opts.body);
    }
    return fetch(path, Object.assign({}, opts, { headers: headers })).then(function (res) {
      if (res.status === 401) {
        window.location.assign("/login");
        throw new Error("Sign in required");
      }
      return res.text().then(function (text) {
        var data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = { detail: text }; }
        if (!res.ok) {
          var detail = data && data.detail ? data.detail : (data && data.error ? data.error.message : res.statusText);
          throw new Error(typeof detail === "string" ? detail : JSON.stringify(detail));
        }
        return data;
      });
    });
  }
  function toast(message, isError) {
    var el = $("#toast");
    el.textContent = message;
    el.classList.toggle("is-error", !!isError);
    el.classList.add("is-on");
    clearTimeout(el._timer);
    el._timer = setTimeout(function () { el.classList.remove("is-on"); }, 2800);
  }
  function priorityClass(p) {
    return "priority priority-" + (p === "none" ? "none" : p || "none");
  }
  function priorityLabel(p) {
    return ({ urgent: "Urgent", high: "High", medium: "Medium", low: "Low", none: "No priority" })[p] || "No priority";
  }
  function statusLabel(s) {
    return ({ backlog: "Backlog", research: "Research", sprint: "Sprint", review: "Review", done: "Done", cancelled: "Cancelled",
      inbox: "Backlog", todo: "Backlog", in_progress: "Sprint", waiting: "Review" })[s] || s;
  }
  function dateOnly(value) {
    if (!value) return "";
    var d = new Date(value);
    if (isNaN(d)) return value;
    return d.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
  }
  function dueLabel(task) {
    if (!task.due_at) return "";
    var d = new Date(task.due_at);
    if (isNaN(d)) return task.due_at;
    var now = new Date();
    var today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var day = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var diff = Math.round((day - today) / 86400000);
    if (diff < 0) return "Overdue · " + dateOnly(task.due_at);
    if (diff === 0) return task.due_all_day ? "Today" : "Today · " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    if (diff === 1) return "Tomorrow" + (task.due_all_day ? "" : " · " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }));
    return dateOnly(task.due_at);
  }
  function dueClass(task) {
    if (!task.due_at) return "";
    var d = new Date(task.due_at);
    if (isNaN(d)) return "";
    var now = new Date();
    var end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    if (d < now) return "due-overdue";
    if (d < end) return "due-today";
    return "";
  }
  function taskRow(task) {
    var done = task.status === "done";
    var due = dueLabel(task);
    var meta = [];
    meta.push('<span class="' + priorityClass(task.priority) + '">' + priorityLabel(task.priority) + "</span>");
    if (task.project_name) meta.push('<span class="meta-dot"></span><span>' + esc(task.project_name) + "</span>");
    if (due) meta.push('<span class="meta-dot"></span><span class="' + dueClass(task) + '">' + esc(due) + "</span>");
    if (task.tags && task.tags.length) meta.push('<span class="meta-dot"></span><span>' + esc(task.tags.map(function (t) { return t.name; }).join(", ")) + "</span>");
    return '<div class="task-row' + (done ? " is-done" : "") + '" data-task-id="' + esc(task.id) + '">' +
      '<button class="task-check' + (done ? " is-done" : "") + '" data-action="toggle" data-task-id="' + esc(task.id) + '" aria-label="' + (done ? "Reopen" : "Complete") + '"></button>' +
      '<div class="task-main"><div class="task-title">' + esc(task.title) + '</div><div class="task-meta">' + meta.join("") + "</div></div>" +
      '<div class="task-side"><button class="row-action" data-action="open" data-task-id="' + esc(task.id) + '" aria-label="Open task"><svg class="icon icon-sm"><use href="#i-chevron-right"></use></svg></button></div>' +
      "</div>";
  }
  function groupTasks(tasks) {
    var groups = { Overdue: [], Today: [], Upcoming: [], Later: [], "No date": [] };
    var now = new Date();
    var todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    var todayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    tasks.forEach(function (task) {
      if (!task.due_at) { groups["No date"].push(task); return; }
      var d = new Date(task.due_at);
      if (d < todayStart) groups.Overdue.push(task);
      else if (d < todayEnd) groups.Today.push(task);
      else if (d < new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7)) groups.Upcoming.push(task);
      else groups.Later.push(task);
    });
    return groups;
  }
  function renderList() {
    var container = $("#task-container");
    if (!state.tasks.length) {
      container.innerHTML = '<div class="empty-state"><strong>All clear.</strong>Capture a thought or ask AI to add something.<div class="empty-actions"><button class="btn btn-quiet btn-small" id="inline-add"><svg class="icon icon-sm"><use href="#i-plus"></use></svg>Add a task</button></div></div>';
      $("#task-count").textContent = "0";
      return;
    }
    $("#task-count").textContent = String(state.tasks.length);
    var groups = groupTasks(state.tasks);
    var html = "";
    Object.keys(groups).forEach(function (name) {
      var items = groups[name];
      if (!items.length) return;
      html += '<div class="task-section"><div class="task-section-head"><span>' + name + '</span><span>' + items.length + "</span></div>";
      items.forEach(function (task) { html += taskRow(task); });
      html += "</div>";
    });
    html += '<button class="add-row" id="inline-add"><svg class="icon icon-sm"><use href="#i-plus"></use></svg>Add a task</button>';
    container.innerHTML = html;
  }
  function renderBoard() {
    var cols = [
      { key: "backlog", label: "Backlog", statuses: ["backlog", "inbox", "todo"] },
      { key: "research", label: "Research", statuses: ["research"] },
      { key: "sprint", label: "Sprint", statuses: ["sprint", "in_progress"] },
      { key: "review", label: "Review", statuses: ["review", "waiting"] },
      { key: "done", label: "Done", statuses: ["done"] }
    ];
    var html = '<div class="board">';
    cols.forEach(function (col) {
      var items = state.tasks.filter(function (t) {
        return col.statuses.indexOf(t.status) !== -1;
      });
      html += '<div class="board-col" data-phase="' + col.key + '"><div class="board-col-head"><span>' + col.label + "</span><span>" + items.length + "</span></div>";
      if (!items.length) html += '<div class="board-empty">No cards yet</div>';
      items.forEach(function (t) {
        html += '<button class="board-card" draggable="true" title="Drag to another phase" data-task-id="' + esc(t.id) + '"><div class="task-title">' + esc(t.title) + '</div><div class="task-meta"><span class="' + priorityClass(t.priority) + '">' + priorityLabel(t.priority) + '</span></div></button>';
      });
      html += "</div>";
    });
    html += "</div>";
    $("#task-container").innerHTML = html;
    $("#task-count").textContent = String(state.tasks.length);
  }
  function renderTasks() {
    if (state.mode === "board") renderBoard(); else renderList();
    var open = state.tasks.filter(function (t) { return t.status !== "done"; }).length;
    var done = state.tasks.filter(function (t) { return t.status === "done"; }).length;
    $("#context-progress").textContent = done + " done";
    $("#context-total").textContent = open + " open";
    var pct = state.tasks.length ? Math.round(done / state.tasks.length * 100) : 0;
    $("#progress-fill").style.width = pct + "%";
    $("#task-panel-title").textContent = state.mode === "board" ? "Board" : "Tasks";
  }
  function renderSidebar() {
    Object.keys(state.counts).forEach(function (key) {
      var el = document.querySelector('[data-count="' + key + '"]');
      if (el) el.textContent = state.counts[key];
    });
    var list = $("#project-list");
    if (!list) return;
    if (!state.projects.length) { list.innerHTML = '<div class="empty-mini">No projects yet</div>'; return; }
    list.innerHTML = state.projects.map(function (p) {
      var active = state.projectId === p.id ? " is-active" : "";
      return '<a class="nav-item nav-project' + active + '" href="/projects/' + esc(p.id) + '"><span class="project-dot" style="background:' + esc(p.color || "#64748b") + '"></span><span>' + esc(p.name) + '</span><span class="nav-count">' + (p.active_count || 0) + "</span></a>";
    }).join("");
  }
  function updateHeader() {
    var titles = { all: "All tasks", backlog: "Backlog", research: "Research", sprint: "Sprint", review: "Review", done: "Done", settings: "Settings & system", projects: "Project" };
    var subs = {
      all: "Every task — open and completed — in one list.",
      backlog: "Ideas and untriaged work waiting to be picked up.",
      research: "Being investigated before it is committed.",
      sprint: "Work in progress this cycle.",
      review: "Waiting on review or on someone else.",
      done: "Completed in the last 30 days.",
      settings: "Local engine, paired devices, and maintenance.",
      projects: "Active work for this project."
    };
    var project = state.projects.filter(function (p) { return p.id === state.projectId; })[0];
    var filtered = !!state.projectId;
    var title = state.view === "projects" && project ? project.name : (titles[state.view] || "Tasks");
    $("#view-title").textContent = title;
    $("#view-subtitle").textContent = filtered && state.view !== "projects" && project
      ? "Filtered by " + project.name
      : (subs[state.view] || "");
    $("#eyebrow").textContent = state.view === "settings"
      ? "System"
      : filtered
        ? "Project filter"
        : state.view.charAt(0).toUpperCase() + state.view.slice(1);
    $("#context-date").textContent = new Date().toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" });
  }
  function loadTasks() {
    var params = new URLSearchParams();
    params.set("view", state.mode === "board" ? "board" : (state.view === "settings" ? "today" : state.view));
    if (state.projectId) params.set("project_id", state.projectId);
    if (state.query) params.set("q", state.query);
    if (state.mode === "board") params.set("include_done", "true");
    return api("/api/tasks?" + params.toString()).then(function (data) {
      state.tasks = data.items || [];
      state.counts = data.counts || state.counts;
      renderTasks();
      renderSidebar();
      updateHeader();
    }).catch(function (err) {
      $("#task-container").innerHTML = '<div class="empty-state"><strong>Could not load tasks.</strong>' + esc(err.message) + "</div>";
    });
  }
  function loadProjects() {
    return api("/api/projects").then(function (data) {
      state.projects = data.items || [];
      renderSidebar();
      updateHeader();
    }).catch(function () {});
  }
  function loadHealth() {
    return api("/api/health").then(function (health) {
      var ai = health.ai || {};
      var db = health.database || {};
      var model = ai.configured ? ai.model : "";
      $("#mini-health").innerHTML =
        '<div class="health-line"><span class="health-dot ' + (db.ok ? "" : "bad") + '"></span>DB ' + (db.ok ? "online" : "offline") + '</div>' +
        '<div class="health-line"><span class="health-dot ' + (ai.configured ? "" : "warn") + '"></span>' + esc(model || "AI not configured") + '</div>';
    }).catch(function () {});
  }
  function refresh() {
    if (state.view === "settings") { renderSettings(); return; }
    return Promise.all([loadProjects(), loadTasks()]).then(loadHealth);
  }

  /* Detail */
  function openDetail(id) {
    api("/api/tasks/" + id).then(function (data) {
      state.selected = data.task;
      renderDrawer();
      $("#drawer").hidden = false;
      $("#drawer-backdrop").hidden = false;
    }).catch(function (err) { toast(err.message, true); });
  }
  function closeDetail() {
    $("#drawer").hidden = true;
    $("#drawer-backdrop").hidden = true;
    state.selected = null;
  }
  function toLocalInput(value) {
    if (!value) return "";
    var d = new Date(value);
    if (isNaN(d)) return "";
    var pad = function (n) { return String(n).padStart(2, "0"); };
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
  }
  function renderDrawer() {
    var t = state.selected;
    if (!t) return;
    $("#detail-eyebrow").textContent = statusLabel(t.status);
    $("#detail-title").textContent = t.title;
    var projects = state.projects.map(function (p) {
      return '<option value="' + esc(p.id) + '"' + (p.id === t.project_id ? " selected" : "") + ">" + esc(p.name) + "</option>";
    }).join("");
    var tags = (t.tags || []).map(function (tag) { return '<span class="tag">' + esc(tag.name) + "</span>"; }).join("");
    var subtasks = (t.subtasks || []).map(function (s) {
      return '<div class="subtask"><input type="checkbox" disabled' + (s.status === "done" ? " checked" : "") + "><span>" + esc(s.title) + "</span></div>";
    }).join("") || '<div class="muted">No subtasks yet.</div>';
    var events = (t.events || []).slice(0, 8).map(function (e) {
      return '<div class="event"><strong>' + esc(e.event_type) + '</strong> · ' + esc(e.actor) + ' · ' + esc((e.created_at || "").slice(0, 16).replace("T", " ")) + "</div>";
    }).join("");
    $("#drawer-body").innerHTML =
      '<div class="detail-tags">' + tags + '<span class="tag">' + esc(statusLabel(t.status)) + '</span><span class="tag">' + esc(t.project_name || "No project") + "</span></div>" +
      '<div class="form-grid">' +
      '<label>Title<input id="d-title" type="text" value="' + esc(t.title) + '"></label>' +
      '<label>Notes<textarea id="d-notes">' + esc(t.description || "") + "</textarea></label>" +
      '<label>Status<select id="d-status">' + ["backlog", "research", "sprint", "review", "done", "cancelled"].map(function (s) { return '<option value="' + s + '"' + (s === t.status ? " selected" : "") + ">" + statusLabel(s) + "</option>"; }).join("") + "</select></label>" +
      '<label>Priority<select id="d-priority">' + ["none", "low", "medium", "high", "urgent"].map(function (p) { return '<option value="' + p + '"' + (p === t.priority ? " selected" : "") + ">" + priorityLabel(p) + "</option>"; }).join("") + "</select></label>" +
      '<label>Project<select id="d-project"><option value="">No project</option>' + projects + "</select></label>" +
      '<label>Due<input id="d-due" type="datetime-local" value="' + esc(toLocalInput(t.due_at)) + '"></label>' +
      '<label>Estimate (minutes)<input id="d-estimate" type="number" min="0" max="1440" value="' + (t.estimate_minutes == null ? "" : t.estimate_minutes) + '"></label>' +
      '<label>Tags (comma separated)<input id="d-tags" type="text" value="' + esc((t.tags || []).map(function (x) { return x.name; }).join(", ")) + '"></label>' +
      "</div>" +
      '<div class="detail-actions"><button class="btn btn-primary" id="d-save">Save</button>' +
      '<button class="btn btn-quiet" id="d-toggle">' + (t.status === "done" ? "Reopen" : "Complete") + "</button>" +
      '<button class="btn btn-danger-quiet" id="d-archive">Archive</button></div>' +
      '<p class="eyebrow">Checklist</p><div class="subtasks">' + subtasks + "</div>" +
      '<p class="eyebrow" style="margin-top:18px">Activity</p><div class="events">' + (events || '<div class="muted">No activity yet.</div>') + "</div>";
    $("#d-save").addEventListener("click", saveDetail);
    $("#d-toggle").addEventListener("click", toggleDetail);
    $("#d-archive").addEventListener("click", archiveDetail);
  }
  function saveDetail() {
    var t = state.selected;
    if (!t) return;
    var due = $("#d-due").value;
    var payload = {
      title: $("#d-title").value,
      description: $("#d-notes").value,
      status: $("#d-status").value,
      priority: $("#d-priority").value,
      project_id: $("#d-project").value || null,
      due_at: due ? new Date(due).toISOString() : null,
      due_all_day: false,
      estimate_minutes: $("#d-estimate").value === "" ? null : Number($("#d-estimate").value),
      tags: $("#d-tags").value.split(",").map(function (x) { return x.trim(); }).filter(Boolean),
      version: t.version
    };
    api("/api/tasks/" + t.id, { method: "PATCH", body: payload }).then(function () {
      toast("Task saved");
      closeDetail();
      refresh();
    }).catch(function (err) { toast(err.message, true); });
  }
  function toggleDetail() {
    var t = state.selected;
    if (!t) return;
    var done = t.status === "done";
    api("/api/tasks/" + t.id + (done ? "/reopen" : "/complete"), { method: "POST" }).then(function () {
      toast(done ? "Task reopened" : "Task completed");
      closeDetail();
      refresh();
    }).catch(function (err) { toast(err.message, true); });
  }
  function archiveDetail() {
    var t = state.selected;
    if (!t) return;
    if (!window.confirm("Archive this task?")) return;
    api("/api/tasks/" + t.id, { method: "DELETE" }).then(function () {
      toast("Task archived");
      closeDetail();
      refresh();
    }).catch(function (err) { toast(err.message, true); });
  }

  /* Quick add */
  function openQuickAdd() {
    var overlay = $("#quick-add-layer");
    if (overlay) { overlay.hidden = false; return; }
    var optionHtml = state.projects.map(function (p) {
      return '<option value="' + esc(p.id) + '"' + (p.id === state.projectId ? " selected" : "") + ">" + esc(p.name) + "</option>";
    }).join("");
    var el = document.createElement("div");
    el.id = "quick-add-layer";
    el.className = "modal-backdrop quick-add-layer";
    el.innerHTML = '<div class="modal"><div class="modal-head"><div><p class="eyebrow">Capture</p><h2>New task</h2></div><button class="icon-btn" id="qa-close" aria-label="Close"><svg class="icon"><use href="#i-x"></use></svg></button></div><div class="form-grid"><label>Title<input id="qa-title" type="text" placeholder="What needs doing?"></label><label>Project<select id="qa-project"><option value="">No project</option>' + optionHtml + '</select></label><label>Priority<select id="qa-priority"><option value="none">No priority</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option><option value="urgent">Urgent</option></select></label><label>Due<input id="qa-due" type="datetime-local"></label></div><div class="modal-actions"><button class="btn btn-primary" id="qa-save">Add task</button><button class="btn btn-quiet" id="qa-cancel">Cancel</button></div></div>';
    document.body.appendChild(el);
    function close() { el.remove(); }
    $("#qa-close").addEventListener("click", close);
    $("#qa-cancel").addEventListener("click", close);
    $("#qa-save").addEventListener("click", function () {
      var title = $("#qa-title").value.trim();
      if (!title) { toast("A title is required", true); return; }
      var due = $("#qa-due").value;
      api("/api/tasks", { method: "POST", body: {
        title: title,
        project_id: $("#qa-project").value || null,
        priority: $("#qa-priority").value,
        due_at: due ? new Date(due).toISOString() : null,
        status: "todo"
      }}).then(function () { close(); toast("Task added"); refresh(); }).catch(function (err) { toast(err.message, true); });
    });
    $("#qa-title").focus();
  }

  /* Command + proposal */
  function setCommandStatus(text, isError) {
    var el = $("#command-status");
    if (!text) { el.hidden = true; el.innerHTML = ""; return; }
    el.hidden = false;
    el.className = "command-status" + (isError ? " is-error" : "");
    el.innerHTML = (isError ? "" : '<span class="dot"></span>') + esc(text);
  }
  function submitCommand(ev) {
    ev.preventDefault();
    var input = $("#command-input");
    var text = input.value.trim();
    if (!text) return;
    setCommandStatus("AI is reading the request…");
    api("/api/ai/interpret", { method: "POST", body: { text: text, view: state.view, project_id: state.projectId || null } }).then(function (data) {
      pollRun(data.run_id);
    }).catch(function (err) { setCommandStatus(err.message, true); });
  }
  function pollRun(runId) {
    clearTimeout(state.pollTimer);
    var started = Date.now();
    function tick() {
      api("/api/ai/runs/" + runId).then(function (run) {
        if (run.status === "queued" || run.status === "running") {
          if (Date.now() - started > 130000) { setCommandStatus("AI is taking too long. Try again.", true); return; }
          setCommandStatus("AI is thinking… " + Math.round((Date.now() - started) / 1000) + "s");
          state.pollTimer = setTimeout(tick, 1200);
          return;
        }
        if (run.status === "failed") {
          setCommandStatus(run.error_message || "AI could not interpret that.", true);
          return;
        }
        if (run.proposal) {
          setCommandStatus("Proposal ready — review below.");
          inputClear();
          showProposal(run.proposal);
        } else {
          setCommandStatus("No changes proposed.");
        }
      }).catch(function (err) { setCommandStatus(err.message, true); });
    }
    tick();
  }
  function inputClear() { $("#command-input").value = ""; }
  var PROPOSAL_LABELS = {
    status: "Status", priority: "Priority", due_at: "Due", due_all_day: "Due type",
    estimate_minutes: "Estimate", project_id: "Project", tags: "Tags",
    description: "Notes", parent_ref: "Parent"
  };
  function humanValue(key, value) {
    if (value === null || value === undefined || value === "") return "—";
    if (key === "due_at") {
      var d = new Date(value);
      return isNaN(d) ? String(value) : d.toLocaleString(undefined, { weekday: "short", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
    }
    if (key === "project_id") {
      var project = state.projects.filter(function (p) { return p.id === value; })[0];
      return project ? project.name : "Selected project";
    }
    if (key === "priority") return priorityLabel(value);
    if (key === "status") return statusLabel(value);
    if (key === "estimate_minutes") return value + " min";
    if (key === "tags") return Array.isArray(value) ? value.join(", ") : String(value);
    if (key === "description") return String(value).slice(0, 160);
    if (key === "due_all_day") return value ? "All day" : "Timed";
    if (key === "parent_ref") return "Subtask of a new task";
    return String(value);
  }
  function proposalFieldRows(fields) {
    return Object.keys(fields || {}).filter(function (key) {
      return PROPOSAL_LABELS[key] && fields[key] !== null && fields[key] !== "" && fields[key] !== undefined;
    }).map(function (key) {
      return '<div class="proposal-field"><dt>' + esc(PROPOSAL_LABELS[key]) + '</dt><dd>' + esc(humanValue(key, fields[key])) + '</dd></div>';
    }).join("");
  }
  function proposalChangeRows(changes) {
    return Object.keys(changes || {}).map(function (key) {
      var change = changes[key] || {};
      var label = PROPOSAL_LABELS[key] || key.replace(/_/g, " ");
      return '<div class="proposal-field"><dt>' + esc(label) + '</dt><dd><s>' + esc(humanValue(key, change.from)) +
        '</s> <span class="arrow">→</span> ' + esc(humanValue(key, change.to)) + '</dd></div>';
    }).join("");
  }
  function renderProposalItems(items) {
    if (!items.length) return '<div class="proposal-item"><strong>No write actions</strong><span>The assistant returned information only.</span></div>';
    return items.map(function (item) {
      if (item.op === "create") {
        var rows = proposalFieldRows(item.fields);
        return '<div class="proposal-item"><strong>Create · ' + esc(item.title || "") + '</strong>' +
          (rows ? '<div class="proposal-fields">' + rows + '</div>' : "") + "</div>";
      }
      if (item.op === "update") {
        var changed = proposalChangeRows(item.changes);
        return '<div class="proposal-item"><strong>Update · ' + esc(item.title || "") + '</strong>' +
          (changed ? '<div class="proposal-fields">' + changed + '</div>' : "") + "</div>";
      }
      if (item.op === "archive") return '<div class="proposal-item"><strong>Archive · ' + esc(item.title || "") + '</strong><span>Task will be archived, not deleted.</span></div>';
      if (item.op === "complete") return '<div class="proposal-item"><strong>Complete · ' + esc(item.title || "") + "</strong></div>";
      if (item.op === "query") {
        var tasks = item.tasks || [];
        return '<div class="proposal-item"><strong>Query · ' + tasks.length + ' tasks</strong>' +
          tasks.slice(0, 5).map(function (t) { return "<span>" + esc(t.title || "") + "</span>"; }).join("") +
          (tasks.length > 5 ? '<span>…and ' + (tasks.length - 5) + ' more</span>' : "") + "</div>";
      }
      return '<div class="proposal-item"><strong>' + esc((item.op || "change").replace(/_/g, " ")) + "</strong></div>";
    }).join("");
  }
  function showProposal(proposal) {
    state.proposal = proposal;
    $("#proposal-title").textContent = proposal.intent ? proposal.intent.replace(/_/g, " ") : "Review changes";
    $("#proposal-message").textContent = proposal.assistant_message || "Review the proposed changes.";
    $("#proposal-list").innerHTML = renderProposalItems(proposal.preview || []);
    $("#proposal-backdrop").hidden = false;
    $("#proposal-modal").hidden = false;
  }
  function closeProposal() {
    $("#proposal-backdrop").hidden = true;
    $("#proposal-modal").hidden = true;
    state.proposal = null;
  }
  function applyProposal() {
    if (!state.proposal) return;
    api("/api/ai/proposals/" + state.proposal.id + "/apply", { method: "POST" }).then(function () {
      closeProposal();
      setCommandStatus("");
      toast("Changes applied");
      refresh();
    }).catch(function (err) { toast(err.message, true); });
  }
  function rejectProposal() {
    if (!state.proposal) return;
    api("/api/ai/proposals/" + state.proposal.id + "/reject", { method: "POST" }).then(function () {
      closeProposal();
      setCommandStatus("");
      toast("Proposal discarded");
    }).catch(function (err) { toast(err.message, true); });
  }

  /* Settings */
  function renderSettings() {
    $("#workspace").hidden = true;
    $("#command-area").hidden = true;
    $("#settings-panel").hidden = false;
    api("/api/auth/devices").then(renderDevices).catch(function () {});
  }
  function renderDevices(data) {
    var items = data.items || [];
    $("#device-list").innerHTML = items.length ? items.map(function (d) {
      return '<div class="device-row"><div><strong>' + esc(d.name) + "</strong><br><span>" + esc((d.last_seen_at || d.created_at || "").slice(0, 16).replace("T", " ")) + "</span></div>" + (d.revoked_at ? '<span style="color:var(--danger)">revoked</span>' : '<button class="btn btn-small btn-quiet" data-revoke="' + esc(d.id) + '">Revoke</button>') + "</div>";
    }).join("") : '<div class="muted">No signed-in devices.</div>';
    $$("[data-revoke]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        api("/api/auth/devices/" + btn.getAttribute("data-revoke") + "/revoke", { method: "POST" }).then(function () {
          toast("Device revoked"); api("/api/auth/devices").then(renderDevices);
        }).catch(function (err) { toast(err.message, true); });
      });
    });
  }
  function backup() {
    api("/api/maintenance/backup", { method: "POST" }).then(function (data) {
      $("#maintenance-note").textContent = "Backup written to " + data.path + " (" + Math.round(data.bytes / 1024) + " KB)";
      toast("Backup complete");
    }).catch(function (err) { toast(err.message, true); });
  }
  function rebuild() {
    api("/api/maintenance/rebuild-search", { method: "POST" }).then(function () {
      $("#maintenance-note").textContent = "FTS5 search index rebuilt.";
      toast("Search index rebuilt");
    }).catch(function (err) { toast(err.message, true); });
  }

  /* Events */
  function moveTaskToPhase(taskId, phase) {
    if (!taskId || !phase) return;
    var task = state.tasks.filter(function (t) { return t.id === taskId; })[0];
    if (!task || task.status === phase) return;
    api("/api/tasks/" + taskId, { method: "PATCH", body: { status: phase, version: task.version } })
      .then(function () { toast("Moved to " + statusLabel(phase) + "."); refresh(); })
      .catch(function (err) { toast(err.message, true); refresh(); });
  }

  var boardEl = $("#task-container");
  boardEl.addEventListener("dragstart", function (ev) {
    var card = ev.target.closest(".board-card");
    if (!card) return;
    state.dragTaskId = card.getAttribute("data-task-id");
    state.suppressClick = true;
    card.classList.add("is-dragging");
    ev.dataTransfer.effectAllowed = "move";
    ev.dataTransfer.setData("text/plain", state.dragTaskId);
  });
  boardEl.addEventListener("dragend", function () {
    state.dragTaskId = null;
    $$(".board-card.is-dragging").forEach(function (el) { el.classList.remove("is-dragging"); });
    $$(".board-col.is-drop-target").forEach(function (el) { el.classList.remove("is-drop-target"); });
    setTimeout(function () { state.suppressClick = false; }, 60);
  });
  boardEl.addEventListener("dragover", function (ev) {
    var col = ev.target.closest(".board-col");
    if (!col || !state.dragTaskId) return;
    ev.preventDefault();
    ev.dataTransfer.dropEffect = "move";
    $$(".board-col.is-drop-target").forEach(function (el) { if (el !== col) el.classList.remove("is-drop-target"); });
    col.classList.add("is-drop-target");
  });
  boardEl.addEventListener("dragleave", function (ev) {
    var col = ev.target.closest(".board-col");
    if (!col) return;
    if (!col.contains(ev.relatedTarget)) col.classList.remove("is-drop-target");
  });
  boardEl.addEventListener("drop", function (ev) {
    var col = ev.target.closest(".board-col");
    if (!col) return;
    ev.preventDefault();
    var id = state.dragTaskId || ev.dataTransfer.getData("text/plain");
    var phase = col.getAttribute("data-phase");
    col.classList.remove("is-drop-target");
    moveTaskToPhase(id, phase);
  });

  document.addEventListener("click", function (ev) {
    var toggle = ev.target.closest("[data-action='toggle']");
    if (toggle) {
      ev.stopPropagation();
      var id = toggle.getAttribute("data-task-id");
      var task = state.tasks.filter(function (t) { return t.id === id; })[0];
      var done = task && task.status === "done";
      api("/api/tasks/" + id + (done ? "/reopen" : "/complete"), { method: "POST" }).then(function () { refresh(); }).catch(function (err) { toast(err.message, true); });
      return;
    }
    if (state.suppressClick) { state.suppressClick = false; return; }
    var row = ev.target.closest(".task-row, .board-card");
    if (row && row.getAttribute("data-task-id")) { openDetail(row.getAttribute("data-task-id")); return; }
    if (ev.target.closest("#inline-add") || ev.target.closest("#quick-add-btn") || ev.target.closest("#add-btn")) { openQuickAdd(); }
  });
  $("#drawer-close").addEventListener("click", closeDetail);
  $("#drawer-backdrop").addEventListener("click", closeDetail);
  $("#proposal-close").addEventListener("click", closeProposal);
  $("#proposal-backdrop").addEventListener("click", closeProposal);
  $("#proposal-apply").addEventListener("click", applyProposal);
  $("#proposal-reject").addEventListener("click", rejectProposal);
  $("#command-form").addEventListener("submit", submitCommand);
  $("#focus-command").addEventListener("click", function () { $("#command-input").focus(); });
  $("#quick-add-btn").addEventListener("click", openQuickAdd);
  $("#backup-btn").addEventListener("click", backup);
  $("#rebuild-btn").addEventListener("click", rebuild);
  function openProjectDialog() {
    var layer = document.createElement("div");
    layer.id = "project-dialog-layer";
    layer.className = "modal-backdrop quick-add-layer";
    layer.innerHTML =
      '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="pd-title">' +
        '<div class="modal-head"><div><p class="eyebrow">Projects</p><h2 id="pd-title">New project</h2></div>' +
        '<button class="icon-btn" id="pd-close" aria-label="Close"><svg class="icon"><use href="#i-x"></use></svg></button></div>' +
        '<div class="form-grid">' +
          '<label>Name<input id="pd-name" type="text" placeholder="e.g. Website relaunch" maxlength="200" autocomplete="off"></label>' +
          '<label>Color<input id="pd-color" type="color" value="#64748b"></label>' +
        '</div>' +
        '<p class="form-error" id="pd-error" hidden></p>' +
        '<div class="modal-actions"><button class="btn btn-quiet" id="pd-cancel">Cancel</button><button class="btn btn-primary" id="pd-create">Create project</button></div>' +
      '</div>';
    document.body.appendChild(layer);
    var nameInput = $("#pd-name");
    var errorEl = $("#pd-error");
    function close() { layer.remove(); }
    function showError(message) { errorEl.hidden = false; errorEl.textContent = message; }
    function submit() {
      var name = nameInput.value.trim();
      if (!name) { showError("Give the project a name."); nameInput.focus(); return; }
      api("/api/projects", { method: "POST", body: { name: name, color: $("#pd-color").value } })
        .then(function () { close(); toast("Project created"); refresh(); })
        .catch(function (err) { showError(err.message); });
    }
    $("#pd-close").addEventListener("click", close);
    $("#pd-cancel").addEventListener("click", close);
    $("#pd-create").addEventListener("click", submit);
    nameInput.addEventListener("keydown", function (ev) {
      if (ev.key === "Enter") { ev.preventDefault(); submit(); }
    });
    layer.addEventListener("mousedown", function (ev) { if (ev.target === layer) close(); });
    nameInput.focus();
  }
  $("#new-project-btn").addEventListener("click", openProjectDialog);
  $$(".nav-item.is-active").forEach(function (el) { el.setAttribute("aria-current", "page"); });
  $("#theme-toggle").addEventListener("click", function () {
    var isDark = document.documentElement.classList.toggle("dark");
    try { localStorage.setItem("gtm-theme", isDark ? "dark" : "light"); } catch (e) {}
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) meta.setAttribute("content", isDark ? "dark" : "light");
  });
  function goToProject(id) {
    if (state.view === "projects") {
      window.location.href = id ? "/projects/" + encodeURIComponent(id) : "/all";
      return;
    }
    var url = window.location.pathname;
    if (id) url += "?project=" + encodeURIComponent(id);
    window.location.href = url;
  }
  var projectFilter = $("#project-filter");
  var projectFilterButton = $("#project-filter-btn");
  var projectMenu = $("#project-menu");
  function closeProjectMenu() {
    if (!projectMenu || !projectFilterButton) return;
    projectMenu.hidden = true;
    projectFilterButton.setAttribute("aria-expanded", "false");
  }
  if (projectFilter && projectFilterButton && projectMenu) {
    projectFilterButton.addEventListener("click", function (ev) {
      ev.stopPropagation();
      var opening = projectMenu.hidden;
      projectMenu.hidden = !opening;
      projectFilterButton.setAttribute("aria-expanded", opening ? "true" : "false");
      if (opening) {
        var target = projectMenu.querySelector(".project-option.is-selected") ||
          projectMenu.querySelector(".project-option");
        if (target) target.focus();
      }
    });
    projectMenu.addEventListener("click", function (ev) {
      var option = ev.target.closest(".project-option");
      if (!option) return;
      closeProjectMenu();
      goToProject(option.getAttribute("data-project") || "");
    });
    document.addEventListener("click", function (ev) {
      if (!projectFilter.contains(ev.target)) closeProjectMenu();
    });
    document.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape") {
        closeProjectMenu();
        projectFilterButton.focus();
      }
    });
  }
  $$("#view-tabs .tab-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      $$("#view-tabs .tab-btn").forEach(function (b) { b.classList.toggle("is-active", b === btn); });
      var view = btn.getAttribute("data-quick-view");
      if (view === "board") {
        // Projects keep their own board; global stage views jump to the full board.
        if (state.view !== "all" && state.view !== "projects") {
          var boardSuffix = state.projectId ? "?project=" + encodeURIComponent(state.projectId) : "";
          window.location.href = "/all" + boardSuffix;
          return;
        }
        state.mode = "board"; loadTasks();
      } else if (view === "list") {
        state.mode = "list"; loadTasks();
      }
    });
  });
  $("#search-input").addEventListener("keydown", function (ev) {
    if (ev.key !== "Enter") return;
    var q = ev.target.value.trim();
    if (!q) { refresh(); return; }
    state.mode = "list"; state.query = q;
    var searchPath = "/api/search?q=" + encodeURIComponent(q);
    if (state.projectId) searchPath += "&project_id=" + encodeURIComponent(state.projectId);
    api(searchPath).then(function (data) {
      state.tasks = data.items || [];
      renderTasks();
      $("#task-panel-title").textContent = 'Search · "' + q + '"';
    }).catch(function (err) { toast(err.message, true); });
  });
  document.addEventListener("keydown", function (ev) {
    var tag = (ev.target.tagName || "").toLowerCase();
    if (tag === "input" || tag === "textarea" || tag === "select") {
      if (ev.key === "Escape") { ev.target.blur(); }
      return;
    }
    if (ev.key === "c") { ev.preventDefault(); openQuickAdd(); }
    if (ev.key === "/") { ev.preventDefault(); $("#search-input").focus(); }
    if ((ev.metaKey || ev.ctrlKey) && ev.key.toLowerCase() === "k") { ev.preventDefault(); $("#command-input").focus(); }
    if (ev.key === "Escape") {
      closeDetail();
      closeProposal();
      var projectDialog = $("#project-dialog-layer");
      if (projectDialog) projectDialog.remove();
    }
  });

  updateHeader();
  refresh();
}());
