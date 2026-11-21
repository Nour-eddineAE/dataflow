import {describe, expect, it} from 'vitest';
import {relationTables} from './goldColumns';
import type {Spec} from './specModel';

const SPEC = {
    dataset: 'rides',
    source: {},
    silver: {columns: [{name: 'zoneId', type: 'string'}]},
    references: [{name: 'zones', format: 'csv'}],
    inputs: {trips: 'taxi_trips.gold.daily'},
    gold: [
        {name: 'by_zone', from: 'current', steps: [{select: ['zoneId']}]},
        {name: 'top', sql: 'SELECT 1'},
    ],
} as unknown as Spec;

const SCHEMA = {
    'rides.reference_zones': [{name: 'borough', type: 'String'}],
    'taxi_trips.gold_daily': [{name: 'day', type: 'Date'}],
};

describe('relationTables', () => {
    const table = (name: string, except?: string) => relationTables(SPEC, SCHEMA, except).find((entry) => entry.name === name);

    it('names what a query in this pipeline reads, with columns from the spec and the catalog', () => {
        expect(table('zones')?.columns).toEqual(['borough']);
        expect(table('trips')?.columns).toEqual(['day']);
        expect(table('by_zone')?.columns).toEqual(['zoneId']);
        expect(table('current')?.columns).toContain('zoneId');
    });

    it('leaves out the table being written', () => {
        expect(table('top', 'top')).toBeUndefined();
        expect(table('by_zone', 'top')).toBeDefined();
    });

    it('lists a table the catalog does not know yet, without columns', () => {
        expect(table('top')?.columns).toEqual([]);
    });
});
