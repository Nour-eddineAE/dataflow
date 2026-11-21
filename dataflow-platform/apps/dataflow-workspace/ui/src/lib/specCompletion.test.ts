import {describe, expect, it} from 'vitest';
import {specDoc} from './specCompletion';

describe('specDoc', () => {
    const line = '  schema-evolution: rescue';

    it('explains a key under the cursor, hyphens included', () => {
        expect(specDoc(line, 5)).toEqual({text: 'rescue or add-new-columns', start: 3, end: 19});
    });

    it('explains a known value', () => {
        expect(specDoc(line, 23)?.text).toBe('Unknown columns and values that do not cast go to _rescued_data');
        expect(specDoc('    - { name: x, check: "a > 0", on-failure: drop }', 47)?.text).toBe('Send the row to quarantine');
    });

    it('says nothing about free text', () => {
        expect(specDoc('owner: platform', 10)).toBeUndefined();
    });
});
