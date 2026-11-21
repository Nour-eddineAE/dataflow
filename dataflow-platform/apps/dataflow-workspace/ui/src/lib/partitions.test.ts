import {describe, expect, it} from 'vitest';
import {newestNotRun, notRun} from './partitions';

const PARTITIONS = [
    {key: '2026-08', status: 'MISSING'},
    {key: '2026-09', status: 'FAILED'},
    {key: '2026-10', status: 'MATERIALIZED'},
];

describe('partitions', () => {
    it('lists every partition without a successful run', () => {
        expect(notRun(PARTITIONS)).toEqual(['2026-08', '2026-09']);
    });

    it('suggests the newest partition that has not run', () => {
        expect(newestNotRun(PARTITIONS)).toBe('2026-09');
        expect(newestNotRun([{key: '2026-10', status: 'MATERIALIZED'}])).toBeUndefined();
    });
});
