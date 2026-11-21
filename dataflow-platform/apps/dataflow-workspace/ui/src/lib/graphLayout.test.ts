import {describe, expect, it} from 'vitest';
import {type Box, layered} from './graphLayout';

const box = (id: string, height = 50): Box => ({id, width: 100, height});
const BOXES = [box('a'), box('b'), box('c'), box('d', 120)];
const LINKS = [{from: 'a', to: 'b'}, {from: 'a', to: 'c'}, {from: 'b', to: 'd'}];

describe('layered layout', () => {
    it('puts each rank in its own column, left to right', () => {
        const positions = layered(BOXES, LINKS, {direction: 'LR'});
        expect(positions.a.x).toBeLessThan(positions.b.x);
        expect(positions.b.x).toBe(positions.c.x);
        expect(positions.b.x).toBeLessThan(positions.d.x);
    });

    it('keeps nodes of one rank apart', () => {
        const positions = layered(BOXES, LINKS, {direction: 'LR', nodeGap: 20});
        expect(Math.abs(positions.b.y - positions.c.y)).toBeGreaterThanOrEqual(70);
    });

    it('centres a lone node against the widest rank', () => {
        const positions = layered(BOXES, LINKS, {direction: 'TB', nodeGap: 20});
        const middle = (positions.b.x + positions.c.x) / 2;
        expect(positions.a.x).toBe(middle);
    });

    it('aligns ranks to the top when asked', () => {
        const positions = layered(BOXES, LINKS, {direction: 'LR', align: 'start', margin: 0});
        expect(positions.a.y).toBe(0);
        expect(Math.min(positions.b.y, positions.c.y)).toBe(0);
    });

    it('lands on a 4 px grid', () => {
        Object.values(layered(BOXES, LINKS, {direction: 'TB'})).forEach((point) => {
            expect(point.x % 4).toBe(0);
            expect(point.y % 4).toBe(0);
        });
    });
});
