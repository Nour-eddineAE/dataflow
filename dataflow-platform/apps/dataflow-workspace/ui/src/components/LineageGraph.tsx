import {Select} from '@mantine/core';
import {IconSearch} from '../icons';
import {type Edge, Handle, type Node, type NodeProps, Position, ReactFlow, type ReactFlowInstance, useNodesState} from '@xyflow/react';
import {ICON, ICON_SMALL} from '../lib/icons';
import '@xyflow/react/dist/style.css';
import {createContext, type CSSProperties, memo, useContext, useEffect, useMemo, useState} from 'react';
import type {Lineage} from '../api/types';
import {layered} from '../lib/graphLayout';
import {layer, tableStyle} from '../lib/layers';
import {lineagePath} from '../lib/lineagePath';
import {plural} from '../lib/format';
import {ArrangeControls, GraphBackground, GRAPH_MOTION, movedTo} from './graph/CanvasControls';
import './graph/Graph.css';
import './LineageGraph.css';
import {COLOR} from '../lib/colors';

const WIDTH = 230;
const HEADER = 40;
const ROW = 24;
const PADDING = 0.15;
const TRACE_WIDTH = 300;
const LIT_STROKE = 1.8;
const EDGE_OPACITY = 0.9;
const OUT_OF_TRACE_OPACITY = 0.1;

type Table = Lineage['tables'][number];
type ColumnsData = { table: Table; focused: boolean; hidden: number };
type Focus = {
    traced: Set<string>;
    hot: Set<string>;
    focus?: string;
    onFocus: (key?: string) => void;
    onPick: (key: string) => void;
};

const NONE = new Set<string>();
const FocusContext = createContext<Focus>({traced: NONE, hot: NONE, onFocus: () => undefined, onPick: () => undefined});

const ColumnsNode = memo(function ColumnsNode({data}: NodeProps<Node<ColumnsData>>) {
    const {traced, hot, focus, onFocus, onPick} = useContext(FocusContext);
    const {table, focused, hidden} = data;
    const Glyph = tableStyle(table.id, table.layer).icon;
    return (
        <div className="df-lineage-table" onMouseLeave={() => onFocus(undefined)} data-focused={focused || undefined}
             style={{'--df-node-tone': layer(table.layer).tone} as CSSProperties}>
            <div className="df-lineage-head">
                <Glyph {...ICON}/>
                <span className="df-lineage-name">{table.label}</span>
                <span className="df-lineage-layer">{layer(table.layer).title}</span>
            </div>
            {table.columns.map((column) => {
                const key = `${table.id}|${column.name}`;
                return (
                    <div key={column.name} className="df-lineage-column" data-dim={(traced.size > 0 && !traced.has(key)) || undefined}
                         data-hot={hot.has(key) || undefined} data-focus={focus === key || undefined}
                         onMouseEnter={() => onFocus(key)}>
                        <Handle type="target" position={Position.Left} id={column.name} isConnectable={false}/>
                        <button type="button" className="df-lineage-column-pick nodrag" aria-pressed={traced.has(key) && focus === key}
                                onFocus={() => onFocus(key)} onBlur={() => onFocus(undefined)} onClick={() => onPick(key)}>
                            <span className="df-lineage-column-name">{column.name}</span>
                            <span className="df-lineage-column-type">{column.type ?? '—'}</span>
                        </button>
                        <Handle type="source" position={Position.Right} id={column.name} isConnectable={false}/>
                    </div>
                );
            })}
            {hidden > 0 && <div className="df-lineage-more">{`+${plural(hidden, 'column')}`}</div>}
        </div>
    );
});

const nodeTypes = {columns: ColumnsNode};

