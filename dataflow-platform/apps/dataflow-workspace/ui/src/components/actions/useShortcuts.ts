import {type RefObject, useEffect, useEffectEvent} from 'react';

export type Shortcuts = Partial<Record<string, () => void>>;

export function useShortcuts(frame: RefObject<HTMLElement | null>, shortcuts: Shortcuts) {
    const press = useEffectEvent((event: KeyboardEvent) => {
        const action = shortcuts[shortcutOf(event)];
        if (!action) return;
        event.preventDefault();
        event.stopPropagation();
        action();
    });
    useEffect(() => {
        const listener = (event: KeyboardEvent) => {
            if (!(event.ctrlKey || event.metaKey) || event.altKey || event.defaultPrevented) return;
            const target = event.target as Element | null;
            if (target !== document.body && !(target && frame.current?.contains(target))) return;
            press(event);
        };
        window.addEventListener('keydown', listener, true);
        return () => window.removeEventListener('keydown', listener, true);
    }, [frame]);
}

export function shortcutOf(event: Pick<KeyboardEvent, 'key' | 'shiftKey'>) {
    const key = event.key.length === 1 ? event.key.toUpperCase() : event.key;
    return `Mod+${event.shiftKey ? 'Shift+' : ''}${key}`;
}
