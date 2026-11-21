import {describe, expect, it} from 'vitest';
import type {QueryResult} from '../api/types';
import {chartAxes, restoreChart} from './chartChoice';

const result = (columns: [string, string][]): QueryResult =>
    ({columns: columns.map(([name, type]) => ({name, type})), rows: [], truncated: false, elapsedMs: 0, rowsRead: 0, bytesRead: 0});

const REGIONS = result([['region', 'String'], ['events', 'UInt64'], ['avgMagnitude', 'Float64']]);

describe('chartAxes', () => {
    it('plots the first text column against the first number by default', () => {
        expect(chartAxes(REGIONS)).toMatchObject({x: 'region', y: 'events', kind: 'bar', whole: true});
    });

    it('keeps the tab\'s choice while its columns still exist', () => {
        expect(chartAxes(REGIONS, {x: 'events', y: 'avgMagnitude', kind: 'line'})).toMatchObject({x: 'events', y: 'avgMagnitude', kind: 'line', whole: false});
    });

    it('falls back when the query no longer returns the chosen columns', () => {
        expect(chartAxes(REGIONS, {x: 'day', y: 'maxMagnitude'})).toMatchObject({x: 'region', y: 'events'});
    });
});

describe('restoreChart', () => {
    it('drops stored values it does not know', () => {
        expect(restoreChart({x: 'region', y: 3, kind: 'pie'})).toEqual({x: 'region', y: undefined, kind: undefined});
        expect(restoreChart('bar')).toBeUndefined();
    });
});
