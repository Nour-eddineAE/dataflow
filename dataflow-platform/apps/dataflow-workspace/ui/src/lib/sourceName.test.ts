import {describe, expect, it} from 'vitest';
import {datasetFromSource, isSource, isWebSource} from './sourceName';

describe('datasetFromSource', () => {
    it('names the dataset after the file, without its date or extension', () => {
        expect(datasetFromSource('https://earthquake.usgs.gov/feed/all_month.csv')).toBe('all_month');
        expect(datasetFromSource('https://d37ci6vzurychx.cloudfront.net/trip-data/yellow_tripdata_2024-01.parquet')).toBe('yellow_tripdata');
        expect(datasetFromSource('https://example.org/2024-01-02.json?x=1')).toBe('');
        expect(datasetFromSource('https://example.org/1999 data.csv')).toBe('source_1999_data');
    });

    it('accepts http and https URLs only', () => {
        expect(isWebSource('https://example.org/a.csv')).toBe(true);
        expect(isWebSource('file:///etc/passwd')).toBe(false);
        expect(isWebSource('example.org/a.csv')).toBe(false);
        expect(isSource('inbox/sensors/readings.csv')).toBe(true);
        expect(isSource('system/tasks/x.json')).toBe(false);
        expect(isSource('inbox/../x.csv')).toBe(false);
    });
});
