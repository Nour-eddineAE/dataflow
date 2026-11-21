import type {BoardChart, BoardData, BoardOverview, TableStorage} from '../api/types';
import {INK, type Scheme, SERIES, seriesColors} from './chartTheme';
import {formatAxisTime, formatBytes} from './format';
import {SECOND} from './timing';

type Option = Record<string, unknown>;

const compact = new Intl.NumberFormat(undefined, {notation: 'compact', maximumFractionDigits: 1});
const full = new Intl.NumberFormat();
const BUCKETS = 30;
const GRID = {left: 8, right: 16, bottom: 8, containLabel: true};
const GRID_TOP_WITH_LEGEND = 36;
const BAR_CORNER = 4;
const BAR_RIGHT_CORNERS = [0, BAR_CORNER, BAR_CORNER, 0];
const BAR_TOP_CORNERS = [BAR_CORNER, BAR_CORNER, 0, 0];
const WHOLE_NUMBERS_FROM = 10;

function base(scheme: Scheme): Option {
    const ink = INK[scheme];
    return {
        animationDuration: 250,
        textStyle: {fontFamily: 'inherit', fontSize: 11, color: ink.muted},
        grid: {left: 8, right: 16, top: 16, bottom: 8, containLabel: true},
        tooltip: {
            backgroundColor: ink.surface, borderColor: ink.grid, textStyle: {color: ink.text, fontSize: 12},
            valueFormatter: (value: number) => full.format(value),
        },
    };
}

function valueAxis(scheme: Scheme, extra: Option = {}): Option {
    const ink = INK[scheme];
    return {
        type: 'value',
        splitLine: {lineStyle: {color: ink.grid}},
        axisLabel: {color: ink.muted, formatter: (value: number) => compact.format(value)},
        ...extra,
    };
}

function categoryAxis(scheme: Scheme, data: string[], extra: Option = {}): Option {
    const ink = INK[scheme];
    return {
        type: 'category',
        data,
        axisLine: {lineStyle: {color: ink.grid}},
        axisTick: {show: false},
        axisLabel: {color: ink.muted}, ...extra
    };
}

export function timeOption(chart: BoardChart, data: BoardData, scheme: Scheme): Option {
    const ink = INK[scheme];
    const split = !!chart.split;
    const names = split ? [...new Set(data.rows.map((row) => String(row[1])))] : [chart.title];
    const colors = split ? seriesColors(names, scheme) : {[chart.title]: SERIES[scheme][0]};
    const series = names.map((name) => ({
        name, type: 'line', showSymbol: false, symbolSize: 8, lineStyle: {width: 2}, color: colors[name],
        emphasis: {focus: 'series'},
        data: data.rows.filter((row) => !split || String(row[1]) === name).map((row) => [String(row[0]).replace(' ', 'T'), Number(row[split ? 2 : 1])]),
    }));
    return {
        ...base(scheme),
        grid: {...GRID, top: split ? GRID_TOP_WITH_LEGEND : GRID.right},
        legend: split ? {
            top: 0,
            left: 0,
            icon: 'roundRect',
            itemWidth: 10,
            itemHeight: 10,
            textStyle: {color: ink.text}
        } : undefined,
        tooltip: {
            ...(base(scheme).tooltip as Option),
            trigger: 'axis',
            axisPointer: {type: 'line', lineStyle: {color: ink.muted}}
        },
        xAxis: {
            type: 'time',
            axisLine: {lineStyle: {color: ink.grid}},
            axisLabel: {color: ink.muted, hideOverlap: true},
            splitLine: {show: false}
        },
        yAxis: valueAxis(scheme),
        series,
    };
}

export function barsOption(data: BoardData, scheme: Scheme, measure: string): Option {
    const rows = [...data.rows].reverse();
    return {
        ...base(scheme),
        tooltip: {...(base(scheme).tooltip as Option), trigger: 'item'},
        xAxis: valueAxis(scheme),
        yAxis: categoryAxis(scheme, rows.map((row) => String(row[0])), {
            axisLabel: {
                color: INK[scheme].muted,
                width: 140,
                overflow: 'truncate'
            }
        }),
        series: [{
            name: measure, type: 'bar', barMaxWidth: 18, cursor: 'pointer',
            itemStyle: {color: SERIES[scheme][0], borderRadius: BAR_RIGHT_CORNERS},
            data: rows.map((row) => Number(row[1])),
        }],
    };
}

