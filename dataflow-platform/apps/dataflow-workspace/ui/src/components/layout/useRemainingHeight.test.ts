import {describe, expect, it} from 'vitest';
import {remainingHeight} from './useRemainingHeight';

describe('remainingHeight', () => {
    it('fills the window below the element, leaving the page foot', () => {
        expect(remainingHeight(900, 300)).toBe(544);
    });

    it('keeps a readable floor on a short window', () => {
        expect(remainingHeight(600, 400)).toBe(380);
    });
});
