/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */


import _ from 'underscore';
import vis from 'vis';

import Style from './Style';

/*
 * Main class for creating a graph of nodes and edges. See Style class for asthetic customisation.
 * Callbacks (for interactivity with the graph) must be registered before calling .render().
 * Graph is drawn *only* after calling .render().
 * Nodes and edges can be added at any time.
 */
export default class Visualiser {
  constructor() {
    this.nodes = new vis.DataSet([]);
    this.edges = new vis.DataSet([]);

    this.callbacks = {};
    this.style = new Style();

        // vis.js network, instantiated on render.
    this.network = {};

        // vis.js default config
    this.networkConfig = {
      autoResize: true,
      nodes: {
        font: {
          size: 15,
          face: 'Geogrotesque-Ultralight',
        },
        shadow: {
          enabled: true,
          size: 10,
          x: 2,
          y: 2,
        },
      },
      physics: {
        barnesHut: {
          springLength: 140,
        },
        minVelocity: 0.75,
      },
      edges: {
        hoverWidth: 2,
        selectionWidth: 2,
        arrowStrikethrough: false,
        arrows: {
          to: true,
        },
        smooth: {
          enabled: false,
          forceDirection: 'none',
        },
      },
      interaction: {
        hover: true,
        multiselect: true,
      },
      layout: {
        improvedLayout: false,
      },
    };

    // Additional properties to show in node label by type.
    this.displayProperties = {};
    this.alreadyFittedToWindow = false;

        // working on stopping nodes from moving
    this.lastFixTime = 0; // this is needed to stop a redraw loop due to the update of the vis dataset
    this.draggingNode = false;

    this.draggingRect = false;
    this.rectangleCleared = false;
  }

  setCallbackOnEvent(eventName, callback) {
    this.callbacks[eventName] = callback;
  }
    /**
     * Start visualisation and render graph.
     * This needs to be called only once, but all callbacks should be configured
     * prior.
     */
  render(container) {
    this.network = new vis.Network(
            container, {
              nodes: this.nodes,
              edges: this.edges,
            },
            this.networkConfig);

    for(const eventName in this.callbacks) {
      this.network.on(eventName, this.callbacks[eventName]);
    }

    this.network.on('stabilized', (params) => {
      if (this.draggingNode === false) {
        this.fixNodes();
      }
    });

    this.network.on('dragEnd', (params) => {
      this.draggingNode = false;
      this.fixNodes(params.nodes);
    });


    this.network.on('afterDrawing', (params) => {
      if (this.draggingRect) {
        this.drawRectangle();
      }
    });

        // Variables used to draw selection rectangle.
    this.canvas = this.network.canvas.frame.canvas;
    this.ctx = this.canvas.getContext('2d');
    this.rect = {};


    return this;
  }

    // Methods used to draw a selection rectangle on the canvas and select multiple nodes
  resetRectangle() {
    this.draggingRect = false;
    this.selectNodesFromHighlight();
    this.rect.w = 0;
    this.rect.h = 0;
  }

  startRectangle(x, y) {
    this.rect.startX = x;
    this.rect.startY = y;
  }

  updateRectangle(x, y) {
    if (this.draggingRect) {
      const canvasPosition = this.network.DOMtoCanvas({
        x,
        y,
      });
      this.rect.w = canvasPosition.x - this.rect.startX;
      this.rect.h = canvasPosition.y - this.rect.startY;
            // Force redraw the canvas which will also draw the rectangle with new size.
      this.network.redraw();
    }
  }

  drawRectangle() {
    this.ctx.setLineDash([5]);
    this.ctx.strokeStyle = 'rgb(0, 102, 0)';
    this.ctx.strokeRect(this.rect.startX, this.rect.startY, this.rect.w, this.rect.h);
    this.ctx.setLineDash([]);
    this.ctx.fillStyle = 'rgba(0, 255, 0, 0.2)';
    this.ctx.fillRect(this.rect.startX, this.rect.startY, this.rect.w, this.rect.h);
  }

  selectNodesFromHighlight() {
    const nodesIdInDrawing = [];
    const xRange = Visualiser.getStartToEnd(this.rect.startX, this.rect.w);
    const yRange = Visualiser.getStartToEnd(this.rect.startY, this.rect.h);

    const allNodes = this.nodes.get();
    const arrayLength = allNodes.length;
    for (let i = 0; i < arrayLength; i++) {
      const curNode = allNodes[i];
      const nodePosition = this.network.getPositions([curNode.id]);
      const nodeXY = nodePosition[curNode.id];
      if (xRange.start <= nodeXY.x && nodeXY.x <= xRange.end && yRange.start <= nodeXY.y && nodeXY.y <= yRange.end) {
        nodesIdInDrawing.push(curNode.id);
      }
    }
    this.network.selectNodes(nodesIdInDrawing);
  }

  static getStartToEnd(start, theLen) {
    return theLen > 0 ? {
      start,
      end: start + theLen,
    } : {
      start: start + theLen,
      end: start,
    };
  }

    //  ----------------------------------------------  //


    // Methods used to fix and release nodes when one or more are dragged //

