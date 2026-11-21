export const SHORTCUTS = {run: 'Mod+Enter', save: 'Mod+S'} as const;

const MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);

export function shortcutLabel(keys: string) {
    return MAC ? keys.replace('Mod+', '⌘').replace('Shift+', '⇧').replace('Alt+', '⌥') : keys.replace('Mod+', 'Ctrl+');
}

export function tooltip(label: string, shortcut?: string) {
    return shortcut ? `${label}  ${shortcutLabel(shortcut)}` : label;
}
