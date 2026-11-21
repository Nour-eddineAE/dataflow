import {Highlight, Kbd, Loader, ScrollArea, Text, TextInput, UnstyledButton} from '@mantine/core';
import {useReducedMotion} from '@mantine/hooks';
import {Spotlight, spotlight} from '@mantine/spotlight';
import {IconArrowDown, IconArrowUp, IconCornerDownLeft, IconSearch, IconX} from '../icons';
import {type CSSProperties, type KeyboardEvent, useEffect, useId, useMemo, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useCatalog, useDatasets, useRuns, useSavedQueries, useSchema, RUN_LIMIT} from '../api/hooks';
import {recents} from '../lib/recents';
import {MOTION} from '../lib/motion';
import {ICON, ICON_HERO, ICON_LARGE, ICON_BIG, ICON_SMALL} from '../lib/icons';
import {type Item, type Scope, SCOPES, searchIndex, searchResults} from '../lib/searchIndex';
import './SearchSpotlight.css';
import {DIALOG_WIDTH} from './Page';

const EMPTY_ICON = 24;

export function SearchSpotlight({onShortcuts}: { onShortcuts: () => void }) {
    const navigate = useNavigate();
    const sources = {datasets: useDatasets(), catalog: useCatalog(), runs: useRuns(undefined, RUN_LIMIT.watched), schema: useSchema(), saved: useSavedQueries()};
    const reducedMotion = useReducedMotion();
    const [opened, setOpened] = useState(recents);
    const [query, setQuery] = useState('');
    const [scope, setScope] = useState<Scope>();
    const [activeId, setActiveId] = useState<string>();
    const input = useRef<HTMLInputElement>(null);
    const options = useRef(new Map<string, HTMLButtonElement>());
    const listId = `search-${useId().replace(/:/g, '')}`;
    const {datasets, catalog, runs, schema, saved} = sources;

    const items = useMemo(() => searchIndex({
        datasets: datasets.data, catalog: catalog.data, runs: runs.data, schema: schema.data, saved: saved.data, opened,
    }, onShortcuts), [datasets.data, catalog.data, runs.data, schema.data, saved.data, opened, onShortcuts]);

    const results = useMemo(() => searchResults(items, query, scope), [items, query, scope]);

    const active = results.find((item) => item.id === activeId) ?? results[0];
    const loading = Object.values(sources).some((source) => source.isPending && source.isFetching);
    const unavailable = Object.values(sources).some((source) => source.isError);
    const optionId = (id: string) => `${listId}-${encodeURIComponent(id)}`;
    const terms = query.trim().split(/\s+/).filter(Boolean);

    useEffect(() => {
        if (active) options.current.get(active.id)?.scrollIntoView({block: 'nearest'});
    }, [active]);

    const open = (item: Item) => {
        spotlight.close();
        if (item.run) item.run();
        else if (item.to) navigate(item.to);
    };
    const changeQuery = (value: string) => {
        setQuery(value);
        setActiveId(undefined);
    };
    const keyboard = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.nativeEvent.isComposing) return;
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            if (!results.length) return;
            const index = results.findIndex((item) => item.id === active?.id);
            setActiveId(results[(index + (event.key === 'ArrowDown' ? 1 : -1) + results.length) % results.length]?.id);
        } else if (event.key === 'Enter' && active) {
            event.preventDefault();
            open(active);
        } else if (event.key === 'Backspace' && !query && scope) {
            setScope(undefined);
        }
    };

    return (
        <Spotlight.Root attributes={{content: {'aria-label': 'Search'}}} shortcut={['mod + K', 'mod + P']} size={DIALOG_WIDTH}
                        query={query} onQueryChange={changeQuery} yOffset="clamp(16px, 9dvh, 72px)" xOffset="sm"
                        transitionProps={{transition: 'pop', duration: reducedMotion ? 0 : MOTION.base}}
                        overlayProps={{backgroundOpacity: 0.3, blur: 3}}
                        classNames={{content: 'df-search-modal', body: 'df-search-body'}}
                        onSpotlightOpen={() => {
                            setOpened(recents());
                            setActiveId(undefined);
                        }}>
            <div className="df-search-head">
                <IconSearch {...ICON_HERO} className="df-search-head-icon"/>
                <TextInput ref={input} variant="unstyled" placeholder="Search pipelines, tables, columns, queries…" aria-label="Search"
                           value={query} onChange={(event) => changeQuery(event.currentTarget.value)} onKeyDown={keyboard}
                           className="df-search-input" role="combobox" aria-autocomplete="list" aria-expanded="true"
                           aria-controls={listId} aria-activedescendant={active ? optionId(active.id) : undefined}
                           autoComplete="off" spellCheck={false} data-autofocus/>
                <span className="df-search-progress" role="status" aria-label={loading ? 'Loading' : undefined}>
                    {loading && <Loader {...ICON_SMALL}/>}
                </span>
                <UnstyledButton className="df-search-close" aria-label="Close search" onClick={() => spotlight.close()}>
                    <IconX {...ICON_LARGE}/>
                </UnstyledButton>
            </div>
            <div className="df-search-scopes" role="toolbar" aria-label="Search in">
                {SCOPES.map((entry) => (
                    <UnstyledButton key={entry.value} className="df-search-scope" aria-pressed={scope === entry.value}
                                    data-active={scope === entry.value || undefined}
                                    onMouseDown={(event) => event.preventDefault()}
                                    onClick={() => {
                                        setScope(scope === entry.value ? undefined : entry.value);
                                        setActiveId(undefined);
                                        input.current?.focus();
                                    }}>
                        <entry.icon {...ICON}/>
                        <span>{entry.label}</span>
                    </UnstyledButton>
                ))}
            </div>
            <ScrollArea className="df-search-results" type="auto" scrollbarSize={6}>
                <div id={listId} role="listbox" aria-label="Search results" aria-busy={loading}>
                    {results.map((item, index) => (
                        <div key={item.id} role="presentation">
                            {item.group !== results[index - 1]?.group && <div className="df-search-group" role="presentation">{item.group}</div>}
                            <UnstyledButton id={optionId(item.id)} role="option" aria-selected={item.id === active?.id} tabIndex={-1}
                                            ref={(element) => {
                                                if (element) options.current.set(item.id, element);
                                                else options.current.delete(item.id);
                                            }}
                                            className="df-search-result" data-selected={item.id === active?.id || undefined}
                                            style={{'--df-search-tone': item.tone} as CSSProperties}
                                            onMouseDown={(event) => event.preventDefault()}
                                            onPointerMove={(event) => {
                                                if (event.movementX || event.movementY) setActiveId(item.id);
                                            }}
                                            onClick={() => open(item)}>
                                <span className="df-search-result-icon"><item.icon {...ICON_LARGE}/></span>
                                <span className="df-search-result-body">
                                    <Highlight component="span" className="df-search-result-title" highlight={terms}>{item.label}</Highlight>
                                    {item.description && <span className="df-search-result-description">{item.description}</span>}
                                </span>
                                {item.meta && <span className="df-search-result-meta">{item.meta}</span>}
                            </UnstyledButton>
                        </div>
                    ))}
                </div>
                {!results.length && (
                    <div className="df-search-empty" role="status">
                        {loading ? <Loader {...ICON_BIG}/> : <IconSearch size={EMPTY_ICON}/>}
                        <Text size="sm" fw={500}>{loading ? 'Loading' : 'No matches'}</Text>
                    </div>
                )}
            </ScrollArea>
            <div className="df-search-footer">
                <span><Kbd><IconArrowUp/><IconArrowDown/></Kbd> Navigate</span>
                <span><Kbd><IconCornerDownLeft/></Kbd> Open</span>
                <span><Kbd>Esc</Kbd> Close</span>
                <span className="df-search-footer-end">{unavailable ? 'Some results are unavailable' : `${results.length} results`}</span>
            </div>
        </Spotlight.Root>
    );
}
