import {describe, expect, it} from 'vitest';
import {readableViewport} from './graphViewport';

const FIT = {minZoom: 0.5, maxZoom: 1, padding: 0.1, edge: 24};

describe('readableViewport', () => {
    it('centres a graph that fits', () => {
        expect(readableViewport({x: 0, y: 0, width: 400, height: 100}, 1000, 500, FIT)).toEqual({x: 300, y: 200, zoom: 1});
    });

    it('starts a graph too wide to read from its first block', () => {
        const viewport = readableViewport({x: 100, y: 0, width: 2000, height: 200}, 400, 600, FIT);

        expect(viewport.zoom).toBe(0.5);
        expect(viewport.x).toBe(24 - 100 * 0.5);
        expect(viewport.y).toBe((600 - 200 * 0.5) / 2);
    });

    it('starts a graph too tall to read from its top block', () => {
        const viewport = readableViewport({x: 0, y: 50, width: 200, height: 3000}, 800, 400, FIT);

        expect(viewport.zoom).toBe(0.5);
        expect(viewport.y).toBe(24 - 50 * 0.5);
    });
});
