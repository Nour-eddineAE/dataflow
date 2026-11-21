import {describe, expect, it} from 'vitest';
import {columnWidths, compareValues, nextSort, prettyValue, sortLabel, sortRows} from './grid';

describe('nextSort', () => {
    it('cycles a column from ascending to descending to unsorted', () => {
        const ascending = nextSort(null, 2);
        const descending = nextSort(ascending, 2);

        expect(ascending).toEqual({column: 2, desc: false});
        expect(descending).toEqual({column: 2, desc: true});
        expect(nextSort(descending, 2)).toBeNull();
    });

    it('starts ascending when another column is chosen', () => {
        expect(nextSort({column: 0, desc: true}, 1)).toEqual({column: 1, desc: false});
    });
});

describe('sortLabel', () => {
    it('names the column, its order and what pressing does next', () => {
        expect(sortLabel('region', null)).toBe('region, sort ascending');
        expect(sortLabel('region', {column: 0, desc: false})).toBe('region, sorted ascending, sort descending');
        expect(sortLabel('region', {column: 0, desc: true})).toBe('region, sorted descending, clear sort');
    });
});

describe('sortRows', () => {
    const rows = [['b', 2], [null, 3], ['a', 10]];

    it('keeps the original rows when unsorted', () => {
        expect(sortRows(rows, null)).toBe(rows);
    });

    it('orders numbers numerically and puts nulls last when ascending', () => {
        expect(sortRows(rows, {column: 1, desc: false}).map((row) => row[1])).toEqual([2, 3, 10]);
        expect(sortRows(rows, {column: 0, desc: false}).map((row) => row[0])).toEqual(['a', 'b', null]);
    });

    it('reverses the order when descending without changing the input', () => {
        expect(sortRows(rows, {column: 1, desc: true}).map((row) => row[1])).toEqual([10, 3, 2]);
        expect(rows.map((row) => row[1])).toEqual([2, 3, 10]);
    });
});

describe('compareValues', () => {
    it('treats null and undefined as greater than any value', () => {
        expect(compareValues(null, 'a')).toBeGreaterThan(0);
        expect(compareValues(1, undefined)).toBeLessThan(0);
        expect(compareValues(null, null)).toBe(0);
    });
});

describe('columnWidths', () => {
    it('fits short columns to the least width and caps long ones', () => {
        const widths = columnWidths({
            columns: [{name: 'id', type: 'UInt8'}, {name: 'payload', type: 'String'}],
            rows: [[1, 'x'.repeat(200)]],
        });

        expect(widths).toEqual([96, 380]);
    });
});

describe('prettyValue', () => {
    it('indents JSON text and leaves other text as it is', () => {
        expect(prettyValue('{"a":1}')).toBe('{\n  "a": 1\n}');
        expect(prettyValue('{not json')).toBe('{not json');
        expect(prettyValue(null)).toBe('null');
    });
});
