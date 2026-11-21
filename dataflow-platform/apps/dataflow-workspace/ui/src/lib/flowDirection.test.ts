import {describe, expect, it} from 'vitest';
import {extent, readableDirection} from './flowDirection';

const NODE = {width: 200, height: 52};

describe('extent', () => {
    it('reaches the far edge of the last node', () => {
        expect(extent({a: {x: 8, y: 8}, b: {x: 248, y: 76}}, NODE)).toEqual({width: 448, height: 128});
    });
});

describe('readableDirection', () => {
    it('stays left to right when the graph fits across', () => {
        expect(readableDirection({width: 900, height: 300}, {width: 600, height: 700}, {width: 1000, height: 500})).toBe('LR');
    });

    it('turns top down when a long chain would shrink across', () => {
        expect(readableDirection({width: 1500, height: 280}, {width: 860, height: 560}, {width: 970, height: 480})).toBe('TB');
    });

    it('keeps left to right when top down is only a little larger', () => {
        expect(readableDirection({width: 1100, height: 300}, {width: 600, height: 520}, {width: 1000, height: 500})).toBe('LR');
    });
});
