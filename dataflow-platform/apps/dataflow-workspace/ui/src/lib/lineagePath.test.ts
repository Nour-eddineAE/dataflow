import {describe, expect, it} from 'vitest';
import {lineagePath} from './lineagePath';

const edge = (from: string, to: string) => {
    const [fromTable, fromColumn] = from.split('|');
    const [toTable, toColumn] = to.split('|');
    return {fromTable, fromColumn, toTable, toColumn};
};

const EDGES = [
    edge('bronze|place', 'silver|region'),
    edge('silver|region', 'current|region'),
    edge('current|region', 'gold.regions|region'),
    edge('bronze|mag', 'silver|magnitude'),
];

describe('lineagePath', () => {
    it('follows a column up to its sources and down to its consumers', () => {
        expect([...lineagePath(EDGES, 'silver|region')].sort())
            .toEqual(['bronze|place', 'current|region', 'gold.regions|region', 'silver|region']);
    });

    it('lights nothing without a column', () => {
        expect(lineagePath(EDGES).size).toBe(0);
    });
});
