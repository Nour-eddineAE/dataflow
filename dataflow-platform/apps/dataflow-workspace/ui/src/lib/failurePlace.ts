import type {FlowCell} from '../api/types';
import {statusTone} from './status';

export const TOWER = 'Tower';
export const BEFORE_START = 'Before start';

type Flow = { flow: string; status: string; startedAt?: string };

export function failurePlace(flows: Flow[]): string {
    const ordered = [...flows].sort((a, b) => (a.startedAt ?? '').localeCompare(b.startedAt ?? ''));
    const failed = ordered.find((flow) => statusTone(flow.status).tone === 'danger');
    return failed ? failed.flow : flows.length ? TOWER : BEFORE_START;
}

export function failurePlaces(cells: FlowCell[], runIds: string[]): Record<string, string> {
    const byRun = new Map<string, FlowCell[]>();
    cells.forEach((cell) => byRun.set(cell.runId, [...(byRun.get(cell.runId) ?? []), cell]));
    return Object.fromEntries(runIds.map((runId) => [runId, failurePlace(byRun.get(runId) ?? [])]));
}
