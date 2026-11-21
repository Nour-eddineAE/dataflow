import {describe, expect, it} from 'vitest';
import {isTableLine} from './outputLine';

describe('isTableLine', () => {
    it('recognises the borders and rows Spark prints for a table', () => {
        expect(isTableLine('+------+-----+')).toBe(true);
        expect(isTableLine('|region|count|')).toBe(true);
        expect(isTableLine('  | Alaska |  12|')).toBe(true);
    });

    it('leaves ordinary log lines to wrap', () => {
        expect(isTableLine('Wrote 120 rows to silver')).toBe(false);
        expect(isTableLine('a | b')).toBe(false);
        expect(isTableLine('+1 retry')).toBe(false);
    });
});
