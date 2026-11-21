import type {QueryResult} from '../api/types';
import {downloadText} from './download';
import {cellText} from './format';

function escape(value: unknown) {
    if (value === null || value === undefined) return '';
    const text = cellText(value);
    return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv(result: Pick<QueryResult, 'columns' | 'rows'>) {
    const header = result.columns.map((column) => escape(column.name)).join(',');
    return [header, ...result.rows.map((row) => row.map(escape).join(','))].join('\n');
}

export function downloadCsv(name: string, result: Pick<QueryResult, 'columns' | 'rows'>) {
    downloadText(name, 'csv', toCsv(result), 'text/csv');
}
