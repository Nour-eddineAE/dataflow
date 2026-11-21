import type {SpecCompute} from './compute';

export type Column = { name: string; type: string; required?: boolean; from?: string };
export type Expectation = { name: string; check: string; 'on-failure'?: 'warn' | 'drop' | 'fail' };
export type Step = {
    name: string;
    transformation: string;
    inputs?: Record<string, string>;
    params?: Record<string, unknown>;
    adds?: Column[];
};
export type Reference = { name: string; url?: string; path?: string; format: string; options?: Record<string, string> };
export type JoinType = 'inner' | 'left' | 'right' | 'full' | 'semi' | 'anti';
export type Join = { with: string; type?: JoinType; on?: Record<string, string>; condition?: string; prefix?: string };
export type Aggregate = { by?: string[]; measures?: Record<string, string> };
export type Dedupe = { keys?: string[]; 'latest-by'?: string };
export type GoldStep = {
    join?: Join;
    union?: string[];
    filter?: string;
    derive?: Record<string, string>;
    select?: string[];
    rename?: Record<string, string>;
    drop?: string[];
    aggregate?: Aggregate;
    dedupe?: Dedupe;
};
export type Operation = keyof GoldStep;
export type Gold = {
    name: string;
    sql?: string;
    transformation?: string;
    inputs?: Record<string, string>;
    params?: Record<string, unknown>;
    from?: string;
    steps?: GoldStep[];
    materialize?: boolean;
};
export type Serve = { from: string; table: string; mode?: 'view' | 'copy'; 'order-by'?: string };

export type Spec = {
    dataset?: string;
    version?: number;
    owner?: string;
    description?: string;
    source?: {
        url?: string;
        path?: string;
        format?: string;
        options?: Record<string, string>;
        partitions?: { monthly?: { start: string }; daily?: { start: string } };
    };
    inputs?: Record<string, string>;
    schedule?: string;
    compute?: SpecCompute;
    bronze?: { 'schema-evolution'?: 'rescue' | 'add-new-columns'; 'max-files-per-trigger'?: number };
    silver?: { columns?: Column[]; steps?: Step[]; derive?: Column[]; expectations?: Expectation[] };
    history?: { keys?: string[]; 'sequence-by'?: string; 'stored-as'?: string; 'track-history-except'?: string[] };
    references?: Reference[];
    gold?: Gold[];
    serve?: Serve[];
};

export type Block = 'input' | 'step' | 'reference' | 'gold-steps' | 'gold-sql' | 'gold-transformation' | 'serve-view' | 'serve-copy';
export type NodeKind = 'pipeline' | 'source' | 'bronze' | 'silver' | 'history' | 'step' | 'input' | 'reference' | 'gold' | 'serve';
export type DesignNode = { id: string; kind: NodeKind; label: string; detail: string; index?: number; stored?: boolean };
export type DesignEdge = { from: string; to: string; editable: boolean };
export type GoldBody = 'steps' | 'sql' | 'transformation';

type Wired = { inputs?: Record<string, string> };

export const CURRENT = 'current';
export const PIPELINE = 'pipeline';
export const OPERATIONS: Operation[] = ['join', 'union', 'filter', 'derive', 'select', 'rename', 'drop', 'aggregate', 'dedupe'];
export const OPERATION_LABELS: Record<Operation, string> = {
    join: 'Join',
    union: 'Append',
    filter: 'Filter',
    derive: 'Add columns',
    select: 'Choose columns',
    rename: 'Rename',
    drop: 'Remove columns',
    aggregate: 'Group',
    dedupe: 'Remove duplicates',
};
const GOLD = 'gold.';
export const REFERENCE = 'reference.';
export const ROWS = 'rows';
const SPINE = new Set<NodeKind>(['pipeline', 'source', 'bronze', 'silver', 'history']);
const SQL_RELATION = /\b(?:from|join)\s+([A-Za-z_][A-Za-z0-9_]*)/gi;

export function ingests(spec: Spec) {
    return spec.source !== undefined;
}

export function isRemovable(node: DesignNode) {
    return !SPINE.has(node.kind);
}

