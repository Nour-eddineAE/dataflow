import dagre from '@dagrejs/dagre';

export type Point = { x: number; y: number };
export type Positions = Record<string, Point>;
export type Box = { id: string; width: number; height: number };
export type Link = { from: string; to: string };
export type Direction = 'LR' | 'TB';
export type Align = 'center' | 'start';

type Options = { direction: Direction; align?: Align; rankGap?: number; nodeGap?: number; margin?: number };

const GRID = 4;

export function layered(boxes: Box[], links: Link[], {direction, align = 'center', rankGap = 64, nodeGap = 20, margin = 24}: Options): Positions {
    if (!boxes.length) return {};
    const across = direction === 'LR';
    const graph = new dagre.graphlib.Graph();
    graph.setGraph({rankdir: direction, nodesep: nodeGap, ranksep: rankGap, ranker: 'network-simplex'});
    graph.setDefaultEdgeLabel(() => ({}));
    boxes.forEach((box) => graph.setNode(box.id, {width: box.width, height: box.height}));
    links.filter((link) => link.from !== link.to).forEach((link) => graph.setEdge(link.from, link.to));
    dagre.layout(graph);

    const ranks = new Map<number, { box: Box; order: number }[]>();
    boxes.forEach((box) => {
        const center = graph.node(box.id);
        const rank = Math.round(across ? center.x : center.y);
        ranks.set(rank, [...(ranks.get(rank) ?? []), {box, order: across ? center.y : center.x}]);
    });
    const columns = [...ranks.entries()].sort(([a], [b]) => a - b)
        .map(([, members]) => members.sort((a, b) => a.order - b.order).map(({box}) => box));

    const depth = (box: Box) => (across ? box.width : box.height);
    const breadth = (box: Box) => (across ? box.height : box.width);
    const spans = columns.map((column) => column.reduce((sum, box) => sum + breadth(box), 0) + nodeGap * (column.length - 1));
    const widest = Math.max(...spans);

    const positions: Positions = {};
    let along = margin;
    columns.forEach((column, index) => {
        const thickness = Math.max(...column.map(depth));
        let cross = margin + (align === 'center' ? (widest - spans[index]) / 2 : 0);
        column.forEach((box) => {
            const offset = (thickness - depth(box)) / 2;
            positions[box.id] = across
                ? {x: snap(along + offset), y: snap(cross)}
                : {x: snap(cross), y: snap(along + offset)};
            cross += breadth(box) + nodeGap;
        });
        along += thickness + rankGap;
    });
    return positions;
}

function snap(value: number) {
    return Math.round(value / GRID) * GRID;
}
