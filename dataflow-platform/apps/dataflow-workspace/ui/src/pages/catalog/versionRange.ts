import type {QueryResult} from '../../api/types';

export type VersionRange = { from: number; to: number };

const CHANGE_LABELS: Record<string, string> = {insert: 'inserted', update_postimage: 'closed', delete: 'deleted'};

export function versionRangeTitle({from, to}: VersionRange) {
    return from === to ? `Changes in version ${from}` : `Changes from version ${from} to ${to}`;
}

export function changeSummary(counts: { change: string; rows: number }[]) {
    return Object.entries(CHANGE_LABELS).flatMap(([change, label]) => {
        const matching = counts.filter((count) => count.change === change);
        return matching.length ? [{label, rows: matching.reduce((sum, count) => sum + count.rows, 0)}] : [];
    });
}

const CHANGE_COLUMN = 'change';
const ROW_CHANGES: Record<string, string> = {
    insert: 'Inserted', update_preimage: 'Before closing', update_postimage: 'Closed', delete: 'Deleted',
};

export function labelChanges(result: QueryResult): QueryResult {
    const column = result.columns.findIndex((entry) => entry.name === CHANGE_COLUMN);
    if (column < 0) return result;
    return {
        ...result,
        rows: result.rows.map((row) => row.map((value, index) =>
            (index === column && typeof value === 'string' ? ROW_CHANGES[value] ?? value : value))),
    };
}
