import {describe, expect, it} from 'vitest';
import {BEFORE_START, failurePlaces, TOWER} from './failurePlace';

const cell = (runId: string, flow: string, status: string, startedAt: string) => ({runId, flow, status, startedAt});

describe('failurePlaces', () => {
    const cells = [
        cell('a', 'silver', 'FAILED', '2026-11-06 10:02'),
        cell('a', 'bronze', 'COMPLETED', '2026-11-06 10:01'),
        cell('b', 'bronze', 'COMPLETED', '2026-11-06 11:01'),
    ];

    it('names the first flow that failed', () => {
        expect(failurePlaces(cells, ['a']).a).toBe('silver');
    });

    it('blames the tower when every flow finished', () => {
        expect(failurePlaces(cells, ['b']).b).toBe(TOWER);
    });

    it('says the run failed before any flow started', () => {
        expect(failurePlaces(cells, ['c']).c).toBe(BEFORE_START);
    });
});
