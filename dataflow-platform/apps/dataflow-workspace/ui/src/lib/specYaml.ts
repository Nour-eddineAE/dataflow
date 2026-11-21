
const INLINE_WIDTH = 60;
export type Written = { text: string; paths: string[] };

type Value = unknown;
type Line = { text: string; path: string };

const ORDER = ['dataset', 'version', 'owner', 'description', 'source', 'inputs', 'schedule', 'compute', 'bronze', 'silver',
    'history', 'references', 'gold', 'serve'];
const GOLD_ORDER = ['name', 'materialize', 'from', 'sql', 'transformation', 'inputs', 'params', 'steps'];
const RESERVED = /^(true|false|yes|no|on|off|null|~|-?\d[\d_]*(\.\d*)?([eE][-+]?\d+)?|\.inf|\.nan)$/i;
const PLAIN_BLOCK = /^[A-Za-z_./][^#]*$/;
const PLAIN_FLOW = /^[A-Za-z_][\w.\-/@]*$/;

export function writeSpec(tree: Record<string, Value>): Written {
    const lines: Line[] = [];
    let previous: Value;
    const gold = Array.isArray(tree.gold) ? tree.gold.map((table) => (isRecord(table) ? sorted(table, GOLD_ORDER) : table)) : tree.gold;
    for (const key of orderedKeys(tree)) {
        const value = key === 'gold' ? gold : tree[key];
        if (isEmpty(value)) continue;
        if (lines.length && (isBlock(value) || isBlock(previous))) lines.push({text: '', path: key});
        lines.push(...entry(key, value, 0, key));
        previous = value;
    }
    return {text: lines.map((line) => line.text).join('\n') + '\n', paths: lines.map((line) => line.path)};
}

function orderedKeys(tree: Record<string, Value>, order = ORDER) {
    const known = order.filter((key) => key in tree);
    return [...known, ...Object.keys(tree).filter((key) => !order.includes(key))];
}

function sorted(record: Record<string, Value>, order: string[]) {
    return Object.fromEntries(orderedKeys(record, order).map((key) => [key, record[key]]));
}

function entry(key: string, value: Value, depth: number, path: string): Line[] {
    const indent = '  '.repeat(depth);
    if (typeof value === 'string' && value.includes('\n')) {
        const body = value.replace(/\n$/, '').split('\n').map((text) => ({
            text: text ? `${indent}  ${text}` : '',
            path
        }));
        return [{text: `${indent}${key}: ${value.endsWith('\n') ? '|' : '|-'}`, path}, ...body];
    }
    if (!isBlock(value) || (fitsFlow(value) && (depth > 0 || Array.isArray(value)))) {
        return [{text: `${indent}${key}: ${inline(value, false)}`, path}];
    }
    if (Array.isArray(value)) return [{text: `${indent}${key}:`, path}, ...list(value, depth + 1, path)];
    return [{text: `${indent}${key}:`, path}, ...map(value as Record<string, Value>, depth + 1, path)];
}

function map(value: Record<string, Value>, depth: number, path: string): Line[] {
    return Object.entries(value).filter(([, item]) => !isEmpty(item))
        .flatMap(([key, item]) => entry(key, item, depth, `${path}.${key}`));
}

function list(items: Value[], depth: number, path: string): Line[] {
    const indent = '  '.repeat(depth);
    if (items.every(isFlatRecord)) return aligned(items as Record<string, Value>[], indent, path);
    return items.flatMap((item, index) => {
        const itemPath = `${path}.${index}`;
        if (!isBlock(item)) return [{text: `${indent}- ${inline(item, false)}`, path: itemPath}];
        const lines = isRecord(item) ? map(item, depth + 1, itemPath) : list(item as Value[], depth + 1, itemPath);
        if (!lines.length) return [{text: `${indent}- ${skeleton(item)}`, path: itemPath}];
        const [first, ...rest] = lines;
        return [{text: `${indent}- ${first.text.trimStart()}`, path: first.path}, ...rest];
    });
}

function aligned(items: Record<string, Value>[], indent: string, path: string): Line[] {
    const keys = [...new Set(items.flatMap((item) => Object.keys(item).filter((key) => !isEmpty(item[key]))))];
    const pieces = items.map((item) => keys.map((key) => (isEmpty(item[key]) ? undefined : `${key}: ${inline(item[key], true)}`)));
    const widths = keys.map((_, column) => Math.max(...pieces.map((row) => (row[column]?.length ?? 0) + 2)));
    return pieces.map((row, index) => {
        const last = row.reduce((found, piece, column) => (piece ? column : found), -1);
        const text = row.slice(0, last + 1)
            .map((piece, column) => (column === last ? piece! : `${piece ? `${piece},` : ''}`.padEnd(widths[column])))
            .join('');
        return {text: `${indent}- { ${text} }`, path: `${path}.${index}`};
    });
}

function inline(value: Value, flow: boolean): string {
    if (Array.isArray(value)) return `[${value.map((item) => inline(item, true)).join(', ')}]`;
    if (isRecord(value)) {
        const pairs = Object.entries(value).filter(([, item]) => !isEmpty(item)).map(([key, item]) => `${key}: ${inline(item, true)}`);
        return `{ ${pairs.join(', ')} }`;
    }
    if (typeof value === 'string') return scalar(value, flow);
    return String(value);
}

function skeleton(value: Value): string {
    if (Array.isArray(value)) return `[${value.map(skeleton).join(', ')}]`;
    if (isRecord(value)) {
        const pairs = Object.entries(value).map(([key, item]) => `${key}: ${skeleton(item)}`);
        return pairs.length ? `{ ${pairs.join(', ')} }` : '{}';
    }
    return value === undefined || value === null ? '""' : inline(value, true);
}

function scalar(text: string, flow: boolean) {
    const plain = flow ? PLAIN_FLOW.test(text) : PLAIN_BLOCK.test(text) && !text.includes(': ') && !text.endsWith(':');
    return plain && !RESERVED.test(text) && text.trim() === text ? text : JSON.stringify(text);
}

function fitsFlow(value: Value) {
    if (Array.isArray(value)) return value.every((item) => !isBlock(item));
    return isFlatRecord(value) && inline(value, false).length <= INLINE_WIDTH;
}

function isFlatRecord(value: Value): value is Record<string, Value> {
    return isRecord(value) && Object.values(value).every((item) => !isBlock(item) && !(typeof item === 'string' && item.includes('\n')));
}

function isRecord(value: Value): value is Record<string, Value> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isBlock(value: Value) {
    return Array.isArray(value) || isRecord(value);
}

function isEmpty(value: Value) {
    return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
        || (isRecord(value) && Object.values(value).every(isEmpty));
}
