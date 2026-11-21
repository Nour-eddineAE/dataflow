import type {KeyboardEvent} from 'react';
import {nextBar} from '../lib/chartKeys';

export function moveBarFocus(event: KeyboardEvent<HTMLElement>, present: boolean[][]) {
    const current = (event.target as HTMLElement).closest<HTMLElement>('[data-col]');
    if (!current) return;
    const next = nextBar(present, {row: Number(current.dataset.row ?? 0), col: Number(current.dataset.col)}, event.key);
    if (!next) return;
    event.preventDefault();
    event.currentTarget.querySelector<HTMLElement>(`[data-row="${next.row}"][data-col="${next.col}"]`)?.focus();
}
