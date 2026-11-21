import type {QueryResult} from '../api/types';
import {columnText} from './format';
import {shortType} from './types';

const WIDTH_SAMPLE = 50;
const COLUMN_WIDTH = {least: 96, most: 380, perCharacter: 7.4, padding: 24};

export type GridSort = { column: number; desc: boolean } | null;

export function columnWidths(result: Pick<QueryResult, 'columns' | 'rows'>): number[] {
    return result.columns.map((column, index) => {
        const sample = result.rows.slice(0, WIDTH_SAMPLE).map((row) => columnText(row[index], column.type).length);
        const longest = Math.max(column.name.length + 2, shortType(column.type).length, ...sample);
        return Math.min(COLUMN_WIDTH.most, Math.max(COLUMN_WIDTH.least, longest * COLUMN_WIDTH.perCharacter + COLUMN_WIDTH.padding));
    });
}

export function nextSort(current: GridSort, column: number): GridSort {
    if (current?.column !== column) return {column, desc: false};
    return current.desc ? null : {column, desc: true};
}

export function sortLabel(column: string, sort: GridSort): string {
    if (!sort) return `${column}, sort ascending`;
    return sort.desc ? `${column}, sorted descending, clear sort` : `${column}, sorted ascending, sort descending`;
}

export function sortRows(rows: unknown[][], sort: GridSort): unknown[][] {
    if (!sort) return rows;
    const direction = sort.desc ? -1 : 1;
    return [...rows].sort((a, b) => compareValues(a[sort.column], b[sort.column]) * direction);
}

export function compareValues(a: unknown, b: unknown): number {
    if (a === b) return 0;
    if (a === null || a === undefined) return 1;
    if (b === null || b === undefined) return -1;
    if (typeof a === 'number' && typeof b === 'number') return a - b;
    return String(a).localeCompare(String(b));
}

export function prettyValue(value: unknown): string {
    if (typeof value === 'string' && (value.startsWith('{') || value.startsWith('['))) {
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch {
            return value;
        }
    }
    return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
}
