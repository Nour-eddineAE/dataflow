import type {QueryResult} from '../api/types';

export const CHART_KINDS = ['bar', 'line'] as const;
export type ChartKind = (typeof CHART_KINDS)[number];
export type ChartChoice = { x?: string; y?: string; kind?: ChartKind };

const NUMERIC = /^(Nullable\()?(U?Int|Float|Decimal)/;
const WHOLE = /^(Nullable\()?U?Int/;

export function chartAxes(result: QueryResult, choice: ChartChoice = {}) {
    const names = result.columns.map((column) => column.name);
    const numeric = result.columns.filter((column) => NUMERIC.test(column.type)).map((column) => column.name);
    const x = choice.x && names.includes(choice.x) ? choice.x : names.find((name) => !numeric.includes(name)) ?? names[0];
    const y = choice.y && numeric.includes(choice.y) ? choice.y : numeric[0];
    const whole = WHOLE.test(result.columns.find((column) => column.name === y)?.type ?? '');
    return {names, numeric, x, y, kind: choice.kind ?? CHART_KINDS[0], whole};
}

export function restoreChart(stored: unknown): ChartChoice | undefined {
    if (!stored || typeof stored !== 'object') return undefined;
    const {x, y, kind} = stored as Record<string, unknown>;
    return {
        x: typeof x === 'string' ? x : undefined,
        y: typeof y === 'string' ? y : undefined,
        kind: CHART_KINDS.find((known) => known === kind),
    };
}
