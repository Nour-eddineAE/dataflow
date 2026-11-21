import {describe, expect, it} from 'vitest';
import type {BoardChart, BoardData, TableStorage} from '../api/types';
import {bucketLabels, histogramOption, resultOption, sizeOption, sizePerLayer, timeOption} from './boardOptions';
import {SERIES, seriesColors} from './chartTheme';

const data = (rows: unknown[][]): BoardData => ({
    chart: 'c',
    sql: '',
    rows,
    truncated: false,
    elapsedMs: 1,
    cached: false
});

const stored = (table: string, layer: string, sizeChanges: TableStorage['sizeChanges']): TableStorage => ({
    table, layer, sizeChanges, commits: 1, files: 1, bytes: 1, smallFiles: 0, waitingBytes: 0, reclaimedBytes: 0,
});

describe('resultOption', () => {
    const plot = {labels: ['Alaska', 'CA'], values: [3, 2], name: 'quakes', whole: true};

    it('draws one bar series in the first series colour', () => {
        const option = resultOption({...plot, kind: 'bar'}, 'dark') as { series: { type: string; color?: string; itemStyle?: { color: string } }[] };
        expect(option.series[0].type).toBe('bar');
        expect(option.series[0].itemStyle?.color).toBe(SERIES.dark[0]);
    });

    it('keeps whole-number axes to whole ticks', () => {
        const option = resultOption({...plot, kind: 'line'}, 'light') as { yAxis: { minInterval?: number } };
        expect(option.yAxis.minInterval).toBe(1);
    });
});

describe('boardOptions', () => {
    it('carries each table size forward and adds tables of one layer', () => {
        const {days, layers} = sizePerLayer([
            stored('bronze', 'bronze', [{day: '2026-10-24', bytes: 100}, {day: '2026-10-26', bytes: -40}]),
            stored('gold.a', 'gold', [{day: '2026-10-25', bytes: 5}]),
            stored('gold.b', 'gold', [{day: '2026-10-24', bytes: 7}]),
        ]);
        expect(days).toEqual(['2026-10-24', '2026-10-25', '2026-10-26']);
        expect(layers).toEqual({bronze: [100, 100, 60], gold: [7, 12, 12]});
    });

    it('stacks one area per layer and marks a single day', () => {
        const option = sizeOption([stored('bronze', 'bronze', [{day: '2026-10-24', bytes: 100}])], 'light');
        const series = option.series as { name: string; stack: string; showSymbol: boolean; data: number[] }[];
        expect(series).toHaveLength(1);
        expect(series[0]).toMatchObject({name: 'bronze', stack: 'size', showSymbol: true, data: [100]});
    });

    it('keeps a color with its series whatever the others are', () => {
        const all = seriesColors(['md', 'ml', 'mb'], 'light');
        const filtered = seriesColors(['ml', 'md'], 'light');
        expect(all.mb).toBe(SERIES.light[0]);
        expect(all.ml).toBe(SERIES.light[2]);
        expect(filtered.md).toBe(SERIES.light[0]);
        expect(seriesColors(Array.from({length: 10}, (_, index) => `s${index}`), 'dark')).not.toHaveProperty('s9');
    });

    it('pivots a split time series into one line per series', () => {
        const chart: BoardChart = {id: 'time', title: 'Rows over time', kind: 'TIME', x: 'day', split: 'band'};
        const option = timeOption(chart, data([['2026-10-24', 'a', 3], ['2026-10-24', 'b', 1], ['2026-10-25', 'a', 4]]), 'light');
        const series = option.series as { name: string; data: unknown[] }[];
        expect(series.map((entry) => entry.name)).toEqual(['a', 'b']);
        expect(series[0].data).toEqual([['2026-10-24', 3], ['2026-10-25', 4]]);
        expect(option.legend).toBeDefined();
    });

    it('labels histogram buckets from the profile range and fills empty ones', () => {
        expect(bucketLabels(0, 30, 3)).toEqual(['0', '10', '20']);
        expect(bucketLabels(-1.5, 1.5, 2)).toEqual(['-1.5', '0.0']);
        const chart: BoardChart = {id: 'h', title: 'mag', kind: 'HISTOGRAM', x: 'mag', low: '0', high: '3'};
        const option = histogramOption(chart, data([[1, 5], [30, 2]]), 'dark');
        const counts = (option.series as { data: number[] }[])[0].data;
        expect(counts).toHaveLength(30);
        expect(counts[0]).toBe(5);
        expect(counts[29]).toBe(2);
        expect(counts[10]).toBe(0);
    });
});