export function bodyOf(gold: Gold): GoldBody {
    if (gold.transformation !== undefined) return 'transformation';
    if (gold.sql !== undefined) return 'sql';
    return 'steps';
}

export function operationOf(step: GoldStep): Operation | undefined {
    return OPERATIONS.find((operation) => step[operation] !== undefined);
}

export function relations(spec: Spec): string[] {
    return [
        ...(ingests(spec) ? [CURRENT] : []),
        ...(spec.references ?? []).map((reference) => reference.name),
        ...Object.keys(spec.inputs ?? {}),
        ...(spec.gold ?? []).map((table) => table.name),
    ].filter(Boolean);
}

export function goldReads(gold: Gold): string[] {
    const found = new Set<string>();
    switch (bodyOf(gold)) {
        case 'sql':
            for (const match of (gold.sql ?? '').matchAll(SQL_RELATION)) found.add(match[1].toLowerCase());
            break;
        case 'transformation': {
            const sources = Object.values(gold.inputs ?? {});
            if (!sources.length) found.add(CURRENT);
            sources.forEach((source) => found.add(relationOf(source)));
            break;
        }
        case 'steps':
            if (gold.from) found.add(gold.from);
            (gold.steps ?? []).forEach((step) => {
                if (step.join?.with) found.add(step.join.with);
                step.union?.forEach((name) => found.add(name));
            });
    }
    return [...found].filter(Boolean);
}

export function throughStep(spec: Spec, table: string, index: number): Spec {
    const tables = spec.gold ?? [];
    const needed = new Set<string>();
    const visit = (name: string) => {
        const gold = tables.find((candidate) => candidate.name === name);
        if (!gold || needed.has(name)) return;
        needed.add(name);
        const read = name === table ? {...gold, steps: (gold.steps ?? []).slice(0, index + 1)} : gold;
        goldReads(read).forEach(visit);
    };
    visit(table);
    return {
        ...spec,
        gold: tables
            .filter((gold) => needed.has(gold.name))
            .map((gold) => (gold.name === table ? {...gold, steps: (gold.steps ?? []).slice(0, index + 1), materialize: undefined} : gold)),
        serve: [],
    };
}

function relationOf(source: string) {
    if (source === ROWS) return CURRENT;
    return source.startsWith(REFERENCE) ? source.slice(REFERENCE.length) : source;
}

export function goldDetail(gold: Gold) {
    switch (bodyOf(gold)) {
        case 'sql':
            return 'SQL';
        case 'transformation':
            return gold.transformation || 'no transformation';
        case 'steps': {
            const operations = (gold.steps ?? []).map(operationOf).filter((operation): operation is Operation => !!operation);
            return operations.length ? operations.map((operation) => OPERATION_LABELS[operation].toLowerCase()).join(' · ')
                : `from ${gold.from || '…'}`;
        }
    }
}

type Ids = { input: Map<string, string>; reference: Map<string, string>; gold: Map<string, string> };

