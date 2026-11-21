import {type Edge, Handle, MarkerType, type Node, type NodeProps, Position, ReactFlow, useNodesState} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {type KeyboardEvent, memo, useCallback, useEffect, useMemo, useState} from 'react';
import type {GraphNode} from '../api/types';
import {NODE_HEIGHT, NODE_WIDTH, RANK_GAP} from '../lib/designerPositions';
import {formatCompact, formatDuration} from '../lib/format';
import {layered} from '../lib/graphLayout';
import {type Direction, extent, readableDirection} from '../lib/flowDirection';
import {layer, tableStyle} from '../lib/layers';
import {ArrangeControls, GraphBackground, movedTo, RefitOnChange, SNUG_FIT_PADDING} from './graph/CanvasControls';
import {NodeCard} from './graph/NodeCard';
import {StatusIcon} from './StatusBadge';
import {COLOR} from '../lib/colors';
import {ICON_SMALL} from '../lib/icons';

const EDGE = COLOR.edge;
const MAX_ZOOM = 1.1;
const TOP_DOWN_RANK_GAP = 40;
const NODE_GAP = 16;
const MARGIN = 8;

type TableData = { node: GraphNode; selected: boolean; dimmed: boolean; vertical: boolean };

const TableNode = memo(function TableNode({data}: NodeProps<Node<TableData>>) {
    const {node} = data;
    const facts = [
        node.rows !== undefined && node.rows !== null ? `${formatCompact(node.rows)} ${unit(node.layer, node.rows)}` : undefined,
        node.durationMs !== undefined && node.durationMs !== null ? formatDuration(node.durationMs) : undefined,
    ].filter(Boolean);
    if (!facts.length && node.note) facts.push(node.note);
    return (
        <>
            <Handle type="target" position={data.vertical ? Position.Top : Position.Left} isConnectable={false}/>
            <NodeCard icon={tableStyle(node.id, node.layer).icon} tone={layer(node.layer).tone} label={node.label} selected={data.selected} dimmed={data.dimmed}
                      detail={`${layer(node.layer).title}${facts.length ? ` · ${facts.join(' · ')}` : ''}`}
                      dashed={!node.flow && node.layer !== 'input'}
                      trailing={node.flow ? <StatusIcon status={node.status} {...ICON_SMALL}/> : undefined}/>
            <Handle type="source" position={data.vertical ? Position.Bottom : Position.Right} isConnectable={false}/>
        </>
    );
});

function place(nodes: GraphNode[], edges: { from: string; to: string }[], direction: Direction) {
    return layered(nodes.map((node) => ({id: node.id, width: NODE_WIDTH, height: NODE_HEIGHT})), edges,
        {direction, rankGap: direction === 'LR' ? RANK_GAP : TOP_DOWN_RANK_GAP, nodeGap: NODE_GAP, margin: MARGIN});
}

function placements(shape: string, nodes: GraphNode[], edges: { from: string; to: string }[]) {
    return {shape, LR: place(nodes, edges, 'LR'), TB: place(nodes, edges, 'TB')};
}

function unit(layerName: string, count: number) {
    const noun = layerName === 'landing' ? 'file' : 'row';
    return count === 1 ? noun : `${noun}s`;
}

const nodeTypes = {table: TableNode};

const NODE = {width: NODE_WIDTH, height: NODE_HEIGHT};

export function FlowGraph({nodes, edges, height = 420, selected, onSelect, highlight, direction}: {
    nodes: GraphNode[];
    edges: { from: string; to: string }[];
    height?: number | string;
    direction?: Direction;
    selected?: string;
    onSelect?: (id: string) => void;
    highlight?: string[];
}) {
    const graphShape = `${nodes.map((node) => node.id).join(',')}|${edges.map((edge) => `${edge.from}>${edge.to}`).join(',')}`;
    const [layout, setLayout] = useState(() => placements(graphShape, nodes, edges));
    if (layout.shape !== graphShape) setLayout(placements(graphShape, nodes, edges));
    const [width, setWidth] = useState(0);
    const frame = useCallback((element: HTMLDivElement | null) => {
        if (!element) return undefined;
        const observer = new ResizeObserver(([entry]) => setWidth(Math.round(entry.contentRect.width)));
        observer.observe(element);
        return () => observer.disconnect();
    }, []);
    const chosen = direction ?? (width && typeof height === 'number'
        ? readableDirection(extent(layout.LR, NODE), extent(layout.TB, NODE), {width, height})
        : 'LR');
    const positions = layout[chosen];
    const shape = `${chosen}|${graphShape}`;
    const lit = highlight?.join('|') ?? '';
    const designed = useMemo<Node<TableData>[]>(() => {
        const highlighted = new Set(lit ? lit.split('|') : []);
        return nodes.map((node) => ({
            id: node.id,
            type: 'table',
            position: positions[node.id] ?? {x: 0, y: 0},
            data: {node, selected: node.id === selected, dimmed: highlighted.size > 0 && !highlighted.has(node.id), vertical: chosen === 'TB'},
        }));
    }, [nodes, positions, selected, lit, chosen]);
    const [flowNodes, setFlowNodes, onNodesChange] = useNodesState(designed);

    useEffect(() => setFlowNodes((current) => {
        const moved = new Map(current.map((node) => [node.id, node.position]));
        return designed.map((node) => ({...node, position: moved.get(node.id) ?? node.position}));
    }), [designed, setFlowNodes]);
    useEffect(() => setFlowNodes((current) => current.map((node) => ({...node, position: positions[node.id] ?? node.position}))),
        [positions, setFlowNodes]);

    const flowEdges = useMemo<Edge[]>(() => {
        const status = new Map(nodes.map((node) => [node.id, node.status]));
        return edges.map((edge) => ({
            id: `${edge.from}->${edge.to}`,
            source: edge.from,
            target: edge.to,
            animated: status.get(edge.to) === 'RUNNING' || status.get(edge.to) === 'STARTED',
            markerEnd: {type: MarkerType.ArrowClosed, width: 14, height: 14, color: EDGE},
            style: {stroke: EDGE, strokeWidth: 1},
        }));
    }, [nodes, edges]);

    const openFocused = (event: KeyboardEvent<HTMLDivElement>) => {
        if (!onSelect || (event.key !== 'Enter' && event.key !== ' ')) return;
        const id = event.target instanceof HTMLElement ? event.target.closest<HTMLElement>('.react-flow__node')?.dataset.id : undefined;
        if (!id) return;
        event.preventDefault();
        onSelect(id);
    };

    return (
        <div style={{height}} className="df-graph" ref={frame} onKeyDown={openFocused}>
            {width > 0 && <ReactFlow nodes={flowNodes} edges={flowEdges} onNodesChange={onNodesChange} nodeTypes={nodeTypes} fitView
                       fitViewOptions={{padding: SNUG_FIT_PADDING, maxZoom: MAX_ZOOM}} minZoom={0.3} maxZoom={1.6}
                       nodesConnectable={false} elementsSelectable={!!onSelect} nodesFocusable={!!onSelect} edgesFocusable={false}
                       onNodeClick={(_, node) => onSelect?.(node.id)} proOptions={{hideAttribution: true}}>
                <GraphBackground/>
                <ArrangeControls targets={positions} onMove={(moved) => setFlowNodes(movedTo(moved))} padding={SNUG_FIT_PADDING}
                                 maxZoom={MAX_ZOOM}/>
                <RefitOnChange shape={shape} padding={SNUG_FIT_PADDING} maxZoom={MAX_ZOOM}/>
            </ReactFlow>}
        </div>
    );
}
