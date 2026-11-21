import {describe, expect, it} from 'vitest';
import {barScale, nextBar} from './chartKeys';

describe('barScale', () => {
    it('scales to the 90th percentile so one slow run does not flatten the rest', () => {
        expect(barScale([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 600])).toBe(10);
    });

    it('scales to the longest when there are only a few runs', () => {
        expect(barScale([3, 9, 4])).toBe(9);
    });

    it('never scales to zero', () => {
        expect(barScale([0, 0])).toBe(1);
    });
});

describe('nextBar', () => {
    const PRESENT = [
        [true, false, true],
        [true, true, false],
    ];

    it('skips a run that has no bar in this flow', () => {
        expect(nextBar(PRESENT, {row: 0, col: 0}, 'ArrowRight')).toEqual({row: 0, col: 2});
    });

    it('moves between flows in the same run', () => {
        expect(nextBar(PRESENT, {row: 0, col: 0}, 'ArrowDown')).toEqual({row: 1, col: 0});
    });

    it('stays put at an edge', () => {
        expect(nextBar(PRESENT, {row: 0, col: 2}, 'ArrowRight')).toBeUndefined();
        expect(nextBar(PRESENT, {row: 0, col: 2}, 'ArrowDown')).toBeUndefined();
    });

    it('jumps to the first and last bar of the row', () => {
        expect(nextBar(PRESENT, {row: 1, col: 1}, 'Home')).toEqual({row: 1, col: 0});
        expect(nextBar(PRESENT, {row: 0, col: 0}, 'End')).toEqual({row: 0, col: 2});
    });
});
