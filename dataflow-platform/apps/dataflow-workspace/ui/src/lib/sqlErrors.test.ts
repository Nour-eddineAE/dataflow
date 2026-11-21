import {describe, expect, it} from 'vitest';
import {errorRange, suggestions} from './sqlErrors';

const slice = (sql: string, message: string) => {
    const range = errorRange(message, sql);
    return range && sql.slice(range.start, range.end);
};

describe('errorRange', () => {
    it('points at the line and column ClickHouse reports', () => {
        const sql = 'SELECT region,\n  count( FROM earthquakes.current';
        expect(slice(sql, 'Syntax error: failed at position 30 (earthquakes) (line 2, col 15): earthquakes.current.'))
            .toBe('earthquakes.current');
    });

    it('counts from the first non-blank character, as the server strips the statement', () => {
        expect(slice('\n\n  SELEC 1', 'Syntax error: failed at position 1 (SELEC) (line 1, col 1)')).toBe('SELEC');
    });

    it('finds an unknown identifier by name', () => {
        expect(slice('SELECT regionx FROM earthquakes.current', 'Unknown expression identifier `regionx`. Maybe you meant: [\'region\'].'))
            .toBe('regionx');
        expect(slice('SELECT * FROM nope', "Unknown table expression identifier 'nope'.")).toBe('nope');
    });

    it('gives up when the message says nothing about where', () => {
        expect(errorRange('Memory limit exceeded', 'SELECT 1')).toBeUndefined();
        expect(errorRange('failed at (line 9, col 1)', 'SELECT 1')).toBeUndefined();
    });
});

describe('suggestions', () => {
    it('reads the names ClickHouse proposes', () => {
        expect(suggestions("Unknown expression identifier `regionx`. Maybe you meant: ['region','regions']. (UNKNOWN_IDENTIFIER)"))
            .toEqual(['region', 'regions']);
        expect(suggestions('Syntax error')).toEqual([]);
    });
});
