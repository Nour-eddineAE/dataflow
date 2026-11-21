import {
    type Icon,
    IconActivity,
    IconCode,
    IconColumns,
    IconFileCode,
    IconKeyboard,
    IconPlus,
    IconRoute,
    IconServer,
    IconTable,
} from '../icons';
import type {CatalogTree, DatasetSummary, RunSummary, SavedQuery, Schema} from '../api/types';
import {formatAgo, shortRunId} from './format';
import {layer, TONES, tableStyle} from './layers';
import {links, PAGE} from './links';
import {DESTINATIONS} from './navigation';
import type {Recent, RecentKind} from './recents';
import {searchItems} from './search';
import {statusTone} from './status';

const QUERY_KEYWORDS = 200;

const RECENT_ITEMS = 6;
import {shortType} from './types';

export type Scope = 'pipelines' | 'tables' | 'columns' | 'queries' | 'runs';
export type Item = {
    id: string;
    group: string;
    scope?: Scope;
    label: string;
    description?: string;
    keywords?: string[];
    meta?: string;
    icon: Icon;
    tone: string;
    to?: string;
    run?: () => void;
};

export const SCOPES: { value: Scope; label: string; icon: Icon }[] = [
    {value: 'pipelines', label: 'Pipelines', icon: IconRoute},
    {value: 'tables', label: 'Tables', icon: IconTable},
    {value: 'columns', label: 'Columns', icon: IconColumns},
    {value: 'queries', label: 'Queries', icon: IconFileCode},
    {value: 'runs', label: 'Runs', icon: IconActivity},
];

const RECENT: Record<RecentKind, { icon: Icon; label: string; tone: string }> = {
    pipeline: {icon: IconRoute, label: 'Pipeline', tone: TONES.brand},
    table: {icon: IconTable, label: 'Table', tone: TONES.silver},
    served: {icon: IconServer, label: 'Serving table', tone: TONES.serving},
    run: {icon: IconActivity, label: 'Run', tone: TONES.history},
};


export const LIMIT = 24;
const NEUTRAL = TONES.neutral;
const BRAND = TONES.brand;

export type Sources = {
    datasets?: DatasetSummary[];
    catalog?: CatalogTree;
    runs?: RunSummary[];
    schema?: Schema;
    saved?: SavedQuery[];
    opened: Recent[];
};

export function searchIndex(sources: Sources, onShortcuts: () => void): Item[] {
    const pages = DESTINATIONS.map(({label, to, icon, keywords}): Item => ({
        id: `page-${to}`, group: 'Pages', label, to, icon, tone: NEUTRAL, keywords: keywords ? [keywords] : undefined,
    }));
    const actions: Item[] = [
        {id: 'new-query', group: 'Actions', label: 'New query', icon: IconCode, tone: BRAND, to: PAGE.newQuery},
        {id: 'new-pipeline', group: 'Actions', label: 'Create pipeline', icon: IconPlus, tone: BRAND, to: PAGE.newPipeline},
        {id: 'shortcuts', group: 'Actions', label: 'Keyboard shortcuts', icon: IconKeyboard, tone: NEUTRAL, run: onShortcuts},
    ];
    const pipelines = (sources.datasets ?? []).map((dataset): Item => ({
        id: `pipeline-${dataset.dataset}`, group: 'Pipelines', scope: 'pipelines', label: dataset.dataset, description: 'Pipeline',
        keywords: ['pipeline', 'dataset'], icon: IconRoute, tone: BRAND, to: links.pipeline(dataset.dataset),
    }));
    const tables: Item[] = [
        ...(sources.catalog?.datasets ?? []).flatMap((entry) => entry.tables.map((table): Item => ({
            id: `table-${entry.dataset}-${table.id}`, group: 'Tables', scope: 'tables', label: table.sqlName ?? `${entry.dataset}.${table.label}`,
            description: `${entry.dataset} · ${layer(table.layer).title}`, keywords: ['table', entry.dataset, table.id, table.layer],
            icon: tableStyle(table.id, table.layer).icon, tone: layer(table.layer).tone, to: links.table(entry.dataset, table.id),
        }))),
        ...(sources.catalog?.serving ?? []).map((table): Item => ({
            id: `served-${table.table}`, group: 'Tables', scope: 'tables', label: table.sqlName, description: `Serving · ${table.mode}`,
            keywords: ['table', 'serving', table.table], icon: layer('serving').icon, tone: TONES.serving, to: links.served(table.table),
        })),
    ];
    const tableLinks = new Map(tables.map((table) => [table.label, table.to!]));
    const sameColumn = new Map<string, { name: string; type: string; tables: string[]; to: string }>();
    Object.entries(sources.schema ?? {}).forEach(([table, fields]) => {
        const to = tableLinks.get(table);
        if (!to) return;
        fields.forEach((field) => {
            const key = `${field.name}|${field.type}`;
            const known = sameColumn.get(key);
            if (known) known.tables.push(table);
            else sameColumn.set(key, {name: field.name, type: field.type, tables: [table], to});
        });
    });
    const columns = [...sameColumn.values()].map(({name, type, tables: owners, to}): Item => ({
        id: `column-${owners[0]}-${name}`, group: 'Columns', scope: 'columns', label: name,
        description: owners.join(', '), meta: shortType(type), keywords: ['column', ...owners, type],
        icon: IconColumns, tone: NEUTRAL, to,
    }));
    const queries = (sources.saved ?? []).map((entry): Item => ({
        id: `query-${entry.id}`, group: 'Queries', scope: 'queries', label: entry.name, description: entry.shared ? 'Shared query' : 'Query',
        meta: formatAgo(entry.updatedAt), keywords: ['query', 'sql', entry.sql.slice(0, QUERY_KEYWORDS)], icon: IconFileCode, tone: TONES.code,
        to: links.savedQuery(entry.id),
    }));
    const recentRuns = (sources.runs ?? []).map((run): Item => ({
        id: `run-${run.runId}`, group: 'Runs', scope: 'runs', label: `${run.dataset ?? run.job} · ${shortRunId(run.runId)}`,
        description: statusTone(run.status).label, meta: formatAgo(run.startedAt ?? run.createdAt),
        keywords: ['run', run.runId, run.status], icon: IconActivity, tone: TONES.history, to: links.run(run.runId),
    }));
    const lately = sources.opened.slice(0, RECENT_ITEMS).map((entry): Item => ({
        id: `recent-${entry.to}`, group: 'Recents', label: entry.label, description: RECENT[entry.kind].label,
        meta: `viewed ${formatAgo(entry.at)}`, icon: RECENT[entry.kind].icon, tone: RECENT[entry.kind].tone, to: entry.to,
    }));
    return [...lately, ...actions, ...pipelines, ...tables, ...queries, ...recentRuns, ...columns, ...pages];
}

export function searchResults(items: Item[], query: string, scope: Scope | undefined): Item[] {
    const text = query.trim();
    const pool = items.filter((item) => (scope ? item.scope === scope : text ? item.group !== 'Recents' : !item.scope && item.group !== 'Pages'));
    const found = searchItems(pool, text, LIMIT);
    const order = [...new Set(found.map((item) => item.group))];
    return [...found].sort((left, right) => order.indexOf(left.group) - order.indexOf(right.group));
}
