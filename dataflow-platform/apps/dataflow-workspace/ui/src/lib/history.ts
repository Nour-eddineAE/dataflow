export type History<T> = { past: T[]; present: T; future: T[]; mergeKey?: string; mergedAt?: number };

export type Change = { merge?: string; at?: number };

const LIMIT = 200;
const MERGE_WINDOW = 800;

export function begin<T>(present: T): History<T> {
    return {past: [], present, future: []};
}

export function record<T>(history: History<T>, next: T, {merge, at = Date.now()}: Change = {}): History<T> {
    if (Object.is(next, history.present)) return history;
    const merged = merge !== undefined && merge === history.mergeKey && history.mergedAt !== undefined
        && at - history.mergedAt <= MERGE_WINDOW && history.past.length > 0;
    return {
        past: merged ? history.past : [...history.past, history.present].slice(-LIMIT),
        present: next,
        future: [],
        mergeKey: merge,
        mergedAt: merge === undefined ? undefined : at,
    };
}

export function replace<T>(history: History<T>, next: T): History<T> {
    return {...history, present: next};
}

export function undo<T>(history: History<T>): History<T> {
    const previous = history.past.at(-1);
    return previous === undefined ? history
        : {past: history.past.slice(0, -1), present: previous, future: [history.present, ...history.future]};
}

export function redo<T>(history: History<T>): History<T> {
    const [next, ...later] = history.future;
    return next === undefined ? history
        : {past: [...history.past, history.present], present: next, future: later};
}
