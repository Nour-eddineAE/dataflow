import {UnstyledButton} from '@mantine/core';
import {type Icon, IconBookmark, IconDatabase, IconHistory, IconTrash} from '../../icons';
import {type ReactNode, useMemo, useState} from 'react';
import {useMe, useSavedQueries} from '../../api/hooks';
import type {SavedQuery} from '../../api/types';
import {IconAction} from '../../components/actions/IconAction';
import {shortcutLabel, SHORTCUTS} from '../../components/actions/shortcut';
import {ErrorState, Loading} from '../../components/ErrorState';
import {DeleteQueryModal} from '../../components/DeleteQueryModal';
import {PaneSearch} from '../../components/layout/PaneSearch';
import type {TabItem} from '../../components/layout/Tabs';
import {formatAgo, formatNumber} from '../../lib/format';
import {searchItems} from '../../lib/search';

export type Tool = 'schema' | 'saved' | 'history';
export type Past = { sql: string; at: string; rows: number; ms: number };

export const TOOLS: Record<Tool, { label: string; icon: Icon }> = {
    schema: {label: 'Schema', icon: IconDatabase},
    saved: {label: 'Saved', icon: IconBookmark},
    history: {label: 'Recent', icon: IconHistory},
};

export const TOOL_TABS: TabItem<Tool>[] = (Object.keys(TOOLS) as Tool[]).map((tool) => ({value: tool, label: TOOLS[tool].label}));

const ALL = Number.POSITIVE_INFINITY;

export function SavedQueries({onOpen}: { onOpen: (query: SavedQuery) => void }) {
    const {data, isLoading, error} = useSavedQueries();
    const {data: me} = useMe();
    const [filter, setFilter] = useState('');
    const [doomed, setDoomed] = useState<SavedQuery>();
    const shown = useMemo(() => searchItems((data ?? []).map((query) => ({label: query.name, description: query.sql, query})), filter, ALL),
        [data, filter]);

    return (
        <ToolFrame filter={filter} onFilter={setFilter}>
            {isLoading ? <Loading/> : error ? <ErrorState error={error}/>
                : !shown.length ? <ToolEmpty text={data?.length ? 'Nothing found' : 'No saved queries'} next={data?.length ? undefined : `Save the open query with ${shortcutLabel(SHORTCUTS.save)}`}/>
                    : shown.map(({query}) => (
                        <div key={query.id} className="df-sql-item">
                            <UnstyledButton className="df-sql-item-main" onClick={() => onOpen(query)}>
                                <span className="df-sql-item-title">{query.name}</span>
                                <span className="df-sql-item-meta">
                                    {query.owner === me?.id ? (query.shared ? 'Shared' : 'Private') : query.owner} · {formatAgo(query.updatedAt)}
                                </span>
                            </UnstyledButton>
                            <IconAction label="Delete" icon={IconTrash} disabled={!(query.owner === me?.id || me?.admin)}
                                        onClick={() => setDoomed(query)}/>
                        </div>
                    ))}
            <DeleteQueryModal query={doomed} onClose={() => setDoomed(undefined)}/>
        </ToolFrame>
    );
}

export function QueryHistory({history, onPick}: { history: Past[]; onPick: (sql: string) => void }) {
    const [filter, setFilter] = useState('');
    const shown = useMemo(() => searchItems(history.map((entry) => ({label: entry.sql, entry})), filter, ALL), [history, filter]);
    return (
        <ToolFrame filter={filter} onFilter={setFilter}>
            {!shown.length ? <ToolEmpty text={history.length ? 'Nothing found' : 'No queries yet'} next={history.length ? undefined : `Run one with ${shortcutLabel(SHORTCUTS.run)}`}/>
                : shown.map(({entry}) => (
                    <div key={entry.at} className="df-sql-item">
                        <UnstyledButton className="df-sql-item-main" onClick={() => onPick(entry.sql)}>
                            <span className="df-sql-item-code">{entry.sql}</span>
                            <span className="df-sql-item-meta df-num">
                                {formatAgo(entry.at)} · {formatNumber(entry.rows)} rows · {formatNumber(entry.ms)} ms
                            </span>
                        </UnstyledButton>
                    </div>
                ))}
        </ToolFrame>
    );
}

function ToolFrame({filter, onFilter, children}: { filter: string; onFilter: (filter: string) => void; children: ReactNode }) {
    return (
        <div className="df-sql-tool">
            <PaneSearch value={filter} onChange={onFilter}/>
            <div className="df-sql-tool-list">{children}</div>
        </div>
    );
}

function ToolEmpty({text, next}: { text: string; next?: string }) {
    return (
        <div className="df-sql-tool-empty">
            {text}
            {next && <div className="df-sql-tool-next">{next}</div>}
        </div>
    );
}
