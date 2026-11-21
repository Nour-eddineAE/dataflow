import {describe, expect, it} from 'vitest';
import type {RuleRun} from '../api/types';
import {isFailing, isPassing, ruleStatus} from './quality';

const run = (failing: number, evaluated: number): RuleRun => ({rule: 'magnitude_known', runId: 'r1', at: '2026-11-06 12:00:00', failing, evaluated});

describe('ruleStatus', () => {
    it('passes a rule that checked rows and found none failing', () => {
        expect(ruleStatus({action: 'drop', latest: run(0, 9_840)})).toBe('PASSED');
    });

    it('fails or warns by the rule\'s action', () => {
        expect(ruleStatus({action: 'drop', latest: run(12, 9_840)})).toBe('FAILED');
        expect(ruleStatus({action: 'warn', latest: run(12, 9_840)})).toBe('WARNING');
    });

    it('does not count a rule that checked nothing as passing', () => {
        expect(ruleStatus({action: 'fail', latest: run(0, 0)})).toBe('UNKNOWN');
        expect(ruleStatus({action: 'fail'})).toBe('UNKNOWN');
    });
});

describe('rule filters', () => {
    it('counts warnings as failing and unchecked rules as neither', () => {
        expect(isFailing({action: 'warn', latest: run(3, 100)})).toBe(true);
        expect(isPassing({action: 'drop', latest: run(0, 100)})).toBe(true);
        expect([isFailing({action: 'fail', latest: run(0, 0)}), isPassing({action: 'fail', latest: run(0, 0)})]).toEqual([false, false]);
    });
});

