import {describe, expect, it} from 'vitest';
import {searchIndex, searchResults} from './searchIndex';

const SOURCES = {
    datasets: [{dataset: 'earthquakes'}] as never,
    catalog: {datasets: [{dataset: 'earthquakes', tables: [{id: 'current', layer: 'current', label: 'current', sqlName: 'earthquakes.current'}]}], serving: []} as never,
    schema: {'earthquakes.current': [{name: 'magnitude', type: 'Nullable(Float64)'}], 'other.table': [{name: 'x', type: 'String'}]} as never,
    opened: [],
};

describe('search index', () => {
    it('lists columns only of tables the person can open', () => {
        const items = searchIndex(SOURCES, () => {});

        expect(items.filter((item) => item.group === 'Columns').map((item) => item.label)).toEqual(['magnitude']);
        expect(items.find((item) => item.label === 'magnitude')?.to).toBe('/catalog/lake/earthquakes/current');
    });

    it('lists a column once for every table that has it with the same type', () => {
        const catalog = {datasets: [{dataset: 'earthquakes', tables: [
            {id: 'current', layer: 'current', label: 'current', sqlName: 'earthquakes.current'},
            {id: 'history', layer: 'history', label: 'history', sqlName: 'earthquakes.history'},
        ]}], serving: []} as never;
        const schema = {'earthquakes.current': [{name: 'depthKm', type: 'Float64'}], 'earthquakes.history': [{name: 'depthKm', type: 'Float64'}]} as never;
        const columns = searchIndex({...SOURCES, catalog, schema}, () => {}).filter((item) => item.group === 'Columns');

        expect(columns.map((item) => [item.label, item.description])).toEqual([['depthKm', 'earthquakes.current, earthquakes.history']]);
    });

    it('scopes and groups results', () => {
        const items = searchIndex(SOURCES, () => {});

        expect(searchResults(items, 'quake', 'pipelines').map((item) => item.label)).toEqual(['earthquakes']);
        expect(searchResults(items, '', undefined).some((item) => item.group === 'Pages')).toBe(false);
        expect(searchResults(items, 'catalog', undefined).map((item) => item.group)).toContain('Pages');
    });
});