  fixNodes(nodeIds) {
    if (new Date() - this.lastFixTime > 100) {
            // this.network.storePositions();
      this.lastFixTime = new Date();
      if (nodeIds !== undefined) {
        nodeIds.forEach(nodeId => this.fixSingleNode(nodeId));
      } else {
        this.nodes.forEach((node) => {
          this.fixSingleNode(node.id);
        });
      }
    }
  }

  fixSingleNode(nodeId) {
    if (nodeId === undefined) return;
    this.nodes.update({
      id: nodeId,
      fixed: {
        x: true,
        y: true,
      },
    });
  }

  releaseNodes(nodeIds) {
    if (nodeIds === undefined) return;
    nodeIds.forEach(nodeId => this.nodes.update({
      id: nodeId,
      fixed: {
        x: false,
        y: false,
      },
    }));
  }

    // --------------------------  //


    // Fit the graph to the window size only on the first ajax call,
    // then leave zoom control to the user
  fitGraphToWindow() {
    if (!this.alreadyFittedToWindow) {
      this.network.fit();
      this.alreadyFittedToWindow = true;
    }
  }
    /**
     * Add a node to the graph. This can be called at any time *after* render().
     */
  addNode(href, bp, ap, ls) {
    if (!this.nodeExists(bp.id)) {
      const colorObj = this.style.getNodeColour(bp.type, bp.baseType);
      const highlightObj = {
        highlight: Object.assign(colorObj.highlight, {
          border: colorObj.highlight.background,
        }),
      };
      const hoverObj = {
        hover: highlightObj.highlight,
      };
      this.nodes.add({
        id: bp.id,
        href,
        label: this.generateLabel(bp.type, ap, bp.label),
        baseLabel: bp.label,
        type: bp.type,
        title: bp.type,
        baseType: bp.baseType,
        color: Object.assign(colorObj, {
          border: colorObj.background,
        }, highlightObj, hoverObj),
        font: this.style.getNodeFont(bp.type, bp.baseType),
        shape: this.style.getNodeShape(bp.baseType),
        selected: false,
        ontology: bp.ontology,
        properties: ap,
        links: ls,
      });
    }

    return this;
  }

  disablePhysicsOnNode(id) {
    if (this.nodeExists(id)) {
      this.nodes.update({
        id,
        physics: false,
      });
    }
    return this;
  }

    /**
     * Add edge between two nodes with @label. This can be called at any time *after* render().
     */
  addEdge(fromNode, toNode, label) {
    if (!this.alreadyConnected(fromNode, toNode)) {
      this.edges.add({
        from: fromNode,
        to: toNode,
        label,
        color: this.style.getEdgeColour(),
        font: this.style.getEdgeFont(),
      });
    }
    return this;
  }

    /**
     * Delete a node and its edges
     */
  deleteNode(id) {
    if (this.nodeExists(id)) {
      this.deleteEdges(id);
      this.nodes.remove(id);
    }
    return this;
  }

    /**
     * Removes all nodes and edges from graph
     */
  clearGraph() {
    this.nodes.clear();
    this.edges.clear();
    this.network.setData({
      nodes: this.nodes,
      edges: this.edges,
    });
    return this;
  }


  getNodeType(id) {
    if (id in this.nodes._data) {
      return this.nodes._data[id].type;
    }
    return undefined;
  }

  getNode(id) {
    return this.nodes._data[id];
  }

  getAllNodeProperties(id) {
    if (id in this.nodes._data) {
      return Object.keys(this.nodes._data[id].properties).sort();
    }
    return [];
  }

  getNodeLabel(id) {
    return this.nodes._data[id].label;
  }

  setDisplayProperties(type, properties) {
    if (type in this.displayProperties && properties.length === 0) {
      delete this.displayProperties[type];
    } else {
      this.displayProperties[type] = properties;
    }

    this.updateNodeLabels(type);
    return this;
  }

    /*
    Internal methods
    */

    /**
     * Check if node has already been added to graph.
     */
  nodeExists(id) {
    return (id in this.nodes._data);
  }

    /**
     * Check if (a,b) match (x,y) in either combination.
     */
  static matching(a, b, x, y) {
    return ((a === x && b === y) || (a === y && b === x));
  }

    /**
     * Check if two nodes (a,b) are already connected by an edge.
     */
  alreadyConnected(a, b) {
    if (!(a in this.nodes._data && b in this.nodes._data)) {
      return false;
    }

    return _.contains(_.values(this.edges._data)
            .map(x => Visualiser.matching(a, b, x.to, x.from)),
            true);
  }

    /**
     * Delete all edges connected to node id
     */
  deleteEdges(id) {
    this.edges.forEach((x) => {
      if (x.to === id || x.from === id) this.edges.remove(x.id);
    });
  }

  generateLabel(type, properties, label) {
    if (type in this.displayProperties) {
      return this.displayProperties[type].reduce((l, x) => {
        let value = (properties[x] === undefined) ? '' : properties[x].label;
        if (value.length > 40) value = `${value.substring(0, 40)}...`;
        return `${(l.length ? `${l}\n` : l) + x}: ${value}`;
      }, '');
    }
    return label;
  }

  updateNodeLabels(type) {
    this.nodes._data = _.mapObject(this.nodes._data, (v, k) => {
      if (v.type === type) {
        this.nodes.update({
          id: k,
          label: this.generateLabel(type, v.properties, v.baseLabel),
        });
      }
      return v;
    });
  }

}
