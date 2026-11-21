import type {Schema} from '../api/types';
import {bodyOf, CURRENT, type Gold, type GoldStep, type JoinType, operationOf, relations, type Spec} from './specModel';

const RESERVED_DATABASES = new Set(['system', 'default', 'serving', 'information_schema']);
const SYSTEM_CURRENT = ['factUniqueId', 'processedAt', '__START_AT'];
const KEEPS_ONLY_LEFT = new Set<JoinType>(['semi', 'anti']);
const ALIAS = /\s+as\s+`?([A-Za-z_][A-Za-z0-9_]*)`?\s*$/i;
const IDENTIFIER = /^`?([A-Za-z_][A-Za-z0-9_]*)`?$/;

export type Columns = (relation: string) => string[];
export type RelationTable = { name: string; columns: string[] };

export function database(dataset: string) {
    return RESERVED_DATABASES.has(dataset.toLowerCase()) ? `lake_${dataset}` : dataset;
}

export function tableSqlName(table: string): string | undefined {
    const [dataset, kind, name] = table.split('.');
    if (!dataset || !kind) return undefined;
    const suffix = kind === 'gold' || kind === 'reference' ? (name ? `${kind}_${name}` : undefined) : kind;
    return suffix ? `${database(dataset)}.${suffix}` : undefined;
}

export function relationColumns(spec: Spec, schema: Schema | undefined): Columns {
    const dataset = spec.dataset ?? '';
    const known = (sqlName: string | undefined) => (sqlName ? (schema?.[sqlName] ?? []).map((column) => column.name) : []);
    const tables = new Map((spec.gold ?? []).map((table) => [table.name, table]));
    const memo = new Map<string, string[]>();
    const visiting = new Set<string>();

    const columns: Columns = (relation) => {
        const cached = memo.get(relation);
        if (cached) return cached;
        if (visiting.has(relation)) return [];
        visiting.add(relation);
        const result = resolve(relation);
        visiting.delete(relation);
        memo.set(relation, result);
        return result;
    };

    const resolve = (relation: string): string[] => {
        if (relation === CURRENT) {
            const silver = spec.silver ?? {};
            return [...(silver.columns ?? []), ...(silver.steps ?? []).flatMap((step) => step.adds ?? []), ...(silver.derive ?? [])]
                .map((column) => column.name).filter(Boolean).concat(SYSTEM_CURRENT);
        }
        if ((spec.references ?? []).some((reference) => reference.name === relation)) {
            return known(`${database(dataset)}.reference_${relation}`);
        }
        const input = spec.inputs?.[relation];
        if (input !== undefined) return known(tableSqlName(input));
        const gold = tables.get(relation);
        if (!gold) return [];
        if (bodyOf(gold) === 'steps') return columnsAfter(gold, gold.steps?.length ?? 0, columns);
        return known(`${database(dataset)}.gold_${relation}`);
    };
    return columns;
}

export function relationTables(spec: Spec, schema: Schema | undefined, except?: string): RelationTable[] {
    const columns = relationColumns(spec, schema);
    return relations(spec).filter((name) => name !== except).map((name) => ({name, columns: columns(name)}));
}

export function columnsAfter(gold: Gold, count: number, columns: Columns): string[] {
    return (gold.steps ?? []).slice(0, count).reduce((current, step) => apply(current, step, columns), columns(gold.from ?? ''));
}

function apply(current: string[], step: GoldStep, columns: Columns): string[] {
    switch (operationOf(step)) {
        case 'join':
            return joined(current, step.join!, columns(step.join!.with));
        case 'union':
            return merge(current, ...step.union!.map(columns));
        case 'derive':
            return merge(current, Object.keys(step.derive!));
        case 'select':
            return step.select!.map(outputName).filter((name): name is string => !!name);
        case 'rename':
            return current.map((name) => step.rename![name] ?? name);
        case 'drop':
            return current.filter((name) => !step.drop!.includes(name));
        case 'aggregate':
            return [...(step.aggregate!.by ?? []).map(outputName).filter((name): name is string => !!name),
                ...Object.keys(step.aggregate!.measures ?? {})];
        default:
            return current;
    }
}

function joined(left: string[], join: NonNullable<GoldStep['join']>, right: string[]) {
    if (KEEPS_ONLY_LEFT.has(join.type ?? 'inner')) return left;
    const shared = new Set(Object.entries(join.on ?? {}).filter(([a, b]) => a.toLowerCase() === b.toLowerCase())
        .map(([a]) => a.toLowerCase()));
    const prefixed = right.map((name) => (join.prefix ? join.prefix + name.charAt(0).toUpperCase() + name.slice(1) : name));
    return merge(left, prefixed.filter((name) => !shared.has(name.toLowerCase())));
}

export function outputName(expression: string): string | undefined {
    const alias = expression.match(ALIAS);
    if (alias) return alias[1];
    const plain = expression.trim().match(IDENTIFIER);
    return plain ? plain[1] : undefined;
}

function merge(first: string[], ...others: string[][]) {
    const result = [...first];
    others.flat().forEach((name) => {
        if (!result.some((existing) => existing.toLowerCase() === name.toLowerCase())) result.push(name);
    });
    return result;
}
