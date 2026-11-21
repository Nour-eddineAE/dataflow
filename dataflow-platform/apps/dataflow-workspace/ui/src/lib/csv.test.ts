import {describe, expect, it} from 'vitest';
import {toCsv} from './csv';

describe('toCsv', () => {
    it('quotes values with commas, quotes and line breaks, and leaves nulls empty', () => {
        const csv = toCsv({
            columns: [{name: 'region', type: 'String'}, {name: 'events', type: 'UInt64'}],
            rows: [['Alaska, USA', 3392], ['say "hi"', null], ['two\nlines', 1]],
        });

        expect(csv).toBe('region,events\n"Alaska, USA",3392\n"say ""hi""",\n"two\nlines",1');
    });
});
