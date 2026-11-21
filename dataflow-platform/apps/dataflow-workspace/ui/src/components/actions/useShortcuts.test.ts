import {describe, expect, it} from 'vitest';
import {shortcutOf} from './useShortcuts';

describe('shortcutOf', () => {
    it('names letters in upper case', () => {
        expect(shortcutOf({key: 's', shiftKey: false})).toBe('Mod+S');
    });

    it('names shifted keys', () => {
        expect(shortcutOf({key: 'Z', shiftKey: true})).toBe('Mod+Shift+Z');
    });

    it('keeps named keys', () => {
        expect(shortcutOf({key: 'Enter', shiftKey: false})).toBe('Mod+Enter');
    });
});
