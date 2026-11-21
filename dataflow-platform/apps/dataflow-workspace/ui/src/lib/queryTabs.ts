import type {SavedQuery} from '../api/types';
import type {DocumentStatus} from '../components/DocumentState';
import {type ChartChoice, restoreChart} from './chartChoice';

export type Saved = { id: string; title: string; sql: string; shared: boolean };
export type QueryTab = { id: string; title: string; sql: string; limit: number; saved?: Saved; chart?: ChartChoice };
export type QueryLink = { tab: string; sql: string } | { tab: string; saved: SavedQuery };
type Opened = { tabs: QueryTab[]; active: string };

type Legacy = Partial<QueryTab> & { savedId?: string; shared?: boolean };

const ROW_LIMIT = {few: 100, usual: 1_000, most: 10_000};
export const LIMITS = Object.values(ROW_LIMIT);
const DEFAULT_LIMIT = ROW_LIMIT.usual;
const ID_RADIX = 36;
const ID_END = 10;

export const newTabId = () => Math.random().toString(ID_RADIX).slice(2, ID_END);

export function createTab(tabs: QueryTab[], sql = '', id = newTabId()): QueryTab {
    return {id, title: nextTitle(tabs), sql, limit: DEFAULT_LIMIT};
}

export function nextTitle(tabs: QueryTab[]) {
    const used = new Set(tabs.map((tab) => tab.title));
    let number = 1;
    while (used.has(`Query ${number}`)) number += 1;
    return `Query ${number}`;
}

export function savedTab(tabs: QueryTab[], query: SavedQuery, id = newTabId()): QueryTab {
    return {...createTab(tabs, query.sql, id), title: query.name, saved: savedOf(query)};
}

export function openLink(tabs: QueryTab[], link: QueryLink): Opened {
    if ('saved' in link) {
        const open = tabs.find((tab) => tab.saved?.id === link.saved.id);
        if (open) return {tabs, active: open.id};
    }
    const tab = 'saved' in link ? savedTab(tabs, link.saved, link.tab) : createTab(tabs, link.sql, link.tab);
    return {tabs: [...tabs, tab], active: tab.id};
}

export function savedOf(query: SavedQuery): Saved {
    return {id: query.id, title: query.name, sql: query.sql, shared: query.shared};
}

export function statusOf(tab: QueryTab): DocumentStatus {
    if (!tab.saved) return tab.sql.trim() ? 'unsaved' : 'draft';
    return tab.saved.sql === tab.sql && tab.saved.title === tab.title ? 'saved' : 'unsaved';
}

export function closeTab(tabs: QueryTab[], id: string, active: string): Opened {
    const index = tabs.findIndex((tab) => tab.id === id);
    const left = tabs.filter((tab) => tab.id !== id);
    if (!left.length) {
        const fresh = createTab([]);
        return {tabs: [fresh], active: fresh.id};
    }
    return {tabs: left, active: id === active ? left[Math.max(0, index - 1)].id : active};
}

export function restoreTabs(stored: unknown): QueryTab[] {
    const tabs = (Array.isArray(stored) ? stored as Legacy[] : [])
        .filter((tab) => typeof tab?.id === 'string' && typeof tab.sql === 'string')
        .map((tab) => ({
            id: tab.id!,
            title: tab.title || 'Query',
            sql: tab.sql!,
            limit: LIMITS.includes(tab.limit ?? 0) ? tab.limit! : DEFAULT_LIMIT,
            saved: tab.saved ?? (tab.savedId ? {id: tab.savedId, title: tab.title ?? '', sql: tab.sql!, shared: !!tab.shared} : undefined),
            chart: restoreChart(tab.chart),
        }));
    return tabs.length ? tabs : [createTab([])];
}
