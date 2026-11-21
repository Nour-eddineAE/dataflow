import {Tooltip, UnstyledButton, VisuallyHidden} from '@mantine/core';
import type {Icon} from '../../icons';
import {type KeyboardEvent, type PointerEvent, type ReactNode, useEffect, useRef, useState} from 'react';
import {load, save} from '../../lib/storage';
import {IconAction} from '../actions/IconAction';
import {PanelGlyph} from '../actions/PanelGlyph';
import {TabStrip} from './Tabs';
import {ICON_LARGE} from '../../lib/icons';
import './Pane.css';

type Side = 'left' | 'right' | 'bottom';
type Stored = { size: number; collapsed: boolean };

export type PaneTool = { id: string; title: string; icon: Icon; badge?: ReactNode };

export type PaneProps = {
    id: string;
    side: Side;
    title: string;
    icon: Icon;
    header?: ReactNode;
    actions?: ReactNode;
    collapsible?: boolean;
    collapsed?: boolean;
    defaultCollapsed?: boolean;
    onCollapsedChange?: (collapsed: boolean) => void;
    badge?: ReactNode;
    tools?: readonly PaneTool[];
    tool?: string;
    onToolChange?: (tool: string) => void;
    defaultSize?: number;
    min?: number;
    max?: number;
    children: ReactNode;
};

export const BOTTOM_PANE_MAX = 2000;

const STEP = 16;
const KEYS: Record<Side, Record<string, number>> = {
    left: {ArrowLeft: -STEP, ArrowRight: STEP},
    right: {ArrowLeft: STEP, ArrowRight: -STEP},
    bottom: {ArrowUp: STEP, ArrowDown: -STEP},
};

export function Pane({id, side, title, icon: PaneIcon, header, actions, collapsible = true, collapsed: controlled, defaultCollapsed = false,
                         onCollapsedChange, badge, tools, tool, onToolChange, defaultSize = 280, min = 180, max = 560,
                         children}: PaneProps) {
    const [state, setState] = useState<Stored>(() => {
        const stored = load<Partial<Stored>>(`df.pane.${id}`, {});
        return {size: clamp(Number(stored.size) || defaultSize, min, max), collapsed: stored.collapsed ?? defaultCollapsed};
    });
    const [dragging, setDragging] = useState(false);
    const origin = useRef({point: 0, size: state.size});
    const bottom = side === 'bottom';
    const collapsed = collapsible && (controlled ?? state.collapsed);
    const direction = side === 'left' ? 1 : -1;

    useEffect(() => save(`df.pane.${id}`, state), [id, state]);

    const resize = (size: number) => setState((current) => ({...current, size: clamp(size, min, max)}));
    const setCollapsed = (next: boolean) => {
        if (onCollapsedChange) onCollapsedChange(next);
        else setState((current) => ({...current, collapsed: next}));
    };
    const toggle = () => setCollapsed(!collapsed);
    const open = (next: string) => {
        onToolChange?.(next);
        setCollapsed(false);
    };
    const point = (event: PointerEvent) => (bottom ? event.clientY : event.clientX);

    const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
        if (event.button !== 0) return;
        event.preventDefault();
        origin.current = {point: point(event), size: state.size};
        event.currentTarget.setPointerCapture(event.pointerId);
        setDragging(true);
    };
    const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
        if (dragging) resize(origin.current.size + (point(event) - origin.current.point) * direction);
    };
    const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        const step = KEYS[side][event.key];
        if (step === undefined) return;
        event.preventDefault();
        resize(state.size + step);
    };

    const narrow = collapsed && !bottom;
    return (
        <aside className="df-pane" data-side={side} data-collapsed={collapsed || undefined}
               data-dragging={dragging || undefined}
               style={collapsed ? undefined : bottom ? {height: state.size} : {width: state.size}}>
            <header className="df-pane-head">
                {!narrow && (header ?? (tools ? (
                    <TabStrip value={tool ?? ''} onChange={open} items={tools.map((entry) => ({
                        value: entry.id,
                        label: <>{entry.title}{entry.badge}</>,
                    }))}/>
                ) : <span className="df-pane-title">{title}{badge}</span>))}
                <span className="df-pane-actions">
                    {!narrow && actions}
                    {collapsible && <IconAction label={collapsed ? 'Expand' : 'Collapse'} onClick={toggle} size={32}
                                                glyph={<PanelGlyph side={side} collapsed={collapsed} size={ICON_LARGE.size}/>}/>}
                </span>
            </header>
            {narrow ? (
                <nav className="df-pane-rail" aria-label={title}>
                    {(tools ?? [{id, title, icon: PaneIcon, badge}]).map((entry) => (
                        <Tooltip key={entry.id} label={entry.title} position={side === 'left' ? 'right' : 'left'}>
                            <UnstyledButton className="df-pane-rail-tool" aria-label={`Expand ${entry.title}`}
                                            onClick={() => (tools ? open(entry.id) : toggle())}>
                                <entry.icon {...ICON_LARGE}/>
                                <span className="df-pane-rail-title">{entry.title}</span>
                                {entry.badge}
                            </UnstyledButton>
                        </Tooltip>
                    ))}
                </nav>
            ) : (
                <div className="df-pane-body" inert={collapsed}>{children}</div>
            )}
            {!collapsed && (
                <div className="df-pane-divider" role="separator" aria-orientation={bottom ? 'horizontal' : 'vertical'}
                     aria-label={`Resize ${title}`} aria-valuenow={state.size} aria-valuemin={min} aria-valuemax={max}
                     tabIndex={0} onPointerDown={onPointerDown} onPointerMove={onPointerMove}
                     onPointerUp={() => setDragging(false)} onPointerCancel={() => setDragging(false)}
                     onDoubleClick={() => resize(defaultSize)} onKeyDown={onKeyDown}/>
            )}
        </aside>
    );
}

function clamp(value: number, min: number, max: number) {
    return Math.min(Math.max(value, min), max);
}

export function PaneLayout({children}: { children: ReactNode }) {
    return <div className="df-pane-layout">{children}</div>;
}

export function PaneMain({title, children}: { title?: string; children: ReactNode }) {
    return (
        <div className="df-pane-main">
            {title && <VisuallyHidden component="h1">{title}</VisuallyHidden>}
            {children}
        </div>
    );
}
