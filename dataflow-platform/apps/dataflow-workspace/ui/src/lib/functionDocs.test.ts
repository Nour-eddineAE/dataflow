import {describe, expect, it} from 'vitest';
import {hoverText} from './functionDocs';

describe('hoverText', () => {
    it('keeps the syntax, the first paragraph and the return value, without tags or link targets', () => {
        expect(hoverText({
            name: 'uniqExact',
            syntax: 'uniqExact(x[, ...])',
            description: '\nCalculates the exact number of different argument values.\n\n<Warning>\nUses more memory than `uniq`.\n</Warning>',
            returns: 'Returns a UInt64. [`UInt64`](/reference/data-types/int-uint)',
        })).toEqual([
            '```sql\nuniqExact(x[, ...])\n```',
            'Calculates the exact number of different argument values.',
            '**Returns** Returns a UInt64. `UInt64`',
        ]);
    });

    it('skips what ClickHouse leaves empty', () => {
        expect(hoverText({
            name: 'plus',
            syntax: '',
            description: 'Adds two numbers.',
            returns: ''
        })).toEqual(['Adds two numbers.']);
    });
});
