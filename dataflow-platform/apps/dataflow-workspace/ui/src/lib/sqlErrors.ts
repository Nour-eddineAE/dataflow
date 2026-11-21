export type Range = { start: number; end: number };

const POSITION = /\(line (\d+), col (\d+)\)/;
const IDENTIFIER = /identifier [`']([^`']+)[`']/;
const WORD = /[\w.`"$]/;
const SUGGESTIONS = /Maybe you meant: \[([^\]]*)]/;

export function errorRange(message: string, sql: string): Range | undefined {
    const lead = sql.length - sql.trimStart().length;
    const position = POSITION.exec(message);
    if (position) {
        const start = offsetOf(sql.slice(lead), Number(position[1]), Number(position[2]));
        if (start !== undefined) return {start: lead + start, end: lead + wordEnd(sql.slice(lead), start)};
    }
    const name = IDENTIFIER.exec(message)?.[1];
    const start = name ? sql.indexOf(name) : -1;
    return start >= 0 && name ? {start, end: start + name.length} : undefined;
}

function offsetOf(sql: string, line: number, column: number) {
    const lines = sql.split('\n');
    if (line < 1 || line > lines.length || column < 1) return undefined;
    const offset = lines.slice(0, line - 1).reduce((total, text) => total + text.length + 1, 0) + column - 1;
    return offset <= sql.length ? offset : undefined;
}

function wordEnd(sql: string, start: number) {
    let end = start;
    while (end < sql.length && WORD.test(sql[end])) end++;
    return end > start ? end : Math.min(start + 1, sql.length);
}

export function suggestions(message: string): string[] {
    const listed = SUGGESTIONS.exec(message)?.[1];
    if (!listed) return [];
    return listed.split(',').map((name) => name.trim().replace(/^['`"]|['`"]$/g, '')).filter(Boolean);
}
