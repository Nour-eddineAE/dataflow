import {describe, expect, it} from 'vitest';
import {runFailure} from './runFailure';

const TOWER_ERROR = 'dagster._core.errors.DagsterInvariantViolationError: Compute function for op "x" yielded a list';

describe('runFailure', () => {
    it('names the first flow that failed', () => {
        const failure = runFailure([{flow: 'bronze', status: 'SUCCESS'}, {flow: 'silver', status: 'FAILURE'}], 'boom');

        expect(failure).toEqual({title: 'Failed in silver', exception: undefined, inTower: false});
    });

    it('blames the tower when every flow finished', () => {
        const failure = runFailure([{flow: 'bronze', status: 'SUCCESS'}, {flow: 'silver', status: 'SUCCESS'}], TOWER_ERROR);

        expect(failure.title).toBe('All 2 flows finished; the run failed in the tower');
        expect(failure.inTower).toBe(true);
    });

    it('keeps only the exception class name', () => {
        expect(runFailure([], TOWER_ERROR).exception).toBe('DagsterInvariantViolationError');
    });

    it('says when no flow started', () => {
        expect(runFailure([], 'Run launch failed').title).toBe('Failed before any flow started');
    });
});