function listIds(kind: NodeKind, names: string[]) {
    const ids = names.map((name, index) => `${kind}:${name || `#${index}`}`);
    return ids.map((id, index) => (ids.indexOf(id) === index ? id : `${kind}:#${index}`));
}

function idsOf(spec: Spec): Ids & { lists: Record<'input' | 'reference' | 'gold' | 'serve' | 'step', string[]> } {
    const inputNames = Object.keys(spec.inputs ?? {});
    const lists = {
        input: listIds('input', inputNames),
        reference: listIds('reference', (spec.references ?? []).map((reference) => reference.name)),
        gold: listIds('gold', (spec.gold ?? []).map((table) => table.name)),
        serve: listIds('serve', (spec.serve ?? []).map((served) => served.table)),
        step: listIds('step', (spec.silver?.steps ?? []).map((step) => step.name)),
    };
    const byName = (names: string[], ids: string[]) => new Map(names.map((name, index) => [name, ids[index]]));
    return {
        lists,
        input: byName(inputNames, lists.input),
        reference: byName((spec.references ?? []).map((reference) => reference.name), lists.reference),
        gold: byName((spec.gold ?? []).map((table) => table.name), lists.gold),
    };
}

export function relationNode(spec: Spec, relation: string, ids: Ids = idsOf(spec)): string | undefined {
    if (relation === CURRENT) return ingests(spec) ? 'history' : undefined;
    return ids.reference.get(relation) ?? ids.input.get(relation) ?? ids.gold.get(relation);
}

export function designGraph(spec: Spec): { nodes: DesignNode[]; edges: DesignEdge[] } {
    const ids = idsOf(spec);
    const steps = spec.silver?.steps ?? [];
    const gold = spec.gold ?? [];
    const serve = spec.serve ?? [];
    const spine: DesignNode[] = ingests(spec) ? [
        {id: 'source', kind: 'source', label: 'Source', detail: sourceDetail(spec)},
        {id: 'bronze', kind: 'bronze', label: 'Bronze', detail: spec.bronze?.['schema-evolution'] ?? 'rescue'},
        {id: 'silver', kind: 'silver', label: 'Silver', detail: silverDetail(spec)},
        ...steps.map((step, index): DesignNode => (
            {id: ids.lists.step[index], kind: 'step', label: step.name || 'step', detail: step.transformation || 'no transformation', index})),
        {id: 'history', kind: 'history', label: 'History', detail: (spec.history?.keys ?? []).join(', ') || 'no keys'},
    ] : [];
    const nodes: DesignNode[] = [
        ...spine,
        ...Object.entries(spec.inputs ?? {}).map(([alias, table], index): DesignNode => (
            {id: ids.lists.input[index], kind: 'input', label: alias || 'input', detail: table || 'no table chosen', index})),
        ...(spec.references ?? []).map((reference, index): DesignNode => (
            {id: ids.lists.reference[index], kind: 'reference', label: reference.name || 'reference', detail: reference.format ?? '', index})),
        ...gold.map((table, index): DesignNode => (
            {id: ids.lists.gold[index], kind: 'gold', label: table.name || 'table', detail: goldDetail(table), index,
                stored: table.materialize !== false})),
        ...serve.map((served, index): DesignNode => (
            {id: ids.lists.serve[index], kind: 'serve', label: served.table || 'table', detail: served.mode ?? 'view', index})),
    ];

    const chain = ingests(spec) ? ['silver', ...steps.map((_, index) => ids.lists.step[index]), 'history'] : [];
    const edges: (DesignEdge | undefined)[] = [
        ...(ingests(spec) ? [{from: 'source', to: 'bronze', editable: false}, {from: 'bronze', to: 'silver', editable: false}] : []),
        ...chain.slice(1).map((to, index) => ({from: chain[index], to, editable: false})),
        ...steps.flatMap((step, index) => Object.values(step.inputs ?? {})
            .filter((source) => source.startsWith(REFERENCE))
            .map((source) => link(ids.reference.get(source.slice(REFERENCE.length)), ids.lists.step[index], true))),
        ...gold.flatMap((table, index) => goldReads(table)
            .map((relation) => link(relationNode(spec, relation, ids), ids.lists.gold[index], bodyOf(table) !== 'sql'))),
        ...serve.map((served, index) => {
            const from = served.from?.startsWith(GOLD) ? ids.gold.get(served.from.slice(GOLD.length))
                : served.from === CURRENT ? relationNode(spec, CURRENT, ids) : undefined;
            return link(from, ids.lists.serve[index], false);
        }),
    ];
    const distinct = new Map<string, DesignEdge>();
    for (const edge of edges) {
        if (!edge || edge.from === edge.to) continue;
        const key = `${edge.from}->${edge.to}`;
        const existing = distinct.get(key);
        if (!existing || !edge.editable) distinct.set(key, edge);
    }
    return {nodes, edges: [...distinct.values()]};
}

function link(from: string | undefined, to: string, editable: boolean): DesignEdge | undefined {
    return from ? {from, to, editable} : undefined;
}

function sourceDetail(spec: Spec) {
    const where = spec.source?.url ?? spec.source?.path ?? 'no source';
    const name = where.split('/').filter(Boolean).pop() ?? where;
    return [spec.source?.format, name].filter(Boolean).join(' · ');
}

function silverDetail(spec: Spec) {
    const silver = spec.silver ?? {};
    const parts: [number, string, string][] = [
        [silver.columns?.length ?? 0, 'column', 'columns'],
        [silver.derive?.length ?? 0, 'derived', 'derived'],
        [silver.expectations?.length ?? 0, 'rule', 'rules'],
    ];
    return parts.filter(([count]) => count > 0).map(([count, one, many]) => `${count} ${count === 1 ? one : many}`).join(' · ')
        || 'no columns';
}

function defaultRelation(spec: Spec) {
    return ingests(spec) ? CURRENT : Object.keys(spec.inputs ?? {})[0] ?? spec.references?.[0]?.name ?? '';
}

export function addInputTable(spec: Spec, table: string): { spec: Spec; id: string } {
    const [dataset, ...rest] = table.split('.');
    const alias = unique(`${dataset}_${rest.at(-1)}`, relations(spec));
    const next = {...spec, inputs: {...spec.inputs, [alias]: table}};
    return {spec: next, id: idOfName(next, 'input', alias)};
}

export function addBlock(spec: Spec, block: Block, source?: DesignNode): { spec: Spec; id: string } {
    const taken = relations(spec);
    const start = source && isRelation(source) ? relationName(source) : undefined;
    switch (block) {
        case 'input': {
            const alias = unique('input', taken);
            const next = {...spec, inputs: {...spec.inputs, [alias]: ''}};
            return {spec: next, id: idOfName(next, 'input', alias)};
        }
        case 'step': {
            const steps = spec.silver?.steps ?? [];
            const name = unique('step', steps.map((step) => step.name));
            const next = {...spec, silver: {...spec.silver, steps: [...steps, {name, transformation: ''}]}};
            return {spec: next, id: idOfName(next, 'step', name)};
        }
        case 'reference': {
            const name = unique('lookup', taken);
            const next = {...spec, references: [...(spec.references ?? []), {name, url: '', format: 'csv'}]};
            return {spec: next, id: idOfName(next, 'reference', name)};
        }
        case 'gold-steps':
        case 'gold-sql':
        case 'gold-transformation': {
            const name = unique('summary', taken);
            const from = start ?? defaultRelation(spec);
            const table: Gold = block === 'gold-steps' ? {name, from, steps: []}
                : block === 'gold-sql' ? {name, sql: `SELECT *\nFROM ${from || CURRENT}\n`}
                    : {name, transformation: '', inputs: from ? {[from === CURRENT ? ROWS : from]: from} : {}};
            const next = {...spec, gold: [...(spec.gold ?? []), table]};
            return {spec: next, id: idOfName(next, 'gold', name)};
        }
        case 'serve-view':
        case 'serve-copy': {
            const serve = spec.serve ?? [];
            const table = unique(spec.dataset ?? 'table', serve.map((served) => served.table));
            const stored = (spec.gold ?? []).find((candidate) => candidate.materialize !== false);
            const picked = source?.kind === 'gold' && source.stored !== false ? `${GOLD}${source.label}` : undefined;
            const from = picked ?? (ingests(spec) ? CURRENT : stored ? `${GOLD}${stored.name}` : CURRENT);
            const served: Serve = block === 'serve-view'
                ? {from, table, mode: 'view'}
                : {from, table, mode: 'copy', 'order-by': spec.history?.keys?.[0] ?? ''};
            const next = {...spec, serve: [...serve, served]};
            return {spec: next, id: idOfName(next, 'serve', table)};
        }
    }
}

function idOfName(spec: Spec, kind: 'input' | 'reference' | 'gold' | 'serve' | 'step', name: string) {
    const ids = idsOf(spec).lists[kind];
    const names = kind === 'input' ? Object.keys(spec.inputs ?? {})
        : kind === 'reference' ? (spec.references ?? []).map((reference) => reference.name)
            : kind === 'gold' ? (spec.gold ?? []).map((table) => table.name)
                : kind === 'serve' ? (spec.serve ?? []).map((served) => served.table)
                    : (spec.silver?.steps ?? []).map((step) => step.name);
    return ids[names.lastIndexOf(name)];
}

export function removeNode(spec: Spec, node: DesignNode): Spec {
    const without = <T, >(items: T[] | undefined) => (items ?? []).filter((_, index) => index !== node.index);
    switch (node.kind) {
        case 'step':
            return {...spec, silver: {...spec.silver, steps: without(spec.silver?.steps)}};
        case 'input':
            return {...spec, inputs: Object.fromEntries(Object.entries(spec.inputs ?? {}).filter((_, index) => index !== node.index))};
        case 'reference':
            return {...spec, references: without(spec.references)};
        case 'gold': {
            const removed = `${GOLD}${spec.gold?.[node.index!]?.name}`;
            const fallback = ingests(spec) ? CURRENT : undefined;
            return {
                ...spec,
                gold: without(spec.gold),
                serve: spec.serve?.flatMap((served) => (served.from !== removed ? [served]
                    : fallback ? [{...served, from: fallback}] : [])),
            };
        }
        case 'serve':
            return {...spec, serve: without(spec.serve)};
        default:
            return spec;
    }
}

export function duplicateNode(spec: Spec, node: DesignNode): { spec: Spec; id: string } | undefined {
    const index = node.index;
    if (index === undefined) return undefined;
    if (node.kind === 'gold' && spec.gold?.[index]) {
        const name = unique(`${spec.gold[index].name}_copy`, relations(spec));
        const gold = [...spec.gold];
        gold.splice(index + 1, 0, {...structuredClone(spec.gold[index]), name});
        const next = {...spec, gold};
        return {spec: next, id: idOfName(next, 'gold', name)};
    }
    if (node.kind === 'reference' && spec.references?.[index]) {
        const name = unique(`${spec.references[index].name}_copy`, relations(spec));
        const references = [...spec.references];
        references.splice(index + 1, 0, {...structuredClone(spec.references[index]), name});
        const next = {...spec, references};
        return {spec: next, id: idOfName(next, 'reference', name)};
    }
    return undefined;
}

export function catalogTable(spec: Spec, node: DesignNode): string | undefined {
    const index = node.index ?? -1;
    switch (node.kind) {
        case 'bronze':
        case 'silver':
            return node.kind;
        case 'history':
            return CURRENT;
        case 'reference':
            return spec.references?.[index]?.name ? `reference.${spec.references[index].name}` : undefined;
        case 'gold':
            return node.stored !== false && spec.gold?.[index]?.name ? `${GOLD}${spec.gold[index].name}` : undefined;
        case 'serve':
            return spec.serve?.[index]?.table ? `serving.${spec.serve[index].table}` : undefined;
        default:
            return undefined;
    }
}

export function moveStep(spec: Spec, index: number, delta: number): Spec {
    const steps = [...(spec.silver?.steps ?? [])];
    const target = index + delta;
    if (target < 0 || target >= steps.length) return spec;
    [steps[index], steps[target]] = [steps[target], steps[index]];
    return {...spec, silver: {...spec.silver, steps}};
}

export function moveGoldStep(gold: Gold, index: number, delta: number): Gold {
    const steps = [...(gold.steps ?? [])];
    const target = index + delta;
    if (target < 0 || target >= steps.length) return gold;
    [steps[index], steps[target]] = [steps[target], steps[index]];
    return {...gold, steps};
}

function renameRelation(spec: Spec, before: string, after: string): Spec {
    const swapName = (name: string | undefined) => (name === before ? after : name);
    const rewire = <T extends Wired>(item: T): T => (item.inputs
        ? {...item, inputs: mapValues(item.inputs, (source) => (source === before ? after
            : source === `${REFERENCE}${before}` ? `${REFERENCE}${after}` : source))}
        : item);
    const renameSql = (sql: string) => sql.replace(SQL_RELATION, (match, name: string) =>
        (name.toLowerCase() === before.toLowerCase() ? match.slice(0, match.length - name.length) + after : match));
    return {
        ...spec,
        silver: spec.silver?.steps ? {...spec.silver, steps: spec.silver.steps.map(rewire)} : spec.silver,
        gold: spec.gold?.map((table) => {
            const wired = rewire(table);
            return {
                ...wired,
                sql: wired.sql === undefined ? undefined : renameSql(wired.sql),
                from: swapName(wired.from),
                steps: wired.steps?.map((step) => ({
                    ...step,
                    join: step.join ? {...step.join, with: swapName(step.join.with) ?? ''} : undefined,
                    union: step.union?.map((name) => swapName(name) ?? name),
                })).map(clean),
            };
        }).map(clean),
    };
}

function clean<T extends object>(item: T): T {
    return Object.fromEntries(Object.entries(item).filter(([, value]) => value !== undefined)) as T;
}

export function renameGold(spec: Spec, index: number, name: string): Spec {
    const before = spec.gold![index].name;
    const renamed = renameRelation(spec, before, name);
    return {
        ...renamed,
        gold: replaceAt(renamed.gold!, index, (table) => ({...table, name})),
        serve: renamed.serve?.map((served) => (served.from === `${GOLD}${before}` ? {...served, from: `${GOLD}${name}`} : served)),
    };
}

export function renameReference(spec: Spec, index: number, name: string): Spec {
    const renamed = renameRelation(spec, spec.references![index].name, name);
    return {...renamed, references: replaceAt(renamed.references!, index, (reference) => ({...reference, name}))};
}

export function renameInput(spec: Spec, before: string, after: string): Spec {
    const renamed = renameRelation(spec, before, after);
    return {
        ...renamed,
        inputs: Object.fromEntries(Object.entries(renamed.inputs ?? {}).map(([alias, table]) => [alias === before ? after : alias, table])),
    };
}

export function canConnect(spec: Spec, from: DesignNode, to: DesignNode): boolean {
    if (from.id === to.id) return false;
    switch (to.kind) {
        case 'serve':
            return from.kind === 'history' || (from.kind === 'gold' && from.stored !== false);
        case 'gold': {
            const table = spec.gold?.[to.index!];
            if (!table || bodyOf(table) === 'sql' || !isRelation(from)) return false;
            return !(from.kind === 'gold' && reaches(spec, to.label, from.label));
        }
        case 'step':
            return from.kind === 'reference';
        default:
            return false;
    }
}

function isRelation(node: DesignNode) {
    return ['history', 'reference', 'input', 'gold'].includes(node.kind);
}

function reaches(spec: Spec, from: string, target: string): boolean {
    const tables = new Map((spec.gold ?? []).map((table) => [table.name, table]));
    const seen = new Set<string>();
    const pending = [from];
    while (pending.length) {
        const name = pending.pop()!;
        if (name === target) return true;
        if (seen.has(name)) continue;
        seen.add(name);
        for (const [candidate, table] of tables) {
            if (goldReads(table).includes(name)) pending.push(candidate);
        }
    }
    return false;
}

function relationName(node: DesignNode) {
    return node.kind === 'history' ? CURRENT : node.label;
}

export function connect(spec: Spec, from: DesignNode, to: DesignNode): Spec {
    if (!canConnect(spec, from, to)) return spec;
    if (to.kind === 'serve') {
        const source = from.kind === 'history' ? CURRENT : `${GOLD}${from.label}`;
        return {...spec, serve: replaceAt(spec.serve!, to.index!, (served) => ({...served, from: source}))};
    }
    if (to.kind === 'gold' && bodyOf(spec.gold![to.index!]) === 'steps') {
        const relation = relationName(from);
        return {
            ...spec,
            gold: replaceAt(spec.gold!, to.index!, (table) => {
                if (!table.from) return {...table, from: relation};
                if (goldReads(table).includes(relation)) return table;
                return {...table, steps: [...(table.steps ?? []), {join: {with: relation, type: 'left', on: {}}}]};
            }),
        };
    }
    const [input, source] = from.kind === 'history' ? [ROWS, CURRENT]
        : from.kind === 'reference' ? [from.label, `${REFERENCE}${from.label}`] : [from.label, from.label];
    return rewireInputs(spec, to, (inputs) => ({...inputs, [input]: source}));
}

export function disconnect(spec: Spec, from: DesignNode, to: DesignNode): Spec {
    if (to.kind === 'gold' && bodyOf(spec.gold![to.index!]) === 'steps') {
        const relation = relationName(from);
        return {
            ...spec,
            gold: replaceAt(spec.gold!, to.index!, (table) => clean({
                ...table,
                from: table.from === relation ? undefined : table.from,
                steps: (table.steps ?? [])
                    .filter((step) => step.join?.with !== relation)
                    .map((step) => (step.union ? {...step, union: step.union.filter((name) => name !== relation)} : step))
                    .filter((step) => !step.union || step.union.length > 0),
            })),
        };
    }
    const sources = new Set(from.kind === 'history' ? [CURRENT, ROWS] : [from.label, `${REFERENCE}${from.label}`]);
    return rewireInputs(spec, to, (inputs) => Object.fromEntries(Object.entries(inputs).filter(([, value]) => !sources.has(value))));
}

function rewireInputs(spec: Spec, to: DesignNode, change: (inputs: Record<string, string>) => Record<string, string>): Spec {
    const wire = <T extends Wired>(item: T): T => ({...item, inputs: change(item.inputs ?? {})});
    if (to.kind === 'gold') return {...spec, gold: replaceAt(spec.gold!, to.index!, wire)};
    if (to.kind === 'step') return {...spec, silver: {...spec.silver, steps: replaceAt(spec.silver!.steps!, to.index!, wire)}};
    return spec;
}

export function nodeOfProblem(spec: Spec, message: string, path?: string): string {
    const ids = idsOf(spec);
    const named = (kind: 'gold' | 'serve' | 'reference' | 'step' | 'input', names: (string | undefined)[], prefix: string) => {
        const index = names.findIndex((name) => !!name && new RegExp(`^${prefix} (name )?'?${escape(name)}(?![\\w])`).test(message));
        return index < 0 ? undefined : ids.lists[kind][index];
    };
    const located = path ? nodeOfPath(path, spec) : PIPELINE;
    return named('gold', (spec.gold ?? []).map((table) => table.name), 'gold')
        ?? named('serve', (spec.serve ?? []).map((served) => served.table), 'serve')
        ?? named('reference', (spec.references ?? []).map((reference) => reference.name), 'reference')
        ?? named('input', Object.keys(spec.inputs ?? {}), 'input')
        ?? named('step', (spec.silver?.steps ?? []).map((step) => step.name), 'silver.steps')
        ?? (located !== PIPELINE ? located : undefined)
        ?? ['source', 'bronze', 'silver', 'history'].find((section) => message.startsWith(section))
        ?? PIPELINE;
}

export function nodeOfPath(path: string, spec: Spec): string {
    const ids = idsOf(spec);
    const [section, part, index] = path.split('.');
    if (['source', 'bronze', 'history'].includes(section)) return section;
    if (section === 'silver') return part === 'steps' && index !== undefined ? ids.lists.step[Number(index)] ?? 'silver' : 'silver';
    if (section === 'inputs' && part !== undefined) return ids.input.get(part) ?? PIPELINE;
    const kind = ({references: 'reference', gold: 'gold', serve: 'serve'} as Record<string, 'reference' | 'gold' | 'serve' | undefined>)[section];
    return kind && part !== undefined ? ids.lists[kind][Number(part)] ?? PIPELINE : PIPELINE;
}

function escape(text: string) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function replaceAt<T>(items: T[], index: number, change: (item: T) => T): T[] {
    return items.map((item, position) => (position === index ? change(item) : item));
}

function mapValues(record: Record<string, string>, change: (value: string) => string) {
    return Object.fromEntries(Object.entries(record).map(([key, value]) => [key, change(value)]));
}

export function unique(base: string, taken: string[]) {
    if (!taken.includes(base)) return base;
    let counter = 2;
    while (taken.includes(`${base}_${counter}`)) counter += 1;
    return `${base}_${counter}`;
}
