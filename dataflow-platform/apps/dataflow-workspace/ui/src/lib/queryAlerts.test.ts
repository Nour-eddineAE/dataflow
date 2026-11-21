import {describe, expect, it} from 'vitest';
import type {SavedQuery} from '../api/types';
import {alertCandidates, describeAlert, intervalLabel, needsThreshold} from './queryAlerts';

describe('query alerts', () => {
    it('asks for a value only when comparing one', () => {
        expect(needsThreshold('ABOVE')).toBe(true);
        expect(needsThreshold('EQUAL')).toBe(true);
        expect(needsThreshold('ANY_ROWS')).toBe(false);
        expect(needsThreshold('NO_ROWS')).toBe(false);
    });

    it('says when an alert fires', () => {
        expect(describeAlert({comparison: 'ABOVE', threshold: 100})).toBe('The first value is above 100');
        expect(describeAlert({comparison: 'ANY_ROWS', threshold: null})).toBe('It returns any rows');
    });

    it('names known intervals and spells out the rest', () => {
        expect(intervalLabel(60)).toBe('Every hour');
        expect(intervalLabel(1440)).toBe('Every day');
        expect(intervalLabel(30)).toBe('Every 30 minutes');
    });

    it('offers only the person\'s own saved queries that have no alert yet', () => {
        const query = (id: string, owner: string): SavedQuery => ({id, owner, name: id, sql: 'SELECT 1', shared: false, updatedAt: ''});
        const queries = [query('late-runs', 'maria'), query('big-quakes', 'maria'), query('zones', 'omar')];
        expect(alertCandidates(queries, [{queryId: 'late-runs'}], 'maria').map(({id}) => id)).toEqual(['big-quakes']);
    });
});
