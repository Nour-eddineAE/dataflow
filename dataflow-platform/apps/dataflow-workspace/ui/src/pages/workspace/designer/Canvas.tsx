import {Menu, Text} from '@mantine/core';
import {IconCopy, IconDatabase, IconTrash} from '../../../icons';
import {
    type Connection,
    type Edge,
    Handle,
    MarkerType,
    type Node,
    type NodeProps,
    Position,
    ReactFlow,
    useNodesInitialized,
    useNodesState,
    useReactFlow,
} from '@xyflow/react';
import {ICON} from '../../../lib/icons';
import {tableOfNode} from '../../../lib/links';
import {shortcutLabel} from '../../../components/actions/shortcut';
import {useShortcuts} from '../../../components/actions/useShortcuts';
import '@xyflow/react/dist/style.css';
import {type DragEvent, type KeyboardEvent, memo, type MouseEvent, useCallback, useEffect, useEffectEvent, useMemo, useRef, useState} from 'react';
import {ArrangeControls, GRAPH_MOTION, GraphBackground, movedTo, SNUG_FIT_PADDING} from '../../../components/graph/CanvasControls';
import {NodeCard} from '../../../components/graph/NodeCard';
import {Count} from '../../../components/Count';
import {
    arrange,
    NODE_HEIGHT,
    NODE_WIDTH,
    RANK_GAP,
    placeBlocks,
    type Positions,
} from '../../../lib/designerPositions';
import {
    addBlock,
    addInputTable,
    type Block,
    canConnect,
    catalogTable,
    connect,
    designGraph,
    type DesignNode,
    disconnect,
    duplicateNode,
    isRemovable,
    PIPELINE,
    type Spec,
} from '../../../lib/specModel';
import {readableViewport} from '../../../lib/graphViewport';
import {DIRECTIONS, neighbour} from '../../../lib/graphNavigation';
import {nodeStyle} from './blocks';
import {COLOR} from '../../../lib/colors';


export const DRAG_TYPE = 'application/x-dataflow-block';
export const DRAG_TABLE = 'application/x-dataflow-table';
const GRID = 8;
const SNAP: [number, number] = [GRID, GRID];
const STROKE = {plain: 1.2, lit: 1.6};
const ARROW = 14;
const RESIZED = 24;
const SETTLE = 220;
const DUPLICATE = 'Mod+D';
const FIT = {minZoom: 0.85, maxZoom: 1, padding: SNUG_FIT_PADDING, edge: 24};

type BlockData = { node: DesignNode; spec: Spec; problems: number; selected: boolean };

type NodeMenu = { node: DesignNode; x: number; y: number };

