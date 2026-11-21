import {useEffect} from 'react';
import {load, save} from './storage';

export type RecentKind = 'table' | 'pipeline' | 'run' | 'served';

export type Recent = { to: string; label: string; kind: RecentKind; at: string };

const KEY = 'df.recents';
const KEEP = 20;

export function recents(): Recent[] {
    return load<Recent[]>(KEY, []);
}

export function remember(entry: Omit<Recent, 'at'>) {
    const others = recents().filter((recent) => recent.to !== entry.to);
    save(KEY, [{...entry, at: new Date().toISOString()}, ...others].slice(0, KEEP));
}

export function useRemember(entry: Omit<Recent, 'at'> | undefined) {
    const to = entry?.to;
    const label = entry?.label;
    const kind = entry?.kind;
    useEffect(() => {
        if (to && label && kind) remember({to, label, kind});
    }, [to, label, kind]);
}
