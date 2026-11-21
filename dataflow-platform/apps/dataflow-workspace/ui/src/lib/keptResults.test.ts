import {describe, expect, it} from 'vitest';
import type {QueryResult} from '../api/types';
import {forgetClosed, forgetResult, KEPT_ROWS, keepResult, keptResults} from './keptResults';

class MemoryStorage implements Storage {
    private readonly entries = new Map<string, string>();
    get length() { return this.entries.size; }
    clear() { this.entries.clear(); }
    getItem(key: string) { return this.entries.get(key) ?? null; }
    key(index: number) { return [...this.entries.keys()][index] ?? null; }
    removeItem(key: string) { this.entries.delete(key); }
    setItem(key: string, value: string) { this.entries.set(key, value); }
}

const result = (rows: number): QueryResult => ({
    columns: [{name: 'n', type: 'UInt32'}],
    rows: Array.from({length: rows}, (_, index) => [index]),
    truncated: false,
    elapsedMs: 12,
    rowsRead: rows,
    bytesRead: rows * 4,
});

describe('kept query results', () => {
    it('brings a tab\'s last result back after a reload', () => {
        const store = new MemoryStorage();
        keepResult(store, 'tab-1', result(3));
        expect(keptResults(store, ['tab-1', 'tab-2'])).toEqual({'tab-1': result(3)});
    });

    it('keeps at most the first thousand rows and says the rest was cut', () => {
        const store = new MemoryStorage();
        keepResult(store, 'tab-1', result(KEPT_ROWS + 5));
        const kept = keptResults(store, ['tab-1'])['tab-1'];
        expect(kept.rows).toHaveLength(KEPT_ROWS);
        expect(kept.truncated).toBe(true);
    });

    it('forgets a failed tab and every closed one', () => {
        const store = new MemoryStorage();
        ['tab-1', 'tab-2', 'tab-3'].forEach((tab) => keepResult(store, tab, result(1)));
        store.setItem('df.sql.tabs', '[]');
        forgetResult(store, 'tab-1');
        forgetClosed(store, ['tab-1', 'tab-2']);
        expect(Object.keys(keptResults(store, ['tab-1', 'tab-2', 'tab-3']))).toEqual(['tab-2']);
        expect(store.getItem('df.sql.tabs')).toBe('[]');
    });

    it('drops a stored result it cannot read', () => {
        const store = new MemoryStorage();
        store.setItem('df.sql.result.tab-1', '{broken');
        expect(keptResults(store, ['tab-1'])).toEqual({});
        expect(store.length).toBe(0);
    });
});
