import {describe, expect, it} from 'vitest';
import {changeSummary, labelChanges, versionRangeTitle} from './versionRange';

describe('versionRange', () => {
    it('names one version or a range', () => {
        expect(versionRangeTitle({from: 3, to: 3})).toBe('Changes in version 3');
        expect(versionRangeTitle({from: 3, to: 7})).toBe('Changes from version 3 to 7');
    });

    it('adds each kind of change across the versions and leaves out preimages', () => {
        expect(changeSummary([
            {change: 'insert', rows: 2},
            {change: 'update_preimage', rows: 1},
            {change: 'update_postimage', rows: 1},
            {change: 'insert', rows: 5},
        ])).toEqual([{label: 'inserted', rows: 7}, {label: 'closed', rows: 1}]);
    });

    it('has nothing to say when no row changed', () => {
        expect(changeSummary([])).toEqual([]);
    });

    it('names each row change in words', () => {
        const result = {
            columns: [{name: 'change', type: 'String'}, {name: 'region', type: 'String'}],
            rows: [['insert', 'update_preimage'], ['update_postimage', 'x'], ['delete', 'y'], ['merge', 'z']],
            truncated: false, elapsedMs: 0, rowsRead: 0, bytesRead: 0,
        };
        expect(labelChanges(result).rows).toEqual([['Inserted', 'update_preimage'], ['Closed', 'x'], ['Deleted', 'y'], ['merge', 'z']]);
    });
});
