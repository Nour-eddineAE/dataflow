import {describe, expect, it} from 'vitest';
import type {FlowCell} from '../api/types';
import {buildMatrix, flowOrder} from './runMatrix';

const cell = (runId: string, flow: string, startedAt: string, durationMs: number | null, status = 'COMPLETED', rowsOut: number | null = 1): FlowCell =>
    ({runId, flow, status, startedAt, durationMs, rowsOut});

describe('run matrix', () => {
    it('orders flows as a run executes them and runs from oldest to newest', () => {
        const matrix = buildMatrix([
            cell('new', 'serving', '2026-10-30 10:05:00.000000', 300),
            cell('new', 'gold.daily', '2026-10-30 10:04:00.000000', 900),
            cell('new', 'bronze', '2026-10-30 10:01:00.000000', 1200),
            cell('old', 'silver', '2026-10-29 10:02:00.000000', 4000, 'FAILED'),
            cell('old', 'ingest', '2026-10-29 10:00:00.000000', 100),
            cell('new', 'ingest', '2026-10-30 10:00:00.000000', 200),
            cell('new', 'maintenance', '2026-10-30 10:06:00.000000', null),
        ]);

        expect(matrix.runs.map((run) => run.runId)).toEqual(['old', 'new']);
        expect(matrix.runs[1].startedAt).toBe('2026-10-30 10:00:00.000000');
        expect(matrix.flows).toEqual(['ingest', 'bronze', 'silver', 'gold.daily', 'serving', 'maintenance']);
        expect(matrix.cell('old', 'silver')?.status).toBe('FAILED');
        expect(matrix.cell('old', 'bronze')).toBeUndefined();
        expect(matrix.longest('ingest')).toBe(200);
        expect(matrix.longest('maintenance')).toBe(0);
    });

    it('sorts gold tables by name between history and serving', () => {
        expect(['serving', 'gold.b', 'history', 'gold.a', 'references'].sort(flowOrder))
            .toEqual(['references', 'history', 'gold.a', 'gold.b', 'serving']);
    });

    it('flags rows down a fifth or more against the last run that reported rows', () => {
        const matrix = buildMatrix([
            cell('a', 'silver', '2026-10-28 10:00:00.000000', 1, 'COMPLETED', 1000),
            cell('b', 'silver', '2026-10-29 10:00:00.000000', 1, 'FAILED', null),
            cell('c', 'silver', '2026-10-30 10:00:00.000000', 1, 'COMPLETED', 700),
            cell('d', 'silver', '2026-10-31 10:00:00.000000', 1, 'COMPLETED', 690),
        ]);

        expect(matrix.mostRows('silver')).toBe(1000);
        expect(matrix.rowsAlarm('a', 'silver')).toBeUndefined();
        expect(matrix.rowsAlarm('b', 'silver')).toBeUndefined();
        expect(matrix.rowsAlarm('c', 'silver')).toBeDefined();
        expect(matrix.rowsAlarm('d', 'silver')).toBeUndefined();
    });

    it('is empty without cells', () => {
        expect(buildMatrix([])).toMatchObject({runs: [], flows: []});
    });
});
