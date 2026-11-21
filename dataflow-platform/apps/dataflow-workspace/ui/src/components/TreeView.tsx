import {Loader} from '@mantine/core';
import {IconChevronRight} from '../icons';
import {useVirtualizer} from '@tanstack/react-virtual';
import {type KeyboardEvent, type ReactNode, useCallback, useEffect, useId, useMemo, useRef, useState} from 'react';
import {load, save} from '../lib/storage';
import {ICON_SMALL} from '../lib/icons';

export type TreeNode = {
    id: string;
    label: string;
    icon?: ReactNode;
    children?: TreeNode[];
    loading?: boolean;
    dimmed?: boolean;
    strike?: boolean;
    badge?: ReactNode;
    actions?: ReactNode;
};

type Row = { node: TreeNode; depth: number };

const ROW_HEIGHT = 28;
const INDENT = 14;
const ROW_PADDING = 6;
const ROW_LOADER = 10;
const DIMMED_OPACITY = 0.55;

export function useTreeState(storageKey: string, initial: string[] = [], selected: string[] = []) {
    const [expanded, setExpanded] = useState<Set<string>>(() => new Set(load<string[]>(storageKey, initial)));
    const selection = selected.join('\n');
    const [revealed, setRevealed] = useState<string>();
    if (selection !== revealed) {
        setRevealed(selection);
        if (selected.some((id) => !expanded.has(id))) setExpanded(new Set([...expanded, ...selected]));
    }
    useEffect(() => save(storageKey, [...expanded]), [storageKey, expanded]);
    const toggle = useCallback((id: string, open?: boolean) => setExpanded((current) => {
        const next = new Set(current);
        if (open ?? !next.has(id)) next.add(id);
        else next.delete(id);
        return next;
    }), []);
    return {expanded, toggle};
}

function flatten(nodes: TreeNode[], expanded: Set<string>, filter: string): Row[] {
    const rows: Row[] = [];
    const wanted = filter.trim().toLowerCase();
    const matches = (node: TreeNode): boolean =>
        node.label.toLowerCase().includes(wanted) || (node.children ?? []).some(matches);
    const walk = (list: TreeNode[], depth: number) => {
        for (const node of list) {
            if (wanted && !matches(node)) continue;
            rows.push({node, depth});
            if (node.children && (wanted || expanded.has(node.id))) walk(node.children, depth + 1);
        }
    };
    walk(nodes, 0);
    return rows;
}

export function TreeView({nodes, expanded, onToggle, selected, onSelect, onOpen, filter = '', empty}: {
    nodes: TreeNode[];
    expanded: Set<string>;
    onToggle: (id: string, open?: boolean) => void;
    selected?: string;
    onSelect?: (node: TreeNode) => void;
    onOpen?: (node: TreeNode) => void;
    filter?: string;
    empty?: ReactNode;
}) {
    const scroller = useRef<HTMLDivElement>(null);
    const base = useId();
    const rows = useMemo(() => flatten(nodes, expanded, filter), [nodes, expanded, filter]);
    const [focus, setFocus] = useState(-1);
    const virtualizer = useVirtualizer({
        count: rows.length,
        getScrollElement: () => scroller.current,
        estimateSize: () => ROW_HEIGHT,
        overscan: 12,
    });

    const selectedIndex = rows.findIndex((row) => row.node.id === selected);
    const cursor = focus >= 0 ? focus : Math.max(selectedIndex, 0);
    const rowId = (index: number) => `${base}-${index}`;
    useEffect(() => {
        if (selectedIndex >= 0) virtualizer.scrollToIndex(selectedIndex, {align: 'auto'});
    }, [selectedIndex, virtualizer]);

    const activate = (row: Row) => {
        if (row.node.children) onToggle(row.node.id);
        onSelect?.(row.node);
    };

    const onKey = (event: KeyboardEvent<HTMLDivElement>) => {
        if (!rows.length) return;
        const row = rows[cursor];
        const move = (index: number) => {
            const next = Math.min(Math.max(index, 0), rows.length - 1);
            setFocus(next);
            virtualizer.scrollToIndex(next, {align: 'auto'});
        };
        if (event.key === 'ArrowDown') move(cursor + 1);
        else if (event.key === 'ArrowUp') move(cursor - 1);
        else if (event.key === 'ArrowRight' && row.node.children && !expanded.has(row.node.id)) onToggle(row.node.id, true);
        else if (event.key === 'ArrowLeft' && row.node.children && expanded.has(row.node.id)) onToggle(row.node.id, false);
        else if (event.key === 'Enter') {
            activate(row);
            onOpen?.(row.node);
        } else return;
        event.preventDefault();
    };

    if (!rows.length && empty) return <div className="df-tree-empty">{empty}</div>;

    return (
        <div ref={scroller} className="df-tree" tabIndex={0} onKeyDown={onKey} onBlur={() => setFocus(-1)} role="tree"
             aria-activedescendant={rows.length ? rowId(cursor) : undefined}>
            <div style={{height: virtualizer.getTotalSize(), position: 'relative'}}>
                {virtualizer.getVirtualItems().map((item) => {
                    const {node, depth} = rows[item.index];
                    const open = !!filter.trim() || expanded.has(node.id);
                    return (
                        <div key={node.id} id={rowId(item.index)} role="treeitem" aria-expanded={node.children ? open : undefined}
                             aria-selected={node.id === selected} className="df-tree-node" data-active={node.id === selected || undefined}
                             data-cursor={item.index === cursor || undefined}
                             style={{
                                 transform: `translateY(${item.start}px)`,
                                 paddingLeft: ROW_PADDING + depth * INDENT,
                                 opacity: node.dimmed ? DIMMED_OPACITY : 1
                             }}
                             onClick={() => {
                                 setFocus(item.index);
                                 activate(rows[item.index]);
                             }}
                             onDoubleClick={() => onOpen?.(node)}>
              <span className="df-tree-chevron">
                {node.loading ? <Loader size={ROW_LOADER}/>
                    : node.children ?
                        <IconChevronRight {...ICON_SMALL} style={{transform: open ? 'rotate(90deg)' : undefined}}/> : null}
              </span>
                            {node.icon && <span className="df-tree-icon">{node.icon}</span>}
                            <span className="df-tree-label"
                                  style={{textDecoration: node.strike ? 'line-through' : undefined}}>{node.label}</span>
                            {node.actions && <span className="df-tree-actions"
                                                   onClick={(event) => event.stopPropagation()}>{node.actions}</span>}
                            {node.badge && <span className="df-tree-badge">{node.badge}</span>}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