export type Plot = { labels: string[]; values: number[]; name: string; kind: 'bar' | 'line'; whole: boolean };

export function resultOption({labels, values, name, kind, whole}: Plot, scheme: Scheme): Option {
    const color = SERIES[scheme][0];
    return {
        ...base(scheme),
        tooltip: {...(base(scheme).tooltip as Option), trigger: 'axis'},
        xAxis: categoryAxis(scheme, labels, {axisLabel: {color: INK[scheme].muted, hideOverlap: true}}),
        yAxis: valueAxis(scheme, whole ? {minInterval: 1} : {}),
        series: [kind === 'line'
            ? {name, type: 'line', smooth: true, showSymbol: false, lineStyle: {width: 2}, color, data: values}
            : {name, type: 'bar', barMaxWidth: 28, itemStyle: {color, borderRadius: BAR_TOP_CORNERS}, data: values}],
    };
}

export function bucketLabels(low: number, high: number, buckets = BUCKETS): string[] {
    const width = (high - low) / buckets;
    const digits = width >= WHOLE_NUMBERS_FROM ? 0 : width >= 1 ? 1 : 2;
    return Array.from({length: buckets}, (_, index) => (low + index * width).toFixed(digits));
}

export function histogramOption(chart: BoardChart, data: BoardData, scheme: Scheme): Option {
    const labels = bucketLabels(Number(chart.low), Number(chart.high));
    const counts = new Array(labels.length).fill(0);
    data.rows.forEach((row) => {
        counts[Number(row[0]) - 1] = Number(row[1]);
    });
    return {
        ...base(scheme),
        tooltip: {...(base(scheme).tooltip as Option), trigger: 'axis', axisPointer: {type: 'shadow'}},
        xAxis: categoryAxis(scheme, labels, {axisLabel: {color: INK[scheme].muted, hideOverlap: true}}),
        yAxis: valueAxis(scheme),
        series: [{
            name: 'Rows',
            type: 'bar',
            barCategoryGap: 2,
            itemStyle: {color: SERIES[scheme][0], borderRadius: BAR_TOP_CORNERS},
            data: counts
        }],
    };
}

const AREA_OPACITY = 0.25;

export function sizePerLayer(storage: TableStorage[]): { days: string[]; layers: Record<string, number[]> } {
    const days = [...new Set(storage.flatMap((table) => table.sizeChanges.map((change) => change.day)))].sort();
    const layers: Record<string, number[]> = {};
    for (const table of storage) {
        const changes = new Map(table.sizeChanges.map((change) => [change.day, change.bytes]));
        const totals = layers[table.layer] ??= days.map(() => 0);
        let size = 0;
        days.forEach((day, index) => {
            size = Math.max(0, size + (changes.get(day) ?? 0));
            totals[index] += size;
        });
    }
    return {days, layers};
}

export function sizeOption(storage: TableStorage[], scheme: Scheme): Option {
    const ink = INK[scheme];
    const {days, layers} = sizePerLayer(storage);
    const names = Object.keys(layers);
    const colors = seriesColors(names, scheme);
    return {
        ...base(scheme),
        grid: {...GRID, top: GRID_TOP_WITH_LEGEND},
        legend: {top: 0, left: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: {color: ink.text}},
        tooltip: {
            ...(base(scheme).tooltip as Option),
            trigger: 'axis',
            axisPointer: {type: 'line', lineStyle: {color: ink.muted}},
            valueFormatter: (value: number) => formatBytes(value),
        },
        xAxis: categoryAxis(scheme, days),
        yAxis: valueAxis(scheme, {axisLabel: {color: ink.muted, formatter: (value: number) => formatBytes(value)}}),
        series: names.map((name) => ({
            name, type: 'line', stack: 'size', showSymbol: days.length === 1, lineStyle: {width: 2},
            areaStyle: {opacity: AREA_OPACITY}, color: colors[name], emphasis: {focus: 'series'}, data: layers[name],
        })),
    };
}

