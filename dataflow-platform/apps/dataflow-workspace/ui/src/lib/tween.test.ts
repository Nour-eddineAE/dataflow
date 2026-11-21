import {describe, expect, it} from 'vitest';
import {between, ease} from './tween';

describe('tween', () => {
    it('eases in and out', () => {
        expect(ease(0)).toBe(0);
        expect(ease(1)).toBe(1);
        expect(ease(0.5)).toBe(0.5);
        expect(ease(0.1)).toBeLessThan(0.1);
        expect(ease(0.9)).toBeGreaterThan(0.9);
    });

    it('moves every node part of the way and lands exactly', () => {
        const from = {a: {x: 0, y: 0}};
        const to = {a: {x: 100, y: 40}, b: {x: 10, y: 10}};
        expect(between(from, to, 0.5)).toEqual({a: {x: 50, y: 20}, b: {x: 10, y: 10}});
        expect(between(from, to, 2)).toEqual(to);
    });
});