export function Canvas({dataset, spec, positions, problems, selected, onSelect, onEdit, onRemove, onDuplicate}: {
    dataset: string;
    spec: Spec;
    positions: Positions;
    problems: Map<string, string[]>;
    selected: string;
    onSelect: (id: string) => void;
    onEdit: (spec: Spec, positions?: Positions) => void;
    onRemove: (node: DesignNode) => void;
    onDuplicate: (node: DesignNode) => void;
}) {
    const flow = useReactFlow();
    const [menu, setMenu] = useState<NodeMenu>();
    const frame = useRef<HTMLDivElement>(null);
    const fitted = useRef(false);
    const moved = useRef(false);
    const width = useRef(0);
    const picked = useEffectEvent(() => selected !== PIPELINE);
    const graph = useMemo(() => designGraph(spec), [spec]);
    const [placement, setPlacement] = useState(() => placeBlocks({nodes: [], automatic: {}}, graph.nodes, graph.edges, positions));
    let current = placement;
    if (placement.nodes !== graph.nodes || placement.positions !== positions) {
        current = placeBlocks(placement, graph.nodes, graph.edges, positions);
        setPlacement(current);
    }
    const {layout} = current;
    const byId = useMemo(() => new Map(graph.nodes.map((node) => [node.id, node])), [graph]);
    const initialized = useNodesInitialized();

    const fit = useCallback((duration?: number) => {
        const element = frame.current;
        if (!element) return;
        const viewport = readableViewport(flow.getNodesBounds(flow.getNodes()), element.clientWidth, element.clientHeight, FIT);
        void flow.setViewport(viewport, duration ? {duration} : undefined);
    }, [flow]);

    useEffect(() => {
        if (!initialized || fitted.current) return;
        fitted.current = true;
        width.current = frame.current?.clientWidth ?? 0;
        fit();
    }, [initialized, fit]);

    const reveal = useEffectEvent((id: string) => {
        const element = frame.current;
        const spot = layout[id];
        if (!element || !spot) return;
        const {x, y, zoom} = flow.getViewport();
        const left = spot.x * zoom + x;
        const top = spot.y * zoom + y;
        const inside = left >= FIT.edge && top >= FIT.edge && left + NODE_WIDTH * zoom <= element.clientWidth - FIT.edge
            && top + NODE_HEIGHT * zoom <= element.clientHeight - FIT.edge;
        const readable = Math.max(zoom, FIT.minZoom);
        if (!inside || readable !== zoom) {
            void flow.setCenter(spot.x + NODE_WIDTH / 2, spot.y + NODE_HEIGHT / 2, {zoom: readable, duration: SETTLE});
        }
    });
    const revealSelected = useEffectEvent(() => {
        if (selected !== PIPELINE) reveal(selected);
    });

    useEffect(() => {
        const element = frame.current;
        if (!element) return undefined;
        let timer = 0;
        const observer = new ResizeObserver(([entry]) => {
            const next = entry.contentRect.width;
            if (!fitted.current || Math.abs(next - width.current) < RESIZED) return;
            width.current = next;
            window.clearTimeout(timer);
            timer = window.setTimeout(() => {
                if (picked()) revealSelected();
                else if (!moved.current) fit(SETTLE);
            }, SETTLE);
        });
        observer.observe(element);
        return () => {
            window.clearTimeout(timer);
            observer.disconnect();
        };
    }, [fit]);

    const placed = useMemo<Node<BlockData>[]>(() => graph.nodes.map((node) => ({
        id: node.id,
        type: 'block',
        position: layout[node.id],
        data: {node, spec, problems: problems.get(node.id)?.length ?? 0, selected: node.id === selected},
        selected: node.id === selected,
        deletable: isRemovable(node),
    })), [graph, layout, spec, problems, selected]);
    const [nodes, setNodes, onNodesChange] = useNodesState(placed);
    useEffect(() => setNodes((existing) => {
        const kept = new Map(existing.map((node) => [node.id, node]));
        return placed.map((next) => {
            const old = kept.get(next.id);
            return old ? {...old, position: next.position, data: next.data, selected: next.selected, deletable: next.deletable} : next;
        });
    }), [placed, setNodes]);

    const edges = useMemo<Edge[]>(() => graph.edges.map((edge) => {
        const lit = edge.from === selected || edge.to === selected;
        const color = lit ? COLOR.edgeActive : COLOR.edge;
        return {
            id: `${edge.from}->${edge.to}`,
            source: edge.from,
            target: edge.to,
            deletable: edge.editable,
            zIndex: lit ? 1 : 0,
            markerEnd: {type: MarkerType.ArrowClosed, width: ARROW, height: ARROW, color},
            style: {stroke: color, strokeWidth: lit ? STROKE.lit : STROKE.plain},
        };
    }), [graph, selected]);

    const ends = useCallback((connection: Connection | Edge) => {
        const from = byId.get(connection.source);
        const to = byId.get(connection.target);
        return from && to ? [from, to] as const : undefined;
    }, [byId]);
    const valid = useCallback((connection: Connection | Edge) => {
        const pair = ends(connection);
        return !!pair && canConnect(spec, ...pair);
    }, [ends, spec]);
    const join = useCallback((connection: Connection) => {
        const pair = ends(connection);
        if (pair) onEdit(connect(spec, ...pair));
    }, [ends, spec, onEdit]);
    const settle = useCallback((_: unknown, __: Node, dragged: Node[]) =>
        onEdit(spec, {...positions, ...Object.fromEntries(dragged.map((node) => [node.id, node.position]))}), [spec, positions, onEdit]);
    const pick = useCallback((_: unknown, node: Node) => onSelect(node.id), [onSelect]);
    const clear = useCallback(() => onSelect(PIPELINE), [onSelect]);
    const removeNodes = useCallback((deleted: Node[]) => deleted.map((node) => byId.get(node.id))
        .filter((node): node is DesignNode => !!node)
        .sort((a, b) => (b.index ?? 0) - (a.index ?? 0)).forEach(onRemove), [byId, onRemove]);
    const removeEdges = useCallback((deleted: Edge[]) => onEdit(deleted.reduce((current, edge) => {
        const pair = ends(edge);
        return pair ? disconnect(current, ...pair) : current;
    }, spec)), [ends, spec, onEdit]);
    const glide = useCallback((moved: Positions) => setNodes(movedTo(moved)), [setNodes]);
    const tidy = useMemo(() => arrange(graph.nodes, graph.edges), [graph]);
    const keep = useCallback(() => {
        if (positions !== tidy) onEdit(spec, tidy);
    }, [tidy, positions, spec, onEdit]);

    useShortcuts(frame, {
        [DUPLICATE]: () => {
            const node = byId.get(selected);
            if (node) onDuplicate(node);
        },
    });


    useEffect(() => {
        if (initialized && selected !== PIPELINE) reveal(selected);
    }, [initialized, selected]);

    const step = (event: KeyboardEvent<HTMLDivElement>) => {
        const direction = DIRECTIONS[event.key];
        if (!direction || event.altKey || event.metaKey || event.ctrlKey) return;
        const nodes = {ids: graph.nodes.map((node) => node.id), links: graph.edges, positions: layout};
        const next = neighbour(nodes, selected, direction, NODE_WIDTH);
        if (!next) return;
        event.preventDefault();
        onSelect(next);
    };

    const openMenu = (event: MouseEvent, node: Node) => {
        const design = byId.get(node.id);
        const box = frame.current?.getBoundingClientRect();
        if (!design || !box) return;
        event.preventDefault();
        onSelect(design.id);
        setMenu({node: design, x: event.clientX - box.left, y: event.clientY - box.top});
    };

    const drop = (event: DragEvent) => {
        const table = event.dataTransfer.getData(DRAG_TABLE);
        const block = event.dataTransfer.getData(DRAG_TYPE) as Block;
        if (!block && !table) return;
        event.preventDefault();
        const point = flow.screenToFlowPosition({x: event.clientX, y: event.clientY});
        if (table) {
            const input = addInputTable(spec, table);
            onEdit(input.spec, {...positions, [input.id]: {x: point.x - NODE_WIDTH / 2, y: point.y - NODE_HEIGHT / 2}});
            onSelect(input.id);
            return;
        }
        const target = flow.getIntersectingNodes({x: point.x, y: point.y, width: 1, height: 1})
            .map((hit) => byId.get(hit.id)).find((node): node is DesignNode => !!node);
        const added = addBlock(spec, block);
        const node = designGraph(added.spec).nodes.find((candidate) => candidate.id === added.id)!;
        const wired = target && canConnect(added.spec, target, node) ? connect(added.spec, target, node) : added.spec;
        const spot = target
            ? {x: (layout[target.id]?.x ?? point.x) + NODE_WIDTH + RANK_GAP, y: layout[target.id]?.y ?? point.y}
            : {x: point.x - NODE_WIDTH / 2, y: point.y - NODE_HEIGHT / 2};
        onEdit(wired, {...positions, [added.id]: spot});
        onSelect(added.id);
    };

    return (
        <div ref={frame} className="df-graph df-canvas" tabIndex={0} aria-label="Pipeline graph" onKeyDown={step}
             onFocus={(event) => {
                 const first = graph.nodes[0];
                 if (event.target === event.currentTarget && event.currentTarget.matches(':focus-visible')
                     && selected === PIPELINE && first) onSelect(first.id);
             }}
             onDragOver={(event) => {
                 if (!event.dataTransfer.types.includes(DRAG_TYPE) && !event.dataTransfer.types.includes(DRAG_TABLE)) return;
                 event.preventDefault();
                 event.dataTransfer.dropEffect = 'copy';
             }} onDrop={drop}>
            <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES} minZoom={0.3} maxZoom={1.6} snapToGrid snapGrid={SNAP}
                       onNodesChange={onNodesChange} onNodeDragStop={settle} onNodeClick={pick} onPaneClick={clear}
                       onNodeContextMenu={openMenu}
                       onMoveStart={(event) => {
                           if (event) moved.current = true;
                       }}
                       isValidConnection={valid} onConnect={join} onNodesDelete={removeNodes} onEdgesDelete={removeEdges}
                       connectionLineStyle={CONNECTION} deleteKeyCode={DELETE_KEYS} proOptions={PRO_OPTIONS} disableKeyboardA11y
                       nodesFocusable={false} edgesFocusable={false}>
                <GraphBackground/>
                <ArrangeControls targets={tidy} onMove={glide} onDone={keep} padding={SNUG_FIT_PADDING}
                                 onFit={() => fit(GRAPH_MOTION)}/>
            </ReactFlow>
            {menu && <NodeActions dataset={dataset} spec={spec} menu={menu} onClose={() => setMenu(undefined)}
                                  onDuplicate={onDuplicate} onRemove={onRemove}/>}
        </div>
    );
}

