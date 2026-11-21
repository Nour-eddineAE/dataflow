import {describe, expect, it} from 'vitest';
import {favoriteEntry} from './favorites';

const AT = '2026-10-30T15:00:00Z';

describe('favoriteEntry', () => {
    it('links each kind to its page', () => {
        expect(favoriteEntry({kind: 'PIPELINE', ref: 'earthquakes', createdAt: AT}, [])?.to).toBe('/pipelines/earthquakes');
        expect(favoriteEntry({kind: 'TABLE', ref: 'earthquakes.gold.regions', createdAt: AT}, [])?.to)
            .toBe('/catalog/lake/earthquakes/gold.regions');
        expect(favoriteEntry({kind: 'SERVED', ref: 'zone_hours', createdAt: AT}, [])?.label).toBe('serving.zone_hours');
    });

    it('names a saved query and drops one that is gone', () => {
        const query = {id: 'q1', name: 'Strong quakes', owner: 'ada', sql: 'SELECT 1', shared: false, updatedAt: AT};
        expect(favoriteEntry({kind: 'QUERY', ref: 'q1', createdAt: AT}, [query])?.label).toBe('Strong quakes');
        expect(favoriteEntry({kind: 'QUERY', ref: 'q2', createdAt: AT}, [query])).toBeUndefined();
    });
});