export function runsOption(overview: BoardOverview, scheme: Scheme): Option {
    const ink = INK[scheme];
    const runs = [...overview.runs].reverse();
    const ok = (status: string) => status === 'COMPLETED';
    return {
        ...base(scheme),
        legend: {top: 0, left: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: {color: ink.text}},
        grid: {left: 8, right: 16, top: 36, bottom: 8, containLabel: true},
        tooltip: {
            ...(base(scheme).tooltip as Option),
            trigger: 'item',
            valueFormatter: (value: number) => `${value.toFixed(1)} s`
        },
        xAxis: categoryAxis(scheme, runs.map((run) => formatAxisTime(run.startedAt)), {
            axisLabel: {
                color: ink.muted,
                hideOverlap: true
            }
        }),
        yAxis: valueAxis(scheme, {axisLabel: {color: ink.muted, formatter: (value: number) => `${value} s`}}),
        series: [
            {
                name: 'Succeeded',
                type: 'bar',
                stack: 'runs',
                barMaxWidth: 18,
                itemStyle: {color: ink.success, borderRadius: BAR_TOP_CORNERS},
                data: runs.map((run) => (ok(run.status) ? (run.durationMs ?? 0) / SECOND : null))
            },
            {
                name: 'Failed',
                type: 'bar',
                stack: 'runs',
                barMaxWidth: 18,
                itemStyle: {color: ink.danger, borderRadius: BAR_TOP_CORNERS},
                data: runs.map((run) => (run.status === 'FAILED' ? (run.durationMs ?? 0) / SECOND : null))
            },
        ],
    };
}

const FLOW_ORDER = ['ingest', 'references', 'bronze', 'silver', 'history', 'gold', 'serving', 'maintenance'];

export const flowRank = (flow: string) => {
    const index = FLOW_ORDER.indexOf(flow.split('.')[0]);
    return index < 0 ? FLOW_ORDER.length : index;
};

export function flowsOption(overview: BoardOverview, scheme: Scheme): Option {
    const flows = overview.flows.filter((flow) => flow.rowsOut !== undefined && flow.rowsOut !== null && flow.flow !== 'maintenance')
        .sort((a, b) => flowRank(a.flow) - flowRank(b.flow) || a.flow.localeCompare(b.flow));
    return barsOption({
        chart: 'flows',
        sql: '',
        rows: flows.map((flow) => [flow.flow, flow.rowsOut]),
        truncated: false,
        elapsedMs: 0,
        cached: false
    }, scheme, 'Rows out');
}

export function rulesOption(overview: BoardOverview, scheme: Scheme): Option {
    const ink = INK[scheme];
    const runs = [...new Set(overview.rules.map((rule) => rule.at))].sort();
    const worst = new Map<string, number>();
    overview.rules.forEach((rule) => worst.set(rule.rule, Math.max(worst.get(rule.rule) ?? 0, rule.evaluated ? rule.failing / rule.evaluated : 0)));
    const names = [...worst.entries()].sort((a, b) => b[1] - a[1]).slice(0, SERIES[scheme].length).map(([name]) => name);
    const colors = seriesColors(names, scheme);
    return {
        ...base(scheme),
        legend: {
            top: 0,
            left: 0,
            icon: 'roundRect',
            itemWidth: 10,
            itemHeight: 10,
            textStyle: {color: ink.text},
            type: 'scroll'
        },
        grid: {left: 8, right: 16, top: 36, bottom: 8, containLabel: true},
        tooltip: {
            ...(base(scheme).tooltip as Option),
            trigger: 'axis',
            valueFormatter: (value: number) => `${value.toFixed(2)} %`
        },
        xAxis: categoryAxis(scheme, runs.map((at) => formatAxisTime(at)), {
            axisLabel: {
                color: ink.muted,
                hideOverlap: true
            }
        }),
        yAxis: valueAxis(scheme, {axisLabel: {color: ink.muted, formatter: (value: number) => `${value} %`}}),
        series: names.map((name) => ({
            name, type: 'line', showSymbol: false, symbolSize: 8, lineStyle: {width: 2}, color: colors[name],
            data: runs.map((at) => {
                const run = overview.rules.find((rule) => rule.rule === name && rule.at === at);
                return run && run.evaluated > 0 ? (100 * run.failing) / run.evaluated : null;
            }),
        })),
    };
}

export function quarantineOption(overview: BoardOverview, scheme: Scheme): Option {
    return barsOption({
        chart: 'quarantine',
        sql: '',
        rows: overview.quarantine.map((reason) => [reason.rule, reason.rows]),
        truncated: false,
        elapsedMs: 0,
        cached: false
    }, scheme, 'Rows');
}
