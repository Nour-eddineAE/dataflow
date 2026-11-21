import type {KeyboardEvent, ReactNode} from 'react';
import {tabAfterKey} from '../../lib/tabKeys';
import './Tabs.css';

export type TabItem<T extends string> = { value: T; label: ReactNode; disabled?: boolean };

export function TabStrip<T extends string>({value, onChange, items}: {
    value: T;
    onChange: (value: T) => void;
    items: TabItem<T>[];
}) {
    const reachable = items.some((item) => item.value === value) ? value : items.find((item) => !item.disabled)?.value;
    const move = (event: KeyboardEvent<HTMLDivElement>) => {
        const next = tabAfterKey(items, value, event.key);
        if (next === undefined) return;
        event.preventDefault();
        onChange(next);
        event.currentTarget.querySelector<HTMLButtonElement>(`[data-value="${next}"]`)?.focus();
    };
    return (
        <div className="df-tab-list" role="tablist" onKeyDown={move}>
            {items.map((item) => (
                <button key={item.value} type="button" role="tab" className="df-tab" aria-selected={item.value === value}
                        data-active={item.value === value || undefined} data-value={item.value} disabled={item.disabled}
                        tabIndex={item.value === reachable ? 0 : -1} onClick={() => onChange(item.value)}>
                    {item.label}
                </button>
            ))}
        </div>
    );
}
