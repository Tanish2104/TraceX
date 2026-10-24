/**
 * TraceX Intelligence Platform - Clean Spacious Graph Topology & Missing Link Discovery
 * Visualizes criminal network with interactive police link tracing, edge hover intelligence, and spacious uncluttered layout
 */
(function () {
  'use strict';

  var container = document.getElementById('network');
  var status = document.getElementById('status');
  var edgeTooltip = document.getElementById('edge-tooltip');
  var btnDetect = document.getElementById('btn-detect-missing');
  var btnFit = document.getElementById('btn-fit');
  var btnPhysics = document.getElementById('btn-physics');
  var btnToggleLabels = document.getElementById('btn-toggle-labels');
  var labelsState = document.getElementById('labels-state');
  var btnToggleLocations = document.getElementById('btn-toggle-locations');
  var locationsState = document.getElementById('locations-state');
  var btnFullscreen = document.getElementById('btn-fullscreen');
  var searchInput = document.getElementById('graph-search');
  var drawer = document.getElementById('missing-drawer');
  var btnCloseDrawer = document.getElementById('btn-close-drawer');
  var missingList = document.getElementById('missing-list');
  var missingCount = document.getElementById('missing-count');

  var network = null;
  var nodesDataSet = null;
  var edgesDataSet = null;
  var rawNodes = [];
  var rawEdges = [];
  var nodeMap = {};
  var physicsEnabled = true;
  var showEdgeLabels = false; // default: false (clean, spacious, zero text clutter!)
  var showLocations = true;

  fetch('/api/graph', { credentials: 'same-origin' })
    .then(function (response) {
      if (!response.ok) {
        throw new Error('HTTP ' + response.status);
      }
      return response.json();
    })
    .then(function (data) {
      if (!data.nodes || !data.nodes.length) {
        status.textContent = 'The graph is empty. Ingest available case data first.';
        return;
      }

      rawNodes = data.nodes;
      rawEdges = data.edges;

      nodeMap = {};
      rawNodes.forEach(function (n) {
        nodeMap[n.id] = n;
      });

      buildGraphView();

      status.textContent = data.nodes.length + ' entities & ' + data.edges.length
        + ' links indexed. Hover any line to view relationship intelligence; double-click a node to open dossier.';

      // Toolbar Controls
      if (btnFit) {
        btnFit.addEventListener('click', function () {
          if (network) network.fit({ animation: { duration: 600 } });
        });
      }

      if (btnPhysics) {
        btnPhysics.addEventListener('click', function () {
          physicsEnabled = !physicsEnabled;
          if (network) network.setOptions({ physics: { enabled: physicsEnabled } });
          btnPhysics.textContent = physicsEnabled ? 'Freeze Physics' : 'Resume Physics';
        });
      }

      if (btnToggleLabels) {
        btnToggleLabels.addEventListener('click', function () {
          showEdgeLabels = !showEdgeLabels;
          if (labelsState) {
            labelsState.textContent = showEdgeLabels ? 'Visible' : 'Hidden';
            labelsState.style.color = showEdgeLabels ? 'var(--ok)' : 'var(--muted)';
          }
          updateEdgeLabels();
        });
      }

      if (btnToggleLocations) {
        btnToggleLocations.addEventListener('click', function () {
          showLocations = !showLocations;
          if (locationsState) {
            locationsState.textContent = showLocations ? 'Visible' : 'Hidden';
            locationsState.style.color = showLocations ? 'var(--ok)' : 'var(--muted)';
          }
          filterLocationNodes();
        });
      }

      if (btnFullscreen) {
        btnFullscreen.addEventListener('click', function () {
          container.classList.toggle('fullscreen');
          setTimeout(function () {
            if (network) network.fit({ animation: { duration: 400 } });
          }, 150);
        });
      }

      if (searchInput) {
        searchInput.addEventListener('input', function (e) {
          var query = e.target.value.toLowerCase().trim();
          if (!query || !nodesDataSet) return;
          var found = nodesDataSet.get({
            filter: function (n) {
              return (n.label && n.label.toLowerCase().includes(query)) ||
                     (n.id && n.id.toLowerCase().includes(query));
            }
          });
          if (found.length && network) {
            network.focus(found[0].id, { scale: 1.4, animation: { duration: 500 } });
            network.selectNodes([found[0].id]);
          }
        });
      }

      if (btnDetect) {
        btnDetect.addEventListener('click', function () {
          fetch('/api/missing-links', { credentials: 'same-origin' })
            .then(function (r) { return r.json(); })
            .then(function (links) {
              if (missingCount) missingCount.textContent = links.length;
              if (drawer) drawer.style.display = 'flex';
              renderMissingLinks(links);
            })
            .catch(function (err) {
              alert('Could not detect missing links: ' + err.message);
            });
        });
      }

      if (btnCloseDrawer) {
        btnCloseDrawer.addEventListener('click', function () {
          if (drawer) drawer.style.display = 'none';
        });
      }
    })
    .catch(function (err) {
      status.textContent = 'Could not load graph topology: ' + err.message;
    });

  function buildGraphView() {
    var nodesList = rawNodes.map(function (n) {
      var isLocation = n.type === 'LOCATION';
      var isPerson = n.type === 'PERSON';

      if (isLocation) {
        // Location nodes: small, subtle, clean satellite markers (NEVER oversized!)
        return {
          id: n.id,
          label: n.label,
          group: n.type,
          shape: 'dot',
          size: 10,
          font: {
            size: 10,
            color: '#f472b6',
            face: 'Plus Jakarta Sans',
            strokeWidth: 2,
            strokeColor: '#090e1a',
            vadjust: -2
          },
          color: {
            background: '#db2777',
            border: '#f472b6',
            highlight: { background: '#f472b6', border: '#fbcfe8' }
          },
          borderWidth: 1.5,
          title: '📍 Location: ' + n.label
        };
      }

      if (isPerson) {
        // Suspect / Person nodes: primary investigative focus
        var scaledSize = Math.min(Math.max(14 + (n.score * 0.28), 16), 34);
        return {
          id: n.id,
          label: n.label,
          group: n.type,
          shape: 'dot',
          size: scaledSize,
          font: {
            size: 13,
            color: '#f8fafc',
            face: 'Plus Jakarta Sans',
            strokeWidth: 3,
            strokeColor: '#090e1a'
          },
          color: {
            background: '#2563eb',
            border: '#60a5fa',
            highlight: { background: '#3b82f6', border: '#93c5fd' }
          },
          borderWidth: 2,
          shadow: { enabled: true, color: 'rgba(0,0,0,0.5)', size: 8, x: 2, y: 2 },
          title: '👤 Suspect: ' + n.label +
                 (n.score > 0 ? '\nThreat Score: ' + n.score.toFixed(1) : '')
        };
      }

      // Other types: PHONE, VEHICLE, ACCOUNT
      var colors = {
        PHONE: { bg: '#059669', br: '#34d399', text: '#34d399' },
        VEHICLE: { bg: '#d97706', br: '#fbbf24', text: '#fbbf24' },
        ACCOUNT: { bg: '#7c3aed', br: '#a78bfa', text: '#a78bfa' }
      };
      var c = colors[n.type] || { bg: '#475569', br: '#94a3b8', text: '#cbd5e1' };

      return {
        id: n.id,
        label: n.label,
        group: n.type,
        shape: 'dot',
        size: 12,
        font: {
          size: 11,
          color: c.text,
          face: 'JetBrains Mono',
          strokeWidth: 2,
          strokeColor: '#090e1a'
        },
        color: {
          background: c.bg,
          border: c.br,
          highlight: { background: c.br, border: '#ffffff' }
        },
        borderWidth: 1.5,
        title: n.type + ': ' + n.label
      };
    });

    var edgesList = rawEdges.map(function (e, i) {
      var isLocationEdge = e.kind === 'LOCATED_AT';
      var formatted = formatKind(e.kind);

      return {
        id: 'e_' + i,
        from: e.from,
        to: e.to,
        rawKind: e.kind,
        weight: e.weight || 1,
        evidence: e.evidence || '',
        // If edge labels are toggled off OR if it is a location edge, keep line clean!
        label: (showEdgeLabels && !isLocationEdge) ? formatted : '',
        title: undefined, // Suppress default browser tooltip in favor of rich hover card
        arrows: {
          to: { enabled: true, scaleFactor: 0.65 }
        },
        color: isLocationEdge
          ? { color: 'rgba(219, 39, 119, 0.25)', highlight: '#f472b6' }
          : { color: 'rgba(96, 165, 250, 0.45)', highlight: '#93c5fd' },
        dashes: isLocationEdge ? [4, 4] : false,
        width: isLocationEdge ? 1 : Math.min(Math.max(e.weight || 1, 1.5), 3.5),
        hoverWidth: 1.8,
        selectionWidth: 2.2,
        font: {
          size: 9,
          face: 'JetBrains Mono',
          color: '#cbd5e1',
          align: 'middle',
          background: 'rgba(15, 23, 42, 0.85)',
          strokeWidth: 0
        },
        smooth: { type: 'continuous', roundness: 0.15 }
      };
    });

    nodesDataSet = new vis.DataSet(nodesList);
    edgesDataSet = new vis.DataSet(edgesList);

    var options = {
      nodes: {
        scaling: {
          label: { enabled: false } // NEVER scale node fonts up into giant text!
        }
      },
      edges: {
        scaling: {
          label: { enabled: false } // NEVER scale edge labels up into giant text!
        },
        hoverWidth: 1.8,
        selectionWidth: 2.2
      },
      physics: {
        enabled: true,
        solver: 'barnesHut',
        stabilization: {
          enabled: true,
          iterations: 180,
          updateInterval: 25
        },
        barnesHut: {
          gravitationalConstant: -18000, // Powerful repulsion spreads nodes out widely!
          centralGravity: 0.12,          // Gentle centering prevents floating away
          springLength: 260,             // Spacious 260px distance between connected nodes!
          springConstant: 0.032,         // Soft, natural spring
          damping: 0.09,
          avoidOverlap: 1                // Zero node collision!
        }
      },
      interaction: {
        hover: true,
        hoverConnectedEdges: true,
        tooltipDelay: 40,
        zoomView: true,
        dragView: true,
        navigationButtons: true,
        keyboard: true
      }
    };

    network = new vis.Network(container, { nodes: nodesDataSet, edges: edgesDataSet }, options);

    // Interactive Edge Hover Details
    network.on('hoverEdge', function (params) {
      var edgeId = params.edge;
      var edgeObj = edgesDataSet.get(edgeId);
      if (!edgeObj) return;

      var srcNode = nodeMap[edgeObj.from] || { label: edgeObj.from, type: 'UNKNOWN' };
      var dstNode = nodeMap[edgeObj.to] || { label: edgeObj.to, type: 'UNKNOWN' };

      showEdgeTooltip(edgeObj, srcNode, dstNode, params.pointer.DOM);
    });

    network.on('blurEdge', function () {
      hideEdgeTooltip();
    });

    network.on('dragStart', function () {
      hideEdgeTooltip();
    });

    network.on('zoom', function () {
      hideEdgeTooltip();
    });

    network.on('click', function () {
      hideEdgeTooltip();
    });

    network.on('doubleClick', function (params) {
      if (params.nodes.length) {
        window.location = '/analysis/entity?key=' + encodeURIComponent(params.nodes[0]);
      }
    });
  }

  function showEdgeTooltip(edge, src, dst, pos) {
    if (!edgeTooltip || !pos) return;

    edgeTooltip.innerHTML = buildEdgeTooltipHtml(edge, src, dst);
    edgeTooltip.style.display = 'block';

    var left = pos.x + 14;
    var top = pos.y + 14;

    var containerWidth = container.clientWidth || 800;
    var containerHeight = container.clientHeight || 600;

    var rect = edgeTooltip.getBoundingClientRect();
    var tipWidth = rect.width || 340;
    var tipHeight = rect.height || 100;

    if (left + tipWidth > containerWidth - 20) {
      left = Math.max(10, pos.x - tipWidth - 14);
    }
    if (top + tipHeight > containerHeight - 20) {
      top = Math.max(10, pos.y - tipHeight - 14);
    }

    edgeTooltip.style.left = left + 'px';
    edgeTooltip.style.top = top + 'px';
  }

  function hideEdgeTooltip() {
    if (edgeTooltip) {
      edgeTooltip.style.display = 'none';
    }
  }

  function buildEdgeTooltipHtml(edge, src, dst) {
    var srcLabel = escapeHtml(src.label || edge.from);
    var dstLabel = escapeHtml(dst.label || edge.to);
    var srcType = (src.type || '').toUpperCase();
    var dstType = (dst.type || '').toUpperCase();
    var kind = (edge.rawKind || '').toUpperCase();
    var weight = edge.weight || 1;
    var evidence = (edge.evidence || '').trim();

    var badgeText = 'LINK';
    var badgeBg = 'rgba(59, 130, 246, 0.2)';
    var badgeColor = '#60a5fa';
    var badgeBorder = 'rgba(59, 130, 246, 0.4)';
    var actionSentence = '';
    var metaDetail = '';

    if (kind === 'CALLED' || kind.includes('CALL')) {
      badgeText = '📞 CALL RECORD';
      badgeBg = 'rgba(16, 185, 129, 0.2)';
      badgeColor = '#34d399';
      badgeBorder = 'rgba(16, 185, 129, 0.4)';
      metaDetail = weight > 1 ? (Math.round(weight) + ' calls logged') : 'Direct voice call';

      if (srcType === 'PERSON' && dstType === 'PHONE') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> dialed / called mobile 📞 <strong>' + dstLabel + '</strong>';
      } else if (srcType === 'PERSON' && dstType === 'PERSON') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> called co-suspect 👤 <strong>' + dstLabel + '</strong>';
      } else if (srcType === 'PHONE' && dstType === 'PHONE') {
        actionSentence = '📞 <strong>' + srcLabel + '</strong> recorded outgoing CDR calls to 📞 <strong>' + dstLabel + '</strong>';
      } else {
        actionSentence = '📞 <strong>' + srcLabel + '</strong> in telephone communication with <strong>' + dstLabel + '</strong>';
      }
    } else if (kind === 'TRANSFERRED' || kind.includes('TRANSFER') || kind.includes('PAYMENT')) {
      badgeText = '💳 FUND TRANSFER';
      badgeBg = 'rgba(139, 92, 246, 0.2)';
      badgeColor = '#a78bfa';
      badgeBorder = 'rgba(139, 92, 246, 0.4)';

      var amountStr = weight > 1 ? ('₹' + Number(weight).toLocaleString('en-IN')) : '';
      metaDetail = amountStr ? ('Amount: ' + amountStr) : 'Banking transaction';

      if (srcType === 'PERSON' && dstType === 'ACCOUNT') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> transferred ' + (amountStr ? ('<strong>' + amountStr + '</strong> ') : '') + 'to Account 💳 <strong>' + dstLabel + '</strong>';
      } else if (srcType === 'ACCOUNT' && dstType === 'ACCOUNT') {
        actionSentence = '💳 Account <strong>' + srcLabel + '</strong> transferred ' + (amountStr ? ('<strong>' + amountStr + '</strong> ') : '') + 'to 💳 Account <strong>' + dstLabel + '</strong>';
      } else if (srcType === 'ACCOUNT' && dstType === 'PERSON') {
        actionSentence = '💳 Account <strong>' + srcLabel + '</strong> remitted ' + (amountStr ? ('<strong>' + amountStr + '</strong> ') : '') + 'to beneficiary 👤 <strong>' + dstLabel + '</strong>';
      } else {
        actionSentence = '💳 <strong>' + srcLabel + '</strong> transferred funds to <strong>' + dstLabel + '</strong>';
      }
    } else if (kind === 'OWNS' || kind.includes('OPERAT') || kind.includes('HOLD')) {
      badgeText = '🔑 ASSET HOLDER';
      badgeBg = 'rgba(245, 158, 11, 0.2)';
      badgeColor = '#fbbf24';
      badgeBorder = 'rgba(245, 158, 11, 0.4)';
      metaDetail = 'Verified ownership';

      if (dstType === 'PHONE') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> is the registered SIM subscriber of 📞 <strong>' + dstLabel + '</strong>';
      } else if (dstType === 'ACCOUNT') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> holds & operates bank account 💳 <strong>' + dstLabel + '</strong>';
      } else if (dstType === 'VEHICLE') {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> owns / operates vehicle 🚗 <strong>' + dstLabel + '</strong>';
      } else {
        actionSentence = '👤 <strong>' + srcLabel + '</strong> is the registered owner of <strong>' + dstLabel + '</strong>';
      }
    } else if (kind === 'LOCATED_AT' || kind.includes('LOCAT')) {
      badgeText = '📍 GEO LOCATION';
      badgeBg = 'rgba(236, 72, 153, 0.2)';
      badgeColor = '#f472b6';
      badgeBorder = 'rgba(236, 72, 153, 0.4)';
      metaDetail = 'Geographic footprint';

      actionSentence = '👤 <strong>' + srcLabel + '</strong> tracked / located in 📍 <strong>' + dstLabel + '</strong>';
    } else if (kind === 'MENTIONED_WITH' || kind === 'CO_ACCUSED' || kind.includes('MENTION')) {
      badgeText = '👥 CASE CO-ACCUSED';
      badgeBg = 'rgba(59, 130, 246, 0.2)';
      badgeColor = '#60a5fa';
      badgeBorder = 'rgba(59, 130, 246, 0.4)';
      metaDetail = 'Direct case co-occurrence';

      actionSentence = '👤 <strong>' + srcLabel + '</strong> directly linked & mentioned with co-accused 👤 <strong>' + dstLabel + '</strong> in case records';
    } else if (kind === 'HIDDEN_BRIDGE') {
      badgeText = '⚡ HIDDEN BRIDGE';
      badgeBg = 'rgba(245, 158, 11, 0.25)';
      badgeColor = '#fbbf24';
      badgeBorder = 'rgba(245, 158, 11, 0.5)';
      metaDetail = 'Algorithmic bridge';

      actionSentence = '⚡ Indirect connection between 👤 <strong>' + srcLabel + '</strong> and 👤 <strong>' + dstLabel + '</strong> detected via shared link';
    } else {
      badgeText = '🔗 ' + (kind ? kind.replace(/_/g, ' ') : 'CONNECTED');
      actionSentence = '🔗 <strong>' + srcLabel + '</strong> is connected to <strong>' + dstLabel + '</strong> via <em>' + formatKind(kind) + '</em>';
      metaDetail = 'Relationship weight: ' + weight;
    }

    var html = '<div class="tooltip-header">'
      + '<span class="tooltip-badge" style="background:' + badgeBg + '; color:' + badgeColor + '; border:1px solid ' + badgeBorder + ';">'
      + badgeText
      + '</span>'
      + '<span style="font-size:0.72rem; color:#94a3b8; font-family:\'JetBrains Mono\', monospace;">'
      + escapeHtml(metaDetail)
      + '</span>'
      + '</div>'
      + '<div class="tooltip-action">'
      + actionSentence
      + '</div>';

    if (evidence) {
      html += '<div class="tooltip-evidence">'
        + '<svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle; margin-right:4px; color:#60a5fa;"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>'
        + '<strong style="color:#cbd5e1;">Case Record:</strong> '
        + escapeHtml(evidence)
        + '</div>';
    }

    return html;
  }

  function formatKind(k) {
    if (!k) return '';
    var upper = k.toUpperCase();
    if (upper === 'MENTIONED_WITH' || upper === 'CO_ACCUSED') return 'Co-accused in FIR';
    if (upper === 'CALLED') return 'Phone Call';
    if (upper === 'TRANSFERRED' || upper === 'TRANSFERRED_TO' || upper === 'TRANSACTION') return 'Fund Transfer';
    if (upper === 'OWNS' || upper === 'REGISTERED_TO') return 'Ownership';
    if (upper === 'LOCATED_AT') return 'Geographic Location';
    if (upper === 'ASSOCIATED_WITH') return 'Direct Associate';
    return k.charAt(0).toUpperCase() + k.slice(1).toLowerCase().replace(/_/g, ' ');
  }

  function updateEdgeLabels() {
    if (!edgesDataSet) return;
    var updates = [];
    edgesDataSet.forEach(function (e) {
      var isLocationEdge = e.rawKind === 'LOCATED_AT';
      var newLabel = (showEdgeLabels && !isLocationEdge) ? formatKind(e.rawKind) : '';
      if (e.label !== newLabel) {
        updates.push({ id: e.id, label: newLabel });
      }
    });
    if (updates.length) {
      edgesDataSet.update(updates);
    }
  }

  function filterLocationNodes() {
    if (!nodesDataSet || !edgesDataSet) return;
    buildGraphView();
    if (!showLocations) {
      var locIds = [];
      nodesDataSet.forEach(function (n) {
        if (n.group === 'LOCATION') locIds.push(n.id);
      });
      nodesDataSet.remove(locIds);
    }
  }

  function renderMissingLinks(links) {
    if (!missingList) return;
    missingList.innerHTML = '';

    if (!links || links.length === 0) {
      missingList.innerHTML = '<p class="muted small" style="margin: 12px 0; text-align:center;">No indirect missing links detected across active case nodes.</p>';
      return;
    }

    links.forEach(function (m) {
      var card = document.createElement('div');
      card.style.background = 'var(--bg-surface)';
      card.style.border = '1px solid var(--line)';
      card.style.borderRadius = 'var(--radius-sm)';
      card.style.padding = '10px 12px';
      card.style.fontSize = '0.84rem';

      card.innerHTML =
        '<div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">' +
          '<strong style="color:var(--ink); font-size:0.88rem;">' + escapeHtml(m.personALabel) + ' ⚡ ' + escapeHtml(m.personBLabel) + '</strong>' +
          '<span class="badge" style="background:var(--warn-bg); color:var(--warn); font-size:0.68rem; padding:1px 6px;">HIDDEN BRIDGE</span>' +
        '</div>' +
        '<div style="color:var(--muted); font-size:0.78rem; margin-bottom:6px;">' +
          'Shared Intermediary: <strong style="color:var(--accent);">' + escapeHtml(m.sharedLabel) + '</strong> (' + escapeHtml(m.sharedType) + ')' +
        '</div>' +
        '<p style="margin:0 0 8px; color:var(--ink-secondary); font-size:0.78rem; line-height:1.35;">' + escapeHtml(m.rationale) + '</p>' +
        '<div style="display:flex; justify-content:flex-end; gap:6px;">' +
          '<button type="button" class="btn small primary btn-focus-bridge" style="padding:3px 9px; font-size:0.74rem;">Focus Bridge &rarr;</button>' +
        '</div>';

      var focusBtn = card.querySelector('.btn-focus-bridge');
      focusBtn.addEventListener('click', function () {
        focusMissingLinkTriad(m);
      });

      missingList.appendChild(card);
    });
  }

  function focusMissingLinkTriad(m) {
    if (!network || !nodesDataSet || !edgesDataSet) return;

    var triadIds = [m.personAKey, m.sharedKey, m.personBKey];
    network.setSelection({ nodes: triadIds }, { unselectAll: true, highlightEdges: true });
    network.fit({
      nodes: triadIds,
      animation: { duration: 700 }
    });

    var inferredEdgeId = 'missing_' + m.personAKey + '_' + m.personBKey;
    if (!edgesDataSet.get(inferredEdgeId)) {
      edgesDataSet.add({
        id: inferredEdgeId,
        from: m.personAKey,
        to: m.personBKey,
        rawKind: 'HIDDEN_BRIDGE',
        weight: 1,
        evidence: m.rationale,
        label: 'HIDDEN BRIDGE (via ' + m.sharedLabel + ')',
        dashes: [6, 4],
        width: 3,
        color: { color: '#f59e0b', highlight: '#fbbf24' },
        font: { color: '#fbbf24', size: 10, face: 'JetBrains Mono', background: 'rgba(15, 23, 42, 0.9)' }
      });
    }
  }

  function escapeHtml(text) {
    if (!text) return '';
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
})();
