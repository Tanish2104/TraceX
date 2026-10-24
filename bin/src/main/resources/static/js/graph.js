(function () {
  var container = document.getElementById('network');
  var status = document.getElementById('status');

  fetch('/api/graph', { credentials: 'same-origin' })
    .then(function (response) {
      if (!response.ok) {
        throw new Error('HTTP ' + response.status);
      }
      return response.json();
    })
    .then(function (data) {
      if (!data.nodes.length) {
        status.textContent = 'The graph is empty. Upload data first.';
        return;
      }
      var nodes = new vis.DataSet(data.nodes.map(function (n) {
        return {
          id: n.id,
          label: n.label,
          group: n.type,
          value: Math.max(n.score, 1),
          title: n.type + ' | score ' + n.score.toFixed(1) + ' | group ' + n.community
        };
      }));
      var edges = new vis.DataSet(data.edges.map(function (e, i) {
        return { id: i, from: e.from, to: e.to, label: e.kind, value: e.weight, arrows: 'to' };
      }));
      var options = {
        groups: {
          PERSON: { color: '#c0392b' },
          PHONE: { color: '#2980b9' },
          VEHICLE: { color: '#27ae60' },
          ACCOUNT: { color: '#8e44ad' },
          LOCATION: { color: '#e67e22' }
        },
        nodes: { shape: 'dot', scaling: { min: 8, max: 40 }, font: { size: 13 } },
        edges: { font: { size: 9, align: 'middle' }, scaling: { min: 1, max: 6 }, smooth: false },
        physics: { stabilization: { iterations: 150 } },
        interaction: { hover: true }
      };
      var network = new vis.Network(container, { nodes: nodes, edges: edges }, options);
      network.on('doubleClick', function (params) {
        if (params.nodes.length) {
          window.location = '/analysis/entity?key=' + encodeURIComponent(params.nodes[0]);
        }
      });
      status.textContent = data.nodes.length + ' entities and ' + data.edges.length
        + ' links. Node size shows the score. Double-click an entity to open its details.';
    })
    .catch(function (err) {
      status.textContent = 'Could not load the graph: ' + err.message;
    });
})();
