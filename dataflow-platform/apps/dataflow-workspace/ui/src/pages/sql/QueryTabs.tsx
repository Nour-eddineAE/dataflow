import {IconPlus, IconSql, IconX} from '../../icons';
import {useEffect, useRef} from 'react';
import {IconAction} from '../../components/actions/IconAction';
import {type QueryTab, statusOf} from '../../lib/queryTabs';
import {ICON, ICON_SMALL} from '../../lib/icons';
import {tabAfterKey} from '../../lib/tabKeys';

const CLOSE_KEYS = ['Delete', 'Backspace'];

type Props = {
    tabs: QueryTab[];
    active: string;
    renaming: string | null;
    onSelect: (id: string) => void;
    onClose: (id: string) => void;
    onRename: (id: string, title: string) => void;
    onRenaming: (id: string | null) => void;
    onAdd: () => void;
};

export function QueryTabs({tabs, active, renaming, onSelect, onClose, onRename, onRenaming, onAdd}: Props) {
    const strip = useRef<HTMLDivElement>(null);

    useEffect(() => {
        strip.current?.querySelector('[data-active]')?.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'smooth'});
    }, [active, tabs.length]);

    return (
        <div className="df-query-tabs">
            <div ref={strip} className="df-query-tabs-strip" role="tablist" aria-label="Queries"
                 onKeyDown={(event) => {
                     if ((event.target as HTMLElement).getAttribute('role') !== 'tab') return;
                     const next = tabAfterKey(tabs.map((tab) => ({value: tab.id})), active, event.key);
                     if (next === undefined) return;
                     event.preventDefault();
                     onSelect(next);
                     strip.current?.querySelector<HTMLElement>(`[data-tab="${next}"]`)?.focus();
                 }}
                 onWheel={(event) => {
                     if (strip.current && Math.abs(event.deltaY) > Math.abs(event.deltaX)) strip.current.scrollLeft += event.deltaY;
                 }}>
                {tabs.map((tab) => (
                    <div key={tab.id} role="tab" tabIndex={tab.id === active ? 0 : -1} aria-selected={tab.id === active} className="df-query-tab"
                         data-tab={tab.id}
                         data-active={tab.id === active || undefined} data-status={statusOf(tab)} title={tab.title}
                         onClick={() => onSelect(tab.id)} onDoubleClick={() => onRenaming(tab.id)}
                         onAuxClick={(event) => {
                             if (event.button !== 1) return;
                             event.preventDefault();
                             onClose(tab.id);
                         }}
                         onKeyDown={(event) => {
                             if (event.target !== event.currentTarget) return;
                             if (event.key === 'Enter' || event.key === ' ') onSelect(tab.id);
                             if (event.key === 'F2') onRenaming(tab.id);
                             if (CLOSE_KEYS.includes(event.key)) onClose(tab.id);
                         }}>
                        <IconSql {...ICON} className="df-query-tab-icon"/>
                        {renaming === tab.id
                            ? <RenameInput title={tab.title} onDone={(title) => {
                                if (title !== null) onRename(tab.id, title);
                                onRenaming(null);
                            }}/>
                            : <span className="df-query-tab-title">{tab.title}</span>}
                        <button type="button" className="df-query-tab-close" aria-label={`Close ${tab.title}`} tabIndex={tab.id === active ? 0 : -1}
                                onClick={(event) => {
                                    event.stopPropagation();
                                    onClose(tab.id);
                                }}>
                            <span className="df-query-tab-dot"/>
                            <IconX {...ICON_SMALL}/>
                        </button>
                    </div>
                ))}
            </div>
            <IconAction label="New query" icon={IconPlus} onClick={onAdd}/>
        </div>
    );
}

function RenameInput({title, onDone}: { title: string; onDone: (title: string | null) => void }) {
    const done = useRef(false);
    const finish = (value: string | null) => {
        if (done.current) return;
        done.current = true;
        onDone(value?.trim() || null);
    };
    return (
        <input className="df-query-tab-input" defaultValue={title} aria-label="Query name" autoFocus
               onFocus={(event) => event.currentTarget.select()}
               onClick={(event) => event.stopPropagation()} onDoubleClick={(event) => event.stopPropagation()}
               onBlur={(event) => finish(event.currentTarget.value)}
               onKeyDown={(event) => {
                   event.stopPropagation();
                   if (event.key === 'Enter') finish(event.currentTarget.value);
                   if (event.key === 'Escape') finish(null);
               }}/>
    );
}
