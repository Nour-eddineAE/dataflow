import {describe, expect, it} from 'vitest';
import {countStatuses, statusTone} from './status';

describe('statusTone', () => {
    it('shows a paused schedule as a warning', () => {
        expect(statusTone('PAUSED')).toEqual({tone: 'warning', label: 'Paused'});
    });

    it('names an unknown status in sentence case', () => {
        expect(statusTone('ARCHIVED')).toEqual({tone: 'neutral', label: 'Archived'});
    });
});

describe('countStatuses', () => {
    it('counts statuses by label, the ones needing attention first', () => {
        expect(countStatuses(['MATERIALIZED', 'MISSING', 'FAILED', 'MATERIALIZED', 'SUCCESS'])).toEqual([
            {status: 'FAILED', label: 'Failed', count: 1},
            {status: 'MISSING', label: 'Not run', count: 1},
            {status: 'MATERIALIZED', label: 'Done', count: 2},
            {status: 'SUCCESS', label: 'Succeeded', count: 1},
        ]);
    });
});