function NodeActions({dataset, spec, menu, onClose, onDuplicate, onRemove}: {
    dataset: string;
    spec: Spec;
    menu: NodeMenu;
    onClose: () => void;
    onDuplicate: (node: DesignNode) => void;
    onRemove: (node: DesignNode) => void;
}) {
    const table = catalogTable(spec, menu.node);
    return (
        <Menu opened onChange={(opened) => !opened && onClose()} position="bottom-start" width={200}>
            <Menu.Target>
                <div className="df-canvas-menu-anchor" style={{left: menu.x, top: menu.y}}/>
            </Menu.Target>
            <Menu.Dropdown>
                <Menu.Label>{menu.node.label}</Menu.Label>
                <Menu.Item leftSection={<IconCopy {...ICON}/>} disabled={!duplicateNode(spec, menu.node)}
                           rightSection={<Text size="xs" c="dimmed">{shortcutLabel(DUPLICATE)}</Text>}
                           onClick={() => onDuplicate(menu.node)}>Duplicate</Menu.Item>
                <Menu.Item leftSection={<IconDatabase {...ICON}/>} disabled={!table} component="a"
                           href={table ? tableOfNode(dataset, table) : undefined} target="_blank" rel="noreferrer">
                    Open in catalog
                </Menu.Item>
                <Menu.Divider/>
                <Menu.Item leftSection={<IconTrash {...ICON}/>} color={COLOR.danger} disabled={!isRemovable(menu.node)}
                           onClick={() => onRemove(menu.node)}>Delete</Menu.Item>
            </Menu.Dropdown>
        </Menu>
    );
}

const DELETE_KEYS = ['Delete', 'Backspace'];
const PRO_OPTIONS = {hideAttribution: true};
const CONNECTION = {stroke: COLOR.edgeActive, strokeWidth: 1.6};

const BlockNode = memo(function BlockNode({data}: NodeProps<Node<BlockData>>) {
    const style = nodeStyle(data.node, data.spec);
    const stored = data.node.stored !== false;
    return (
        <>
            <Handle type="target" position={Position.Left}/>
            <NodeCard icon={style.icon} tone={style.tone} label={data.node.label} detail={data.node.detail}
                      selected={data.selected} invalid={data.problems > 0} dashed={!stored}
                      trailing={data.problems > 0 && <Count tone="danger">{data.problems}</Count>}/>
            <Handle type="source" position={Position.Right}/>
        </>
    );
});

const NODE_TYPES = {block: BlockNode};
