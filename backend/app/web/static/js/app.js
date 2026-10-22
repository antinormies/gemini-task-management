(function () {
  "use strict";
  var state = {
    view: document.body.getAttribute("data-view") || "today",
    projectId: document.body.getAttribute("data-project-id") || "",
    tasks: [],
    projects: [],
    counts: {},
    selected: null,
    proposal: null,
    pollTimer: null,
    mode: "list",
    query: ""
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
    el.style.background = isError ? "#b3261e" : "#1d1d1f";
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
    return ({ inbox: "Inbox", todo: "To do", in_progress: "In progress", waiting: "Waiting", done: "Done", cancelled: "Cancelled" })[s] || s;
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
      '<div class="task-side"><button class="row-action" data-action="open" data-task-id="' + esc(task.id) + '" aria-label="Open task">›</button></div>' +
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
      container.innerHTML = '<div class="empty-state"><strong>All clear.</strong>Capture a thought or ask Gemini to add something.</div>';
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
    html += '<button class="add-row" id="inline-add">+ Add a task</button>';
    container.innerHTML = html;
  }
  function renderBoard() {
    var cols = [
      { key: "todo", label: "To do" },
      { key: "in_progress", label: "In progress" },
      { key: "waiting", label: "Waiting" },
      { key: "done", label: "Done" }
    ];
    var html = '<div class="board">';
    cols.forEach(function (col) {
      var items = state.tasks.filter(function (t) {
        return col.key === "todo" ? (t.status === "todo" || t.status === "inbox") : t.status === col.key;
      });
      html += '<div class="board-col"><div class="board-col-head"><span>' + col.label + "</span><span>" + items.length + "</span></div>";
      if (!items.length) html += '<div class="board-empty">No cards yet</div>';
      items.forEach(function (t) {
        html += '<button class="board-card" data-task-id="' + esc(t.id) + '"><div class="task-title">' + esc(t.title) + '</div><div class="task-meta"><span class="' + priorityClass(t.priority) + '">' + priorityLabel(t.priority) + '</span></div></button>';
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
    var titles = { today: "My day", inbox: "Inbox", upcoming: "Upcoming", anytime: "Anytime", waiting: "Waiting", done: "Done", settings: "Settings & system", projects: "Project" };
    var subs = {
      today: "Overdue and due today, plus anything scheduled.",
      inbox: "Untriaged capture. Move it when it has a home.",
      upcoming: "The next seven days.",
      anytime: "Open work with no date.",
      waiting: "Blocked on someone or something.",
      done: "Completed in the last 30 days.",
      settings: "Local engine, paired devices, and maintenance.",
      projects: "Active work for this project."
    };
    var project = state.projects.filter(function (p) { return p.id === state.projectId; })[0];
    $("#view-title").textContent = project ? project.name : (titles[state.view] || "Tasks");
    $("#view-subtitle").textContent = subs[state.view] || "";
    $("#eyebrow").textContent = state.view === "settings" ? "System" : (project ? "Project" : "Today");
    $("#context-date").textContent = new Date().toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" });
  }
  function loadTasks() {
    var params = new URLSearchParams();
    params.set("view", state.mode === "board" ? "board" : (state.view === "settings" ? "today" : state.view));
    if (state.view === "projects" && state.projectId) params.set("project_id", state.projectId);
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
  function refresh() {
    if (state.view === "settings") { renderSettings(); return; }
    return Promise.all([loadProjects(), loadTasks()]);
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
    $("#detail-eyebrow").textContent = t.short_key + " · " + statusLabel(t.status);
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
      '<label>Status<select id="d-status">' + ["inbox", "todo", "in_progress", "waiting", "done", "cancelled"].map(function (s) { return '<option value="' + s + '"' + (s === t.status ? " selected" : "") + ">" + statusLabel(s) + "</option>"; }).join("") + "</select></label>" +
      '<label>Priority<select id="d-priority">' + ["none", "low", "medium", "high", "urgent"].map(function (p) { return '<option value="' + p + '"' + (p === t.priority ? " selected" : "") + ">" + priorityLabel(p) + "</option>"; }).join("") + "</select></label>" +
      '<label>Project<select id="d-project"><option value="">No project</option>' + projects + "</select></label>" +
      '<label>Due<input id="d-due" type="datetime-local" value="' + esc(toLocalInput(t.due_at)) + '"></label>' +
      '<label>Estimate (minutes)<input id="d-estimate" type="number" min="0" max="1440" value="' + (t.estimate_minutes == null ? "" : t.estimate_minutes) + '"></label>' +
      '<label>Tags (comma separated)<input id="d-tags" type="text" value="' + esc((t.tags || []).map(function (x) { return x.name; }).join(", ")) + '"></label>' +
      "</div>" +
      '<div class="detail-actions"><button class="btn btn-primary" id="d-save">Save</button>' +
      '<button class="btn btn-quiet" id="d-toggle">' + (t.status === "done" ? "Reopen" : "Complete") + "</button>" +
      '<button class="btn btn-danger" id="d-archive">Archive</button></div>' +
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
    var optionHtml = state.projects.map(function (p) { return '<option value="' + esc(p.id) + '">' + esc(p.name) + "</option>"; }).join("");
    var el = document.createElement("div");
    el.id = "quick-add-layer";
    el.className = "modal-backdrop";
    el.style.display = "grid";
    el.style.placeItems = "center";
    el.innerHTML = '<div class="modal" style="position:relative;top:auto;left:auto;transform:none;width:min(440px,92vw);"><div class="modal-head"><div><p class="eyebrow">Capture</p><h2>New task</h2></div><button class="icon-btn" id="qa-close">✕</button></div><div class="form-grid"><label>Title<input id="qa-title" type="text" placeholder="What needs doing?"></label><label>Project<select id="qa-project"><option value="">No project</option>' + optionHtml + '</select></label><label>Priority<select id="qa-priority"><option value="none">No priority</option><option value="low">Low</option><option value="medium">Medium</option><option value="high">High</option><option value="urgent">Urgent</option></select></label><label>Due<input id="qa-due" type="datetime-local"></label></div><div class="modal-actions" style="margin-top:14px"><button class="btn btn-primary" id="qa-save">Add task</button><button class="btn btn-quiet" id="qa-cancel">Cancel</button></div></div>';
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
    setCommandStatus("Gemini is reading the request…");
    api("/api/ai/interpret", { method: "POST", body: { text: text, view: state.view } }).then(function (data) {
      pollRun(data.run_id);
    }).catch(function (err) { setCommandStatus(err.message, true); });
  }
  function pollRun(runId) {
    clearTimeout(state.pollTimer);
    var started = Date.now();
    function tick() {
      api("/api/ai/runs/" + runId).then(function (run) {
        if (run.status === "queued" || run.status === "running") {
          if (Date.now() - started > 130000) { setCommandStatus("Gemini is taking too long. Try again.", true); return; }
          setCommandStatus("Gemini is thinking… " + Math.round((Date.now() - started) / 1000) + "s");
          state.pollTimer = setTimeout(tick, 1200);
          return;
        }
        if (run.status === "failed") {
          setCommandStatus(run.error_message || "Gemini could not interpret that.", true);
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
  function showProposal(proposal) {
    state.proposal = proposal;
    $("#proposal-title").textContent = proposal.intent ? proposal.intent.replace(/_/g, " ") : "Review changes";
    $("#proposal-message").textContent = proposal.assistant_message || "Review the proposed changes.";
    var items = proposal.preview || [];
    $("#proposal-list").innerHTML = items.map(function (item) {
      if (item.op === "create") return '<div class="proposal-item"><strong>Create · ' + esc(item.title || "") + '</strong><span>' + esc(JSON.stringify(item.fields || {})) + "</span></div>";
      if (item.op === "update") return '<div class="proposal-item"><strong>Update · ' + esc(item.title || "") + '</strong><span>' + esc(JSON.stringify(item.changes || {})) + "</span></div>";
      if (item.op === "archive") return '<div class="proposal-item"><strong>Archive · ' + esc(item.title || "") + '</strong><span>Task will be archived, not deleted.</span></div>';
      if (item.op === "complete") return '<div class="proposal-item"><strong>Complete · ' + esc(item.title || "") + "</strong></div>";
      return '<div class="proposal-item"><strong>' + esc(item.op || "Change") + "</strong><span>" + esc(JSON.stringify(item)) + "</span></div>";
    }).join("") || '<div class="proposal-item"><strong>No write actions</strong><span>The assistant returned information only.</span></div>';
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
    api("/api/health").then(function (health) {
      var ai = health.ai || {};
      var lines = [
        ["App", health.version],
        ["Database", health.database.ok ? "OK" : "Error"],
        ["AI provider", ai.provider],
        ["agy", ai.agy_installed ? ai.agy_model : "not found"],
        ["OpenCode", ai.opencode_configured ? ai.opencode_model : "not configured"],
        ["Gemini", ai.gemini_configured ? "configured" : "not configured"],
        ["Voice", health.voice.enabled ? health.voice.provider + " · " + health.voice.input : "disabled"],
        ["Pairing", health.pairing.enabled ? "enabled" : "disabled"]
      ];
      $("#health-details").innerHTML = lines.map(function (row) {
        return '<div class="kv"><span>' + esc(row[0]) + "</span><strong>" + esc(row[1]) + "</strong></div>";
      }).join("");
      $("#mini-health").innerHTML = '<div class="health-line"><span class="health-dot ' + (health.database.ok ? "" : "bad") + '"></span>DB ' + (health.database.ok ? "online" : "offline") + '</div><div class="health-line"><span class="health-dot ' + (ai.agy_installed || ai.opencode_configured ? "" : "warn") + '"></span>' + esc(ai.opencode_configured ? ai.opencode_model : ai.agy_model) + "</div>";
    }).catch(function () {});
    api("/api/auth/devices").then(renderDevices).catch(function () {});
  }
  function renderDevices(data) {
    var items = data.items || [];
    $("#device-list").innerHTML = items.length ? items.map(function (d) {
      return '<div class="device-row"><div><strong>' + esc(d.name) + "</strong><br><span>" + esc((d.last_seen_at || d.created_at || "").slice(0, 16).replace("T", " ")) + "</span></div>" + (d.revoked_at ? '<span style="color:var(--danger)">revoked</span>' : '<button class="btn btn-small btn-quiet" data-revoke="' + esc(d.id) + '">Revoke</button>') + "</div>";
    }).join("") : '<div class="muted">No paired devices.</div>';
    $$("[data-revoke]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        api("/api/auth/devices/" + btn.getAttribute("data-revoke") + "/revoke", { method: "POST" }).then(function () {
          toast("Device revoked"); api("/api/auth/devices").then(renderDevices);
        }).catch(function (err) { toast(err.message, true); });
      });
    });
  }
  function generateCode() {
    api("/api/auth/pairing-code", { method: "POST" }).then(function (data) {
      var el = $("#pairing-code");
      el.hidden = false;
      el.textContent = data.code;
      toast("Pairing code valid for 5 minutes");
    }).catch(function (err) { toast(err.message, true); });
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
    var row = ev.target.closest(".task-row, .board-card");
    if (row && row.getAttribute("data-task-id")) { openDetail(row.getAttribute("data-task-id")); return; }
    if (ev.target.id === "inline-add" || ev.target.id === "quick-add-btn" || ev.target.id === "add-btn") { openQuickAdd(); }
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
  $("#pairing-code-btn").addEventListener("click", generateCode);
  $("#backup-btn").addEventListener("click", backup);
  $("#rebuild-btn").addEventListener("click", rebuild);
  $("#new-project-btn").addEventListener("click", function () {
    var name = window.prompt("Project name");
    if (!name) return;
    api("/api/projects", { method: "POST", body: { name: name } }).then(function () { toast("Project created"); refresh(); }).catch(function (err) { toast(err.message, true); });
  });
  $$("#view-tabs .tab-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      $$("#view-tabs .tab-btn").forEach(function (b) { b.classList.toggle("is-active", b === btn); });
      var view = btn.getAttribute("data-quick-view");
      if (view === "board") { state.mode = "board"; loadTasks(); }
      else if (view === "all") { state.mode = "list"; window.location.href = "/anytime"; }
      else { state.mode = "list"; window.location.href = "/today"; }
    });
  });
  $("#search-input").addEventListener("keydown", function (ev) {
    if (ev.key !== "Enter") return;
    var q = ev.target.value.trim();
    if (!q) { refresh(); return; }
    state.mode = "list"; state.query = q;
    api("/api/search?q=" + encodeURIComponent(q)).then(function (data) {
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
    if (ev.key === "Escape") { closeDetail(); closeProposal(); }
  });

  updateHeader();
  refresh();
}());
