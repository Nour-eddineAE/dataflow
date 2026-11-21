import {Box, Text} from '@mantine/core';
import {IconClearAll, IconRefresh} from '../icons';
import {useEffect, useRef, useState} from 'react';
import {useLoaderData, useSearchParams} from 'react-router-dom';
import {useRunSql, useSavedQueries, useSaveQuery, useSchema, useSqlFunctions} from '../api/hooks';
import type {SavedQuery} from '../api/types';
import {IconAction} from '../components/actions/IconAction';
import {useShortcuts} from '../components/actions/useShortcuts';
import {CodeEditor, type EditorHandle} from '../components/CodeEditor';
import {Pane, PaneLayout, PaneMain} from '../components/layout/Pane';
import {SHORTCUTS} from '../components/actions/shortcut';
import {TabStrip} from '../components/layout/Tabs';
import {SchemaBrowser} from '../components/SchemaBrowser';
import {insertAtCursor} from '../lib/editorInsert';
import {monaco, setSqlFunctions} from '../lib/monaco';
import {closeTab, createTab, newTabId, openLink, type QueryTab, restoreTabs, savedOf, statusOf} from '../lib/queryTabs';
import {errorRange} from '../lib/sqlErrors';
import {statementAt, statements} from '../lib/statements';
import {load, save} from '../lib/storage';
import {PAGE} from '../lib/links';
import {pageName} from '../lib/navigation';
import {QueryTabs} from './sql/QueryTabs';
import type {loadQueryLink} from './sql/queryLink';
import {type Outcome, Results, type ResultView} from './sql/Results';
import {forgetClosed, forgetResult, keepResult, keptResults} from '../lib/keptResults';
import {type SaveDraft, SaveQueryModal} from './sql/SaveQueryModal';
import {type SqlCommands, SqlToolbar, type Undoable} from './sql/SqlToolbar';
import {type Past, QueryHistory, SavedQueries, type Tool, TOOL_TABS, TOOLS} from './sql/SqlTools';

import './sql/Sql.css';
import {notifyDone, notifyFailure} from '../components/notify';
import {FormModal} from '../components/FormModal';

type Picked = { text: string; start: number };

const TABS_KEY = 'df.sql.tabs.v3';
const LEGACY_TABS_KEY = 'df.sql.tabs.v2';
const ACTIVE_KEY = 'df.sql.active.v2';
const HISTORY_KEY = 'df.sql.history';
const TOOL_KEY = 'df.sql.tool';
const RESULTS_KEY = 'df.sql.results.hidden';
const RUN_MARKERS = 'sql-run';
const FLASH_MS = 900;
const HISTORY_SIZE = 50;

const modelPath = (id: string) => `inmemory://query/${id}.sql`;

