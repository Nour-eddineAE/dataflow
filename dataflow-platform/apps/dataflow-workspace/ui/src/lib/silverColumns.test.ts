import {describe, expect, it} from 'vitest';
import {addSilverColumn} from './silverColumns';

const COLUMN = `- { name: flag, type: string, from: "get_json_object(_rescued_data, '$.flag')" }`;

const SPEC = [
    'dataset: quakes',
    'silver:',
    '  # kept as written',
    '  columns:',
    '    - { name: id, type: string, from: id }',
    '    - name: mag',
    '      type: double',
    '',
    '  expectations:',
    '    - { name: known, check: "id IS NOT NULL" }',
    'gold:',
    '  - { name: daily, sql: "SELECT 1" }',
    '',
].join('\n');

describe('addSilverColumn', () => {
    it('adds the line after the last column and leaves the rest as written', () => {
        const result = addSilverColumn(SPEC, COLUMN);
        expect(result).toEqual({
            text: SPEC.replace('      type: double\n', `      type: double\n    ${COLUMN}\n`),
        });
    });

    it('handles columns at the end of the file without a final newline', () => {
        const spec = 'silver:\n  columns:\n    - { name: id, type: string }';
        expect(addSilverColumn(spec, COLUMN)).toEqual({text: `${spec}\n    ${COLUMN}`});
    });

    it('accepts list items at the indent of the key', () => {
        const spec = 'silver:\n  columns:\n  - { name: id, type: string }\n  derive:\n  - { name: a, type: int, from: "1" }\n';
        expect(addSilverColumn(spec, COLUMN)).toEqual({
            text: `silver:\n  columns:\n  - { name: id, type: string }\n  ${COLUMN}\n  derive:\n  - { name: a, type: int, from: "1" }\n`,
        });
    });

    it('refuses a column already declared', () => {
        expect(addSilverColumn('silver:\n  columns:\n    - { name: flag, type: string }\n', COLUMN))
            .toEqual({problem: 'flag is already in silver.columns'});
    });

    it('refuses a spec it cannot edit by line', () => {
        expect(addSilverColumn('dataset: quakes\n', COLUMN)).toEqual({problem: 'The spec has no silver block'});
        expect(addSilverColumn('silver:\n  columns: [{ name: id, type: string }]\n', COLUMN))
            .toEqual({problem: 'silver.columns is not a block list'});
    });
});