export function LineageGraph({lineage, height = 640, initialFocus, focusTable, hidden}: {
    lineage: Lineage;
    height?: number;
    initialFocus?: string;
    focusTable?: string;
    hidden?: Record<string, number>;
}) {
    const [hovered, setHovered] = useState<string>();
    const [pinned, setPinned] = useState<string | undefined>(initialFocus);
    const [flow, setFlow] = useState<ReactFlowInstance<Node<ColumnsData>>>();
    const focus = hovered ?? pinned;
    const hot = useMemo(() => lineagePath(lineage.edges, hovered), [lineage, hovered]);
    const traced = useMemo(() => lineagePath(lineage.edges, pinned), [lineage, pinned]);
    const context = useMemo<Focus>(() => ({
        traced, hot, focus, onFocus: setHovered,
        onPick: (key: string) => setPinned((current) => (current === key ? undefined : key)),
    }), [traced, hot, focus]);

    useEffect(() => {
        if (!flow || traced.size === 0) return;
        const tables = [...new Set([...traced].map((key) => key.split('|')[0]))];
        void flow.fitView({nodes: tables.map((id) => ({id})), padding: PADDING, duration: GRAPH_MOTION});
    }, [flow, traced]);

    const columns = useMemo(() => lineage.tables.map((table) => ({
        group: table.label,
        items: table.columns.map((column) => ({value: `${table.id}|${column.name}`, label: `${table.label}.${column.name}`})),
    })), [lineage]);

    const placed = useMemo(() => place(lineage, focusTable, hidden), [lineage, focusTable, hidden]);
    const [nodes, setNodes, onNodesChange] = useNodesState(placed);
    useEffect(() => setNodes(placed), [placed, setNodes]);

    const edges = useMemo<Edge[]>(() => lineage.edges
        .filter((edge) => edge.fromTable !== edge.toTable)
        .map((edge) => {
            const from = `${edge.fromTable}|${edge.fromColumn}`;
            const to = `${edge.toTable}|${edge.toColumn}`;
            const inTrace = traced.has(from) && traced.has(to);
            const lit = inTrace || (hot.has(from) && hot.has(to));
            return {
                id: `${edge.fromTable}|${edge.fromColumn}->${edge.toTable}|${edge.toColumn}`,
                source: edge.fromTable,
                sourceHandle: edge.fromColumn,
                target: edge.toTable,
                targetHandle: edge.toColumn,
                zIndex: lit ? 1 : 0,
                style: {
                    stroke: lit ? COLOR.primary : COLOR.edge,
                    strokeWidth: lit ? LIT_STROKE : 1,
                    opacity: traced.size > 0 && !inTrace && !lit ? OUT_OF_TRACE_OPACITY : EDGE_OPACITY,
                },
            };
        }), [lineage, traced, hot]);

    return (
        <div style={{height}} className="df-lineage">
            <div className="df-lineage-bar">
                <Select size="xs" w={TRACE_WIDTH} data={columns} value={pinned ?? null} onChange={(value) => setPinned(value ?? undefined)}
                        searchable clearable placeholder="Trace a column" leftSection={<IconSearch {...ICON_SMALL}/>}
                        nothingFoundMessage="No column" maxDropdownHeight={320} aria-label="Trace a column"/>
            </div>
            <div className="df-graph df-lineage-canvas">
                <div className="df-lineage-tools">
                    <ColumnCard lineage={lineage} focus={focus}/>
                </div>
                <FocusContext.Provider value={context}>
                    <ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} nodeTypes={nodeTypes} fitView
                               fitViewOptions={{padding: PADDING}} minZoom={0.15} maxZoom={1.5} nodesConnectable={false}
                               nodesFocusable={false} edgesFocusable={false}
                               onlyRenderVisibleElements onInit={setFlow} proOptions={{hideAttribution: true}}>
                        <GraphBackground/>
                        <ArrangeControls targets={targetsOf(placed)} onMove={(moved) => setNodes(movedTo(moved))} padding={PADDING}/>
                    </ReactFlow>
                </FocusContext.Provider>
            </div>
        </div>
    );
}

function ColumnCard({lineage, focus}: { lineage: Lineage; focus?: string }) {
    const [tableId, name] = focus?.split('|') ?? [];
    const table = lineage.tables.find((candidate) => candidate.id === tableId);
    const column = table?.columns.find((candidate) => candidate.name === name);
    return (
        <div className="df-lineage-card" data-shown={!!column || undefined} aria-live="polite">
            {table && column && (
                <>
                    <div className="df-lineage-card-name">
                        <span>{table.label}.{column.name}</span>
                        <span className="df-lineage-column-type">{column.type ?? column.role}</span>
                    </div>
                    {column.expression && <code className="df-lineage-card-code">{column.expression}</code>}
                </>
            )}
        </div>
    );
}

function place(lineage: Lineage, focusTable?: string, hidden: Record<string, number> = {}): Node<ColumnsData>[] {
    const links = lineage.edges.map((edge) => ({from: edge.fromTable, to: edge.toTable}));
    const rows = (table: Table) => table.columns.length + (hidden[table.id] ? 1 : 0);
    const positions = layered(lineage.tables.map((table) => ({id: table.id, width: WIDTH, height: HEADER + rows(table) * ROW})), links,
        {direction: 'LR', align: 'start', rankGap: 96, nodeGap: 24, margin: 16});
    return lineage.tables.map((table) => ({
        id: table.id, type: 'columns', position: positions[table.id],
        data: {table, focused: table.id === focusTable, hidden: hidden[table.id] ?? 0},
    }));
}

function targetsOf(nodes: Node[]) {
    return Object.fromEntries(nodes.map((node) => [node.id, node.position]));
}