export function SqlPage() {
    const [params, setParams] = useSearchParams();
    const [tabs, setTabs] = useState<QueryTab[]>(() => restoreTabs(load(TABS_KEY, null) ?? load(LEGACY_TABS_KEY, [])));
    const [active, setActive] = useState<string>(() => load(ACTIVE_KEY, tabs[0].id));
    const [tool, setTool] = useState<Tool>(() => {
        const stored = load<string>(TOOL_KEY, 'schema');
        return stored in TOOLS ? stored as Tool : 'schema';
    });
    const [hidden, setHidden] = useState(() => load(RESULTS_KEY, false));
    const [view, setView] = useState<ResultView>('results');
    const [outcomes, setOutcomes] = useState<Record<string, Outcome>>(() => Object.fromEntries(
        Object.entries(keptResults(window.sessionStorage, tabs.map((tab) => tab.id))).map(([id, result]) => [id, {result}])));
    const [running, setRunning] = useState<Record<string, boolean>>({});
    const [history, setHistory] = useState<Past[]>(() => load(HISTORY_KEY, []));
    const [renaming, setRenaming] = useState<string | null>(null);
    const [draft, setDraft] = useState<SaveDraft | null>(null);
    const [undoable, setUndoable] = useState<Undoable>({undo: false, redo: false});
    const frame = useRef<HTMLDivElement>(null);
    const editor = useRef<EditorHandle | null>(null);
    const latestTabs = useRef(tabs);
    const focusNext = useRef(false);
    const run = useRunSql();
    const persist = useSaveQuery();
    const schema = useSchema();
    const saved = useSavedQueries();
    const {data: functions} = useSqlFunctions();
    const link = useLoaderData<typeof loadQueryLink>();
    const [linked, setLinked] = useState<string>();
    if (link && link.tab !== linked) {
        setLinked(link.tab);
        const next = openLink(tabs, link);
        setTabs(next.tabs);
        setActive(next.active);
    }

    useEffect(() => {
        latestTabs.current = tabs;
        save(TABS_KEY, tabs);
        forgetClosed(window.sessionStorage, tabs.map((tab) => tab.id));
    }, [tabs]);
    useEffect(() => save(ACTIVE_KEY, active), [active]);
    useEffect(() => save(HISTORY_KEY, history), [history]);
    useEffect(() => save(TOOL_KEY, tool), [tool]);
    useEffect(() => save(RESULTS_KEY, hidden), [hidden]);
    useEffect(() => setSqlFunctions(functions), [functions]);
    useEffect(() => {
        const open = new Set(tabs.map((tab) => modelPath(tab.id)));
        monaco.editor.getModels()
            .filter((model) => model.uri.scheme === 'inmemory' && model.uri.authority === 'query' && !open.has(model.uri.toString()))
            .forEach((model) => model.dispose());
    }, [tabs]);

    useEffect(() => {
        if (!focusNext.current || !editor.current) return;
        focusNext.current = false;
        editor.current.focus();
    }, [active]);

    const current = tabs.find((tab) => tab.id === active) ?? tabs[0];
    const status = statusOf(current);
    const busy = !!running[current.id];

    const update = (id: string, patch: Partial<QueryTab>) =>
        setTabs((all) => all.map((tab) => (tab.id === id ? {...tab, ...patch} : tab)));

    const add = (sql = '') => {
        const tab = createTab(latestTabs.current, sql);
        focusNext.current = true;
        setTabs((all) => [...all, tab]);
        setActive(tab.id);
        return tab;
    };

    const openSaved = (query: SavedQuery) => {
        const next = openLink(latestTabs.current, {tab: newTabId(), saved: query});
        setTabs(next.tabs);
        setActive(next.active);
    };

    const [closing, setClosing] = useState<QueryTab>();
    const requestClose = (id: string) => {
        const tab = latestTabs.current.find((candidate) => candidate.id === id);
        if (tab && statusOf(tab) === 'unsaved') setClosing(tab);
        else close(id);
    };
    const close = (id: string) => {
        const next = closeTab(latestTabs.current, id, active);
        setTabs(next.tabs);
        setActive(next.active);
        setOutcomes(({[id]: _, ...rest}) => rest);
    };

    useEffect(() => {
        if (!params.toString()) return;
        if (link && 'sql' in link) {
            if (editor.current) editor.current.focus();
            else focusNext.current = true;
        }
        setParams({}, {replace: true});
    }, [params, link, setParams]);

    const runText = (id: string, picked: Picked, limit: number, model: monaco.editor.ITextModel | null) => {
        const sql = picked.text;
        unmark(model);
        setRunning((all) => ({...all, [id]: true}));
        setHidden(false);
        run.mutateAsync({sql, limit})
            .then((result) => {
                setOutcomes((all) => ({...all, [id]: {result}}));
                keepResult(window.sessionStorage, id, result);
                setHistory((past) => [{sql, at: new Date().toISOString(), rows: result.rows.length, ms: result.elapsedMs},
                    ...past.filter((entry) => entry.sql !== sql)].slice(0, HISTORY_SIZE));
            })
            .catch((error: Error) => {
                setOutcomes((all) => ({...all, [id]: {error}}));
                forgetResult(window.sessionStorage, id);
                mark(model, picked, error.message);
            })
            .finally(() => setRunning(({[id]: _, ...rest}) => rest));
    };

    const execute = () => {
        if (busy) return;
        editor.current?.trigger('run', 'hideSuggestWidget', null);
        const picked = toRun(editor.current) ?? {text: current.sql, start: 0};
        if (!picked.text.trim()) return;
        runText(current.id, picked, current.limit, editor.current?.getModel() ?? null);
    };

    const preview = (table: string) => {
        const tab = add(`SELECT *\nFROM ${table}\nLIMIT 100`);
        runText(tab.id, {text: tab.sql, start: 0}, tab.limit, null);
    };

    const insert = (text: string) => insertAtCursor(editor.current, text);

    const store = (tab: QueryTab, choice: SaveDraft, id?: string) =>
        persist.mutate({id, name: choice.name.trim(), sql: tab.sql, shared: choice.shared}, {
            onSuccess: (query) => {
                update(tab.id, {title: query.name, saved: savedOf(query)});
                setDraft(null);
                notifyDone({message: `Saved ${query.name}`});
            },
            onError: notifyFailure('Not saved'),
        });

    const onSave = () => {
        if (!current.sql.trim() || status === 'saved' || persist.isPending) return;
        if (current.saved) store(current, {name: current.title, shared: current.saved.shared}, current.saved.id);
        else setDraft({name: current.title, shared: false});
    };

    const copyLink = () => {
        if (!current.saved) return;
        void navigator.clipboard.writeText(`${window.location.origin}/sql?saved=${current.saved.id}`)
            .then(() => notifyDone({message: 'Link copied'}));
    };

    const commands: SqlCommands = {
        run: execute,
        limit: (limit) => update(current.id, {limit}),
        edit: (command) => editor.current?.trigger('toolbar', command, null),
        format: () => void editor.current?.getAction('editor.action.formatDocument')?.run(),
        save: onSave,
        copyLink,
        saveAs: () => setDraft({name: `${current.title} copy`, shared: false}),
        rename: () => setRenaming(current.id),
        close: () => requestClose(current.id),
    };

    useShortcuts(frame, {[SHORTCUTS.run]: execute, [SHORTCUTS.save]: onSave});

    const toolActions = {
        schema: <IconAction label="Refresh" icon={IconRefresh} loading={schema.isFetching} onClick={() => void schema.refetch()}/>,
        saved: <IconAction label="Refresh" icon={IconRefresh} loading={saved.isFetching} onClick={() => void saved.refetch()}/>,
        history: <IconAction label="Clear history" icon={IconClearAll} disabled={!history.length} onClick={() => setHistory([])}/>,
    }[tool];

    return (
        <Box ref={frame} h="100%">
            <PaneLayout>
                <Pane id="sql.tools" side="left" title={TOOLS[tool].label} icon={TOOLS[tool].icon} defaultSize={280} min={240}
                      max={480} header={<TabStrip value={tool} onChange={setTool} items={TOOL_TABS}/>} actions={toolActions}>
                    {tool === 'schema' ? <SchemaBrowser schema={schema.data} error={schema.error} onInsert={insert} onPreview={preview}/>
                        : tool === 'saved' ? <SavedQueries onOpen={openSaved}/>
                            : <QueryHistory history={history} onPick={(sql) => add(sql)}/>}
                </Pane>
                <PaneMain title={pageName(PAGE.sql)}>
                    <QueryTabs tabs={tabs} active={current.id} renaming={renaming} onSelect={setActive} onClose={requestClose}
                               onRename={(id, title) => update(id, {title})} onRenaming={setRenaming} onAdd={() => add()}/>
                    <SqlToolbar tab={current} busy={busy} saving={persist.isPending} undoable={undoable} commands={commands}/>
                    <div className="df-sql-editor">
                        <CodeEditor path={modelPath(current.id)} value={current.sql} language="sql" schema={schema.data}
                                    onRun={execute} onSave={onSave}
                                    onChange={(sql) => {
                                        unmark(editor.current?.getModel() ?? null);
                                        update(current.id, {sql});
                                    }}
                                    onEditor={(instance) => {
                                        editor.current = instance;
                                        if (focusNext.current) {
                                            focusNext.current = false;
                                            instance.focus();
                                        }
                                        const refresh = () => {
                                            const model = instance.getModel();
                                            setUndoable({undo: !!model?.canUndo(), redo: !!model?.canRedo()});
                                        };
                                        instance.onDidChangeModel(refresh);
                                        instance.onDidChangeModelContent(refresh);
                                        refresh();
                                    }}/>
                    </div>
                    <Results outcome={outcomes[current.id]} running={busy} name={current.title} view={view} onView={setView}
                             chart={current.chart} onChart={(chart) => update(current.id, {chart})}
                             collapsed={hidden} onCollapsedChange={setHidden}/>
                </PaneMain>
            </PaneLayout>
            <SaveQueryModal draft={draft} loading={persist.isPending} onChange={setDraft} onClose={() => setDraft(null)}
                            onSubmit={(choice) => store(current, choice)}/>
            <FormModal opened={!!closing} onClose={() => setClosing(undefined)} title={`Close ${closing?.title ?? ''}?`}
                       submit="Close" danger onSubmit={() => {
                if (closing) close(closing.id);
                setClosing(undefined);
            }}>
                <Text size="sm">Its SQL is not saved.</Text>
            </FormModal>
        </Box>
    );
}

