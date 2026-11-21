import {describe, expect, it} from 'vitest';
import {arrange, follow, NODE_WIDTH, place, placeBlocks, renamedIds, withoutNode} from './designerPositions';
import {designGraph, type Spec} from './specModel';

const SPEC: Spec = {
    dataset: 'x',
    inputs: {trips: 'taxi_trips.current'},
    gold: [{name: 'daily', from: 'trips', steps: []}],
    serve: [{from: 'gold.daily', table: 'x'}],
};

describe('designer positions', () => {
    it('lays the flow out from left to right', () => {
        const {nodes, edges} = designGraph({dataset: 'x', source: {url: 'u', format: 'csv'}, serve: [{from: 'current', table: 'x'}]});
        const positions = arrange(nodes, edges);

        const order = ['source', 'bronze', 'silver', 'history', 'serve:x'].map((id) => positions[id].x);
        expect(order).toEqual([...order].sort((a, b) => a - b));
        expect(order[1] - order[0]).toBeGreaterThan(NODE_WIDTH);
    });

    it('places a new block beside what it reads and leaves the others where they are', () => {
        const before = designGraph(SPEC);
        const known = arrange(before.nodes, before.edges);
        const next = designGraph({...SPEC, gold: [...SPEC.gold!, {name: 'weekly', from: 'daily', steps: []}]});

        const placed = place(next.nodes, next.edges, known);

        Object.entries(known).forEach(([id, point]) => expect(placed[id]).toEqual(point));
        expect(placed['gold:weekly'].x).toBeGreaterThan(placed['gold:daily'].x);
    });

    it('puts a dragged block back where it was placed when its position is undone', () => {
        const {nodes, edges} = designGraph(SPEC);
        const opened = placeBlocks({nodes: [], automatic: {}}, nodes, edges, {});
        const dragged = placeBlocks(opened, nodes, edges, {'gold:daily': {x: 900, y: 900}});

        const undone = placeBlocks(dragged, nodes, edges, {});

        expect(dragged.layout['gold:daily']).toEqual({x: 900, y: 900});
        expect(undone.layout).toEqual(opened.layout);
    });

    it('keeps blocks still when another is added after a drag', () => {
        const before = designGraph(SPEC);
        const dragged = placeBlocks(placeBlocks({nodes: [], automatic: {}}, before.nodes, before.edges, {}),
            before.nodes, before.edges, {'serve:x': {x: 900, y: 0}});
        const next = designGraph({...SPEC, gold: [...SPEC.gold!, {name: 'weekly', from: 'daily', steps: []}]});

        const added = placeBlocks(dragged, next.nodes, next.edges, dragged.positions);

        before.nodes.forEach((node) => expect(added.layout[node.id]).toEqual(dragged.layout[node.id]));
        expect(added.layout['gold:weekly'].x).toBeGreaterThan(added.layout['gold:daily'].x);
    });

    it('keeps a position when its block is renamed, and forgets a removed one', () => {
        const before = designGraph(SPEC).nodes;
        const after = designGraph({...SPEC, gold: [{name: 'per_day', from: 'trips', steps: []}]}).nodes;
        const renamed = renamedIds(before, after);

        expect(renamed.get('gold:daily')).toBe('gold:per_day');
        expect(follow({'gold:daily': {x: 5, y: 6}}, renamed)).toEqual({'gold:per_day': {x: 5, y: 6}});
        expect(withoutNode({'gold:daily': {x: 1, y: 1}, 'serve:x': {x: 2, y: 2}}, before.find((node) => node.id === 'gold:daily')!))
            .toEqual({'serve:x': {x: 2, y: 2}});
    });
});
