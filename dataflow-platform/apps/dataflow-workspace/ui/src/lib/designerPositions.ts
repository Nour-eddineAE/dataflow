import {layered, type Point, type Positions} from './graphLayout';
import type {DesignEdge, DesignNode} from './specModel';

export type {Point, Positions} from './graphLayout';

export const NODE_WIDTH = 200;
export const NODE_HEIGHT = 52;
export const RANK_GAP = 40;
const NODE_GAP = 20;

export function arrange(nodes: DesignNode[], edges: DesignEdge[]): Positions {
    return layered(nodes.map((node) => ({id: node.id, width: NODE_WIDTH, height: NODE_HEIGHT})), edges,
        {direction: 'LR', rankGap: RANK_GAP, nodeGap: NODE_GAP});
}

export function place(nodes: DesignNode[], edges: DesignEdge[], known: Positions): Positions {
    const missing = nodes.filter((node) => !known[node.id]);
    if (!missing.length) return known;
    if (!Object.keys(known).some((id) => nodes.some((node) => node.id === id))) return {...known, ...arrange(nodes, edges)};
    const placed: Positions = {...known};
    for (const node of missing) {
        placed[node.id] = free(beside(node.id, edges, placed), placed, nodes);
    }
    return placed;
}

function beside(id: string, edges: DesignEdge[], placed: Positions): Point {
    const upstream = edges.filter((edge) => edge.to === id).map((edge) => placed[edge.from]).filter(Boolean);
    if (upstream.length) {
        const right = Math.max(...upstream.map((point) => point.x));
        return {x: right + NODE_WIDTH + RANK_GAP, y: Math.min(...upstream.map((point) => point.y))};
    }
    const downstream = edges.filter((edge) => edge.from === id).map((edge) => placed[edge.to]).filter(Boolean);
    if (downstream.length) {
        return {x: Math.min(...downstream.map((point) => point.x)) - NODE_WIDTH - RANK_GAP, y: downstream[0].y};
    }
    const points = Object.values(placed);
    if (!points.length) return {x: 0, y: 0};
    return {x: Math.min(...points.map((point) => point.x)), y: Math.max(...points.map((point) => point.y)) + NODE_HEIGHT + NODE_GAP};
}

function free(start: Point, placed: Positions, nodes: DesignNode[]): Point {
    const live = nodes.map((node) => placed[node.id]).filter(Boolean);
    const overlaps = (point: Point) => live.some((other) => Math.abs(other.x - point.x) < NODE_WIDTH
        && Math.abs(other.y - point.y) < NODE_HEIGHT + NODE_GAP / 2);
    let point = start;
    while (overlaps(point)) point = {x: point.x, y: point.y + NODE_HEIGHT + NODE_GAP};
    return point;
}

export type Placement = { nodes: DesignNode[]; positions: Positions; automatic: Positions; layout: Positions };

export function placeBlocks(previous: Pick<Placement, 'nodes' | 'automatic'>, nodes: DesignNode[], edges: DesignEdge[],
                            positions: Positions): Placement {
    const carried = follow(previous.automatic, renamedIds(previous.nodes, nodes));
    const placed = place(nodes, edges, {...carried, ...positions});
    const automatic = Object.fromEntries(nodes.map((node) => [node.id, carried[node.id] ?? placed[node.id]]));
    return {nodes, positions, automatic, layout: {...automatic, ...positions}};
}

export function renamedIds(before: DesignNode[], after: DesignNode[]): Map<string, string> {
    const beforeIds = new Set(before.map((node) => node.id));
    const afterIds = new Set(after.map((node) => node.id));
    const renamed = new Map<string, string>();
    for (const node of after) {
        if (node.index === undefined || beforeIds.has(node.id)) continue;
        const previous = before.find((candidate) => candidate.kind === node.kind && candidate.index === node.index
            && !afterIds.has(candidate.id));
        if (previous) renamed.set(previous.id, node.id);
    }
    return renamed;
}

export function follow(positions: Positions, renamed: Map<string, string>): Positions {
    if (!renamed.size) return positions;
    return Object.fromEntries(Object.entries(positions).map(([id, point]) => [renamed.get(id) ?? id, point]));
}

export function withoutNode(positions: Positions, node: DesignNode): Positions {
    return Object.fromEntries(Object.entries(positions).filter(([id]) => id !== node.id));
}