function toRun(editor: EditorHandle | null): Picked | undefined {
    const model = editor?.getModel();
    const selection = editor?.getSelection();
    if (!editor || !model || !selection) return undefined;
    if (!selection.isEmpty()) {
        return {text: model.getValueInRange(selection), start: model.getOffsetAt(selection.getStartPosition())};
    }
    const all = statements(model.getValue());
    if (all.length < 2) return all[0] ?? {text: '', start: 0};
    const statement = statementAt(all, model.getOffsetAt(selection.getPosition()))!;
    const from = model.getPositionAt(statement.start);
    const to = model.getPositionAt(statement.end);
    const flash = editor.createDecorationsCollection([{
        range: {startLineNumber: from.lineNumber, startColumn: from.column, endLineNumber: to.lineNumber, endColumn: to.column},
        options: {className: 'df-statement-flash'},
    }]);
    window.setTimeout(() => flash.clear(), FLASH_MS);
    return statement;
}

function mark(model: monaco.editor.ITextModel | null, picked: Picked, message: string) {
    const range = model && !model.isDisposed() ? errorRange(message, picked.text) : undefined;
    if (!model || !range) return;
    const from = model.getPositionAt(picked.start + range.start);
    const to = model.getPositionAt(picked.start + range.end);
    monaco.editor.setModelMarkers(model, RUN_MARKERS, [{
        severity: monaco.MarkerSeverity.Error,
        message,
        startLineNumber: from.lineNumber,
        startColumn: from.column,
        endLineNumber: to.lineNumber,
        endColumn: to.column,
    }]);
}

function unmark(model: monaco.editor.ITextModel | null) {
    if (model && !model.isDisposed() && monaco.editor.getModelMarkers({resource: model.uri, owner: RUN_MARKERS}).length) {
        monaco.editor.setModelMarkers(model, RUN_MARKERS, []);
    }
}
