import type {QueryResult} from '../api/types';

const PREFIX = 'df.sql.result.';
export const KEPT_ROWS = 1_000;

const keyOf = (tab: string) => PREFIX + tab;

export function keepResult(store: Storage, tab: string, result: QueryResult) {
    const kept = result.rows.length > KEPT_ROWS ? {...result, rows: result.rows.slice(0, KEPT_ROWS), truncated: true} : result;
    try {
        store.setItem(keyOf(tab), JSON.stringify(kept));
    } catch (error) {
        if (!(error instanceof DOMException)) throw error;
        store.removeItem(keyOf(tab));
    }
}

export function keptResults(store: Storage, tabs: string[]): Record<string, QueryResult> {
    return Object.fromEntries(tabs.flatMap((tab) => {
        const raw = store.getItem(keyOf(tab));
        if (!raw) return [];
        try {
            return [[tab, JSON.parse(raw) as QueryResult]];
        } catch (error) {
            if (!(error instanceof SyntaxError)) throw error;
            store.removeItem(keyOf(tab));
            return [];
        }
    }));
}

export function forgetResult(store: Storage, tab: string) {
    store.removeItem(keyOf(tab));
}

export function forgetClosed(store: Storage, open: string[]) {
    const kept = new Set(open.map(keyOf));
    const stale = Array.from({length: store.length}, (_, index) => store.key(index))
        .filter((key): key is string => !!key && key.startsWith(PREFIX) && !kept.has(key));
    stale.forEach((key) => store.removeItem(key));
}
