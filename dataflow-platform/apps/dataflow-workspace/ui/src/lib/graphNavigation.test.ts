import {describe, expect, it} from 'vitest';
import {neighbour} from './graphNavigation';

const IDS = ['input:a', 'input:b', 'gold:joined', 'gold:top', 'serve:t'];
const LINKS = [
    {from: 'input:a', to: 'gold:joined'},
    {from: 'input:b', to: 'gold:joined'},
    {from: 'gold:joined', to: 'gold:top'},
    {from: 'gold:joined', to: 'serve:t'},
];
const POSITIONS = {
    'input:a': {x: 0, y: 0},
    'input:b': {x: 0, y: 100},
    'gold:joined': {x: 300, y: 50},
    'gold:top': {x: 600, y: 0},
    'serve:t': {x: 600, y: 120},
};
const COLUMN = 150;
const GRAPH = {ids: IDS, links: LINKS, positions: POSITIONS};

describe('neighbour', () => {
    it('follows edges left and right, preferring the block level with the current one', () => {
        expect(neighbour(GRAPH, 'gold:joined', 'right', COLUMN)).toBe('gold:top');
        expect(neighbour(GRAPH, 'serve:t', 'left', COLUMN)).toBe('gold:joined');
        expect(neighbour(GRAPH, 'gold:joined', 'left', COLUMN)).toBe('input:a');
        expect(neighbour(GRAPH, 'gold:top', 'right', COLUMN)).toBeUndefined();
    });

    it('moves up and down within a column', () => {
        expect(neighbour(GRAPH, 'input:a', 'down', COLUMN)).toBe('input:b');
        expect(neighbour(GRAPH, 'serve:t', 'up', COLUMN)).toBe('gold:top');
        expect(neighbour(GRAPH, 'input:b', 'down', COLUMN)).toBeUndefined();
    });

    it('starts from the first block when nothing is selected', () => {
        expect(neighbour(GRAPH, 'pipeline', 'right', COLUMN)).toBe('input:a');
    });
});
