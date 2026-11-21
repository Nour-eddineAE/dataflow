import type {FlowCell} from '../api/types';
import {parseTime} from './format';
import {rowChange} from './rowChange';

const GOLD = 'gold';
const ORDER = ['ingest', 'references', 'bronze', 'silver', 'history', GOLD, 'serving', 'maintenance'];
const KEY_SEPARATOR = '\u0000';

export type MatrixRun = { runId: string; startedAt: string };

export type RunMatrix = {
    runs: MatrixRun[];
    flows: string[];
    cell: (runId: string, flow: string) => FlowCell | undefined;
    longest: (flow: string) => number;
    mostRows: (flow: string) => number;
    rowsAlarm: (runId: string, flow: string) => string | undefined;
};

const epoch = (value: string) => parseTime(value)?.getTime() ?? 0;

function rank(flow: string): number {
    const known = ORDER.indexOf(flow);
    return known >= 0 ? known : ORDER.indexOf(GOLD);
}

export function flowOrder(a: string, b: string): number {
    return rank(a) - rank(b) || a.localeCompare(b);
}

export function buildMatrix(cells: FlowCell[]): RunMatrix {
    const starts = new Map<string, string>();
    const byKey = new Map<string, FlowCell>();
    const longest = new Map<string, number>();
    const mostRows = new Map<string, number>();
    for (const cell of cells) {
        const first = starts.get(cell.runId);
        if (!first || epoch(cell.startedAt) < epoch(first)) starts.set(cell.runId, cell.startedAt);
        byKey.set(`${cell.runId}${KEY_SEPARATOR}${cell.flow}`, cell);
        longest.set(cell.flow, Math.max(longest.get(cell.flow) ?? 0, cell.durationMs ?? 0));
        mostRows.set(cell.flow, Math.max(mostRows.get(cell.flow) ?? 0, cell.rowsOut ?? 0));
    }
    const runs = [...starts.entries()]
        .map(([runId, startedAt]) => ({runId, startedAt}))
        .sort((a, b) => epoch(a.startedAt) - epoch(b.startedAt));
    const cellOf = (runId: string, flow: string) => byKey.get(`${runId}${KEY_SEPARATOR}${flow}`);
    const rowsAlarm = (runId: string, flow: string) => {
        const now = cellOf(runId, flow)?.rowsOut;
        if (now == null) return undefined;
        const earlier = runs.slice(0, runs.findIndex((run) => run.runId === runId));
        const before = earlier.map((run) => cellOf(run.runId, flow)?.rowsOut).findLast((rows) => rows != null);
        if (before == null) return undefined;
        const change = rowChange(now, before);
        return change.alarming ? change.label : undefined;
    };
    return {
        runs,
        flows: [...longest.keys()].sort(flowOrder),
        cell: cellOf,
        longest: (flow) => longest.get(flow) ?? 0,
        mostRows: (flow) => mostRows.get(flow) ?? 0,
        rowsAlarm,
    };
}
