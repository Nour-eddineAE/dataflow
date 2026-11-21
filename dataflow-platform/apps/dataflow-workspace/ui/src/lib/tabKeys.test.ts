import {describe, expect, it} from 'vitest';
import {tabAfterKey} from './tabKeys';

const TABS = [{value: 'schema'}, {value: 'saved', disabled: true}, {value: 'recent'}];

describe('tabAfterKey', () => {
    it('moves right past a disabled tab', () => {
        expect(tabAfterKey(TABS, 'schema', 'ArrowRight')).toBe('recent');
    });

    it('wraps around at either end', () => {
        expect(tabAfterKey(TABS, 'recent', 'ArrowRight')).toBe('schema');
        expect(tabAfterKey(TABS, 'schema', 'ArrowLeft')).toBe('recent');
    });

    it('jumps to the first and last tab', () => {
        expect(tabAfterKey(TABS, 'recent', 'Home')).toBe('schema');
        expect(tabAfterKey(TABS, 'schema', 'End')).toBe('recent');
    });

    it('ignores other keys', () => {
        expect(tabAfterKey(TABS, 'schema', 'Enter')).toBeUndefined();
    });
});
