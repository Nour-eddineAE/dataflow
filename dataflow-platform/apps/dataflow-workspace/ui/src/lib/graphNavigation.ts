import type {Positions} from './designerPositions';

export type Direction = 'left' | 'right' | 'up' | 'down';
type Link = { from: string; to: string };
export type NavigableGraph = { ids: string[]; links: Link[]; positions: Positions };

export const DIRECTIONS: Record<string, Direction> = {
    ArrowLeft: 'left',
    ArrowRight: 'right',
    ArrowUp: 'up',
    ArrowDown: 'down',
};

export function neighbour({ids, links, positions}: NavigableGraph, selected: string | undefined,
                          direction: Direction, column: number): string | undefined {
    const at = (id: string) => positions[id] ?? {x: 0, y: 0};
    if (!selected || !ids.includes(selected)) {
        return [...ids].sort((a, b) => at(a).x - at(b).x || at(a).y - at(b).y)[0];
    }
    const here = at(selected);
    const nearest = (candidates: string[], distance: (id: string) => number) =>
        [...candidates].sort((a, b) => distance(a) - distance(b))[0];
    switch (direction) {
        case 'right':
            return nearest(links.filter((link) => link.from === selected).map((link) => link.to), (id) => Math.abs(at(id).y - here.y));
        case 'left':
            return nearest(links.filter((link) => link.to === selected).map((link) => link.from), (id) => Math.abs(at(id).y - here.y));
        default: {
            const sign = direction === 'down' ? 1 : -1;
            const stacked = ids.filter((id) => id !== selected && Math.abs(at(id).x - here.x) < column
                && sign * (at(id).y - here.y) > 0);
            return nearest(stacked, (id) => sign * (at(id).y - here.y));
        }
    }
}
