export type JavaMember = {
    name: string;
    kind: 'METHOD' | 'FIELD';
    isStatic: boolean;
    returns: string;
    parameters: string[];
    deprecated: boolean
};

export type JavaType = {
    name: string;
    qualifiedName: string;
    kind: 'CLASS' | 'INTERFACE' | 'RECORD' | 'ANNOTATION';
    members: JavaMember[]
};

export type JavaIndex = { types: Map<string, JavaType>; members: Map<string, Map<string, JavaMember[]>> };

export type SuggestionKind =
    'method'
    | 'function'
    | 'field'
    | 'constant'
    | 'type'
    | 'annotation'
    | 'keyword'
    | 'variable'
    | 'column';

export type Suggestion = {
    label: string;
    kind: SuggestionKind;
    insertText: string;
    snippet?: boolean;
    detail?: string;
    documentation?: string;
    deprecated?: boolean;
    sortText?: string;
    importLine?: { line: number; text: string };
};

export type Signatures = {
    signatures: { label: string; parameters: string[]; documentation: string }[];
    active: number;
    parameter: number
};

type Segment = { name: string; call: boolean; created?: boolean };
type Resolved = { type: string; isStatic: boolean };

const FUNCTIONS = 'functions';
const FALLBACK = ['Dataset', 'Column', 'Row'];
const COLUMN_ARGUMENTS = new Set(['col', 'column', 'select', 'drop', 'groupBy', 'orderBy', 'sort', 'withColumnRenamed', 'dropDuplicates', 'getAs', 'fieldIndex', 'partitionBy', 'cube', 'rollup', 'asc', 'desc', 'sum', 'avg', 'min', 'max', 'count', 'countDistinct', 'first', 'last']);
const KEYWORDS = ['public', 'private', 'final', 'static', 'return', 'new', 'if', 'else', 'for', 'while', 'record', 'class', 'implements', 'extends', 'import', 'var', 'null', 'true', 'false', 'this', 'double', 'long', 'int', 'boolean', 'String'];
const LANGUAGE_ANNOTATIONS = ['Override', 'FunctionalInterface', 'SuppressWarnings'];
const IDENTIFIER = /[A-Za-z_$][\w$]*$/;
const DECLARATION = /\b([A-Z][\w$]*(?:<(?:[^<>;=(){}]|<[^<>]*>)*>)?(?:\[\])?)\s+([a-z_$][\w$]*)\s*(?=[=;,):])/g;
const VAR = /\bvar\s+([a-z_$][\w$]*)\s*=\s*([^;]+);/g;
const RECORD = /\brecord\s+([A-Z][\w$]*)\s*\(([^)]*)\)/g;

export function javaIndex(types: JavaType[]): JavaIndex {
    const members = new Map<string, Map<string, JavaMember[]>>();
    for (const type of types) {
        const byName = new Map<string, JavaMember[]>();
        for (const member of type.members) byName.set(member.name, [...(byName.get(member.name) ?? []), member]);
        members.set(type.name, byName);
    }
    return {types: new Map(types.map((type) => [type.name, type])), members};
}

export function baseType(type: string) {
    return type.replace(/<.*$/s, '').replace(/(\[\]|\.\.\.)+$/, '').trim();
}

function splitTopLevel(text: string) {
    const parts: string[] = [];
    let depth = 0;
    let start = 0;
    for (let i = 0; i < text.length; i += 1) {
        const char = text[i];
        if (char === '<' || char === '(') depth += 1;
        else if (char === '>' || char === ')') depth -= 1;
        else if (char === ',' && depth === 0) {
            parts.push(text.slice(start, i));
            start = i + 1;
        }
    }
    parts.push(text.slice(start));
    return parts.map((part) => part.trim()).filter(Boolean);
}

export function localTypes(text: string): JavaType[] {
    return [...text.matchAll(RECORD)].map(([, name, components]) => ({
        name,
        qualifiedName: name,
        kind: 'RECORD',
        members: splitTopLevel(components).flatMap((component) => {
            const match = /^(?:final\s+)?(.+?)\s+([a-z_$][\w$]*)$/.exec(component);
            return match ? [{
                name: match[2],
                kind: 'METHOD' as const,
                isStatic: false,
                returns: match[1],
                parameters: [],
                deprecated: false
            }] : [];
        }),
    }));
}

function withLocals(index: JavaIndex, text: string): JavaIndex {
    const locals = localTypes(text);
    return locals.length ? javaIndex([...index.types.values(), ...locals]) : index;
}

export function variables(text: string, index?: JavaIndex): Map<string, string> {
    const found = new Map<string, string>();
    for (const [, type, name] of text.matchAll(DECLARATION)) {
        if (!['return', 'new'].includes(type)) found.set(name, type);
    }
    if (index) {
        for (const [, name, expression] of text.matchAll(VAR)) {
            const resolved = resolve(chain(expression.trim()), index, found);
            if (resolved && !resolved.isStatic) found.set(name, resolved.type);
        }
    }
    return found;
}

function skipBack(text: string, end: number, open: string, close: string) {
    let depth = 0;
    for (let i = end; i >= 0; i -= 1) {
        const char = text[i];
        if (char === '"') {
            i -= 1;
            while (i >= 0 && !(text[i] === '"' && text[i - 1] !== '\\')) i -= 1;
        } else if (char === close) depth += 1;
        else if (char === open) {
            depth -= 1;
            if (depth === 0) return i;
        }
    }
    return -1;
}

function skipSpace(text: string, i: number) {
    while (i >= 0 && /\s/.test(text[i])) i -= 1;
    return i;
}

export function chain(text: string): Segment[] {
    const segments: Segment[] = [];
    let i = skipSpace(text, text.length - 1);
    while (i >= 0) {
        let call = false;
        if (text[i] === ')') {
            const open = skipBack(text, i, '(', ')');
            if (open < 0) return [];
            call = true;
            i = skipSpace(text, open - 1);
        }
        const match = IDENTIFIER.exec(text.slice(0, i + 1));
        if (!match) return [];
        segments.unshift({name: match[0], call});
        i = skipSpace(text, i - match[0].length);
        if (text[i] !== '.') {
            if (call && /\bnew\s*$/.test(text.slice(0, i + 1))) segments[0].created = true;
            break;
        }
        i = skipSpace(text, i - 1);
    }
    return segments;
}

function member(index: JavaIndex, type: string, name: string, call: boolean) {
    return index.members.get(type)?.get(name)?.find((candidate) => (candidate.kind === 'METHOD') === call);
}

export function resolve(segments: Segment[], index: JavaIndex, locals: Map<string, string>): Resolved | undefined {
    if (!segments.length) return undefined;
    const [root, ...rest] = segments;
    let current: Resolved | undefined;
    if (root.created) current = {type: baseType(root.name), isStatic: false};
    else if (root.call) {
        const fn = member(index, FUNCTIONS, root.name, true);
        current = fn ? {type: baseType(fn.returns), isStatic: false} : undefined;
    } else if (locals.has(root.name)) current = {type: baseType(locals.get(root.name)!), isStatic: false};
    else if (index.types.has(root.name)) current = {type: root.name, isStatic: true};
    for (const segment of rest) {
        if (!current) return undefined;
        const found = member(index, current.type, segment.name, segment.call);
        current = found ? {type: baseType(found.returns), isStatic: false} : undefined;
    }
    return current;
}

function signature(member: JavaMember) {
    return member.kind === 'FIELD' ? `${member.returns} ${member.name}` : `${member.returns} ${member.name}(${member.parameters.join(', ')})`;
}

function documentation(overloads: JavaMember[]) {
    return ['```java', ...overloads.map(signature), '```'].join('\n');
}

function memberSuggestions(overloads: Map<string, JavaMember[]>, isStatic: boolean | undefined, owner: string, sort = '0') {
    return [...overloads.entries()].flatMap(([name, all]): Suggestion[] => {
        const usable = isStatic === undefined ? all : all.filter((candidate) => candidate.isStatic === isStatic);
        if (!usable.length) return [];
        const first = usable[0];
        const method = first.kind === 'METHOD';
        const more = usable.length > 1 ? ` +${usable.length - 1} overloads` : '';
        return [{
            label: name,
            kind: method ? (first.isStatic ? 'function' : 'method') : first.isStatic ? 'constant' : 'field',
            insertText: method ? (usable.every((candidate) => !candidate.parameters.length) ? `${name}()` : `${name}($0)`) : name,
            snippet: method,
            detail: `${owner}: ${signature(first)}${more}`,
            documentation: documentation(usable),
            deprecated: usable.every((candidate) => candidate.deprecated),
            sortText: `${sort}${name}`,
        }];
    });
}

export function importLine(text: string, statement: string): { line: number; text: string } | undefined {
    const lines = text.split('\n');
    const wildcard = statement.replace(/\.[\w$]+;$/, '.*;');
    if (lines.some((line) => line.trim() === statement || line.trim() === wildcard)) return undefined;
    const isStatic = statement.startsWith('import static ');
    let last = -1;
    let lastOther = -1;
    lines.forEach((line, i) => {
        const trimmed = line.trim();
        if (!trimmed.startsWith('import ')) return;
        if (trimmed.startsWith('import static ') === isStatic) last = i;
        else lastOther = i;
    });
    if (last >= 0) return {line: last + 2, text: `${statement}\n`};
    if (lastOther >= 0) return isStatic ? {line: lastOther + 2, text: `\n${statement}\n`} : {
        line: 1,
        text: `${statement}\n`
    };
    const packageLine = lines.findIndex((line) => line.trim().startsWith('package '));
    return packageLine >= 0 ? {line: packageLine + 2, text: `\n${statement}\n`} : {line: 1, text: `${statement}\n\n`};
}

function inString(line: string) {
    let quotes = 0;
    for (let i = 0; i < line.length; i += 1) {
        if (line[i] === '"' && line[i - 1] !== '\\') quotes += 1;
        else if (line[i] === '/' && line[i + 1] === '/' && quotes % 2 === 0) return false;
    }
    return quotes % 2 === 1;
}

function inComment(text: string) {
    const line = text.slice(text.lastIndexOf('\n') + 1);
    const open = text.lastIndexOf('/*');
    if (open >= 0 && text.indexOf('*/', open) < 0) return true;
    let quotes = 0;
    for (let i = 0; i < line.length - 1; i += 1) {
        if (line[i] === '"' && line[i - 1] !== '\\') quotes += 1;
        else if (line[i] === '/' && line[i + 1] === '/' && quotes % 2 === 0) return true;
    }
    return false;
}

export function enclosingCall(text: string) {
    let depth = 0;
    let commas = 0;
    for (let i = text.length - 1; i >= 0; i -= 1) {
        const char = text[i];
        if (char === '"') {
            i -= 1;
            while (i >= 0 && !(text[i] === '"' && text[i - 1] !== '\\')) i -= 1;
        } else if (char === ')' || char === ']' || char === '}') depth += 1;
        else if (char === '(' || char === '[' || char === '{') {
            if (depth === 0) {
                if (char !== '(') return undefined;
                const segments = chain(text.slice(0, i));
                return segments.length ? {segments, parameter: commas} : undefined;
            }
            depth -= 1;
        } else if (char === ',' && depth === 0) commas += 1;
        else if (char === ';' && depth === 0) return undefined;
    }
    return undefined;
}

function overloadsOf(segments: Segment[], index: JavaIndex, locals: Map<string, string>): JavaMember[] {
    const last = segments[segments.length - 1];
    if (segments.length === 1) {
        if (last.created) return [];
        return index.members.get(FUNCTIONS)?.get(last.name)?.filter((candidate) => candidate.kind === 'METHOD') ?? [];
    }
    const receiver = resolve(segments.slice(0, -1), index, locals);
    const types = receiver ? [receiver.type] : FALLBACK;
    return types.flatMap((type) => index.members.get(type)?.get(last.name)?.filter((candidate) => candidate.kind === 'METHOD') ?? []);
}

export function javaSuggestions(text: string, offset: number, api: JavaIndex, columns: string[]): Suggestion[] {
    const before = text.slice(0, offset);
    if (inComment(before)) return [];
    const line = before.slice(before.lastIndexOf('\n') + 1);
    if (inString(line)) {
        const call = enclosingCall(before.slice(0, before.lastIndexOf('"')));
        const name = call?.segments[call.segments.length - 1].name;
        return name && COLUMN_ARGUMENTS.has(name) ? columns.map((column) => ({
            label: column,
            kind: 'column',
            insertText: column,
            detail: 'column'
        })) : [];
    }
    const index = withLocals(api, text);
    const prefix = IDENTIFIER.exec(before)?.[0] ?? '';
    const head = before.slice(0, before.length - prefix.length);

    if (/^\s*import\s+(static\s+)?[\w.]*$/.test(line)) {
        const isStatic = /^\s*import\s+static/.test(line);
        return [...api.types.values()].map((type) => ({
            label: isStatic ? `${type.qualifiedName}.*` : type.qualifiedName,
            kind: 'type',
            insertText: isStatic ? `${type.qualifiedName}.*` : type.qualifiedName,
            detail: type.kind.toLowerCase(),
        }));
    }

    if (/@\s*$/.test(head)) {
        const annotations = [...api.types.values()].filter((type) => type.kind === 'ANNOTATION');
        return [
            ...annotations.map((type) => ({
                label: type.name,
                kind: 'annotation' as const,
                insertText: type.name,
                detail: type.qualifiedName,
                importLine: importLine(text, `import ${type.qualifiedName};`)
            })),
            ...LANGUAGE_ANNOTATIONS.map((name) => ({
                label: name,
                kind: 'annotation' as const,
                insertText: name,
                detail: 'java.lang'
            })),
        ];
    }

    const locals = variables(text, index);
    const dot = /\.\s*$/.exec(head);
    if (dot) {
        const segments = chain(head.slice(0, dot.index));
        const resolved = resolve(segments, index, locals);
        if (resolved) {
            const overloads = index.members.get(resolved.type);
            return overloads ? memberSuggestions(overloads, resolved.isStatic, resolved.type) : [];
        }
        if (!segments.length || ((index.types.has(segments[0].name) || locals.has(segments[0].name)) && !segments[0].call)) return [];
        const merged = new Map<string, Suggestion>();
        FALLBACK.forEach((type, rank) => memberSuggestions(index.members.get(type) ?? new Map(), false, type, String(rank))
            .forEach((suggestion) => merged.has(suggestion.label) || merged.set(suggestion.label, suggestion)));
        return [...merged.values()];
    }

    const functions = memberSuggestions(index.members.get(FUNCTIONS) ?? new Map(), true, FUNCTIONS, '3')
        .map((suggestion) => ({
            ...suggestion,
            importLine: importLine(text, `import static org.apache.spark.sql.functions.${suggestion.label};`)
        }));
    const types = [...index.types.values()].filter((type) => type.name !== FUNCTIONS).map((type): Suggestion => ({
        label: type.name,
        kind: type.kind === 'ANNOTATION' ? 'annotation' : 'type',
        insertText: type.name,
        detail: type.qualifiedName,
        sortText: `2${type.name}`,
        importLine: api.types.has(type.name) ? importLine(text, `import ${type.qualifiedName};`) : undefined,
    }));
    const declared = [...locals.entries()].map(([name, type]): Suggestion => ({
        label: name,
        kind: 'variable',
        insertText: name,
        detail: type,
        sortText: `1${name}`
    }));
    const keywords = KEYWORDS.map((word): Suggestion => ({
        label: word,
        kind: 'keyword',
        insertText: word,
        sortText: `4${word}`
    }));
    return [...declared, ...types, ...functions, ...keywords];
}

export function javaSignatures(text: string, offset: number, api: JavaIndex): Signatures | undefined {
    const before = text.slice(0, offset);
    if (inComment(before)) return undefined;
    const call = enclosingCall(before);
    if (!call) return undefined;
    const index = withLocals(api, text);
    const overloads = overloadsOf(call.segments, index, variables(text, index));
    if (!overloads.length) return undefined;
    const fits = overloads.findIndex((candidate) => candidate.parameters.length > call.parameter || candidate.parameters.some((parameter) => parameter.endsWith('...')));
    return {
        signatures: overloads.map((candidate) => ({
            label: signature(candidate),
            parameters: candidate.parameters,
            documentation: candidate.deprecated ? 'Deprecated' : ''
        })),
        active: Math.max(fits, 0),
        parameter: call.parameter,
    };
}

export function javaHover(text: string, offset: number, api: JavaIndex): {
    start: number;
    end: number;
    contents: string
} | undefined {
    const start = offset - (IDENTIFIER.exec(text.slice(0, offset))?.[0].length ?? 0);
    const end = offset + (/^[\w$]*/.exec(text.slice(offset))?.[0].length ?? 0);
    if (start === end || inComment(text.slice(0, start))) return undefined;
    const word = text.slice(start, end);
    const index = withLocals(api, text);
    const locals = variables(text, index);
    const head = text.slice(0, start);
    const called = /^\s*\(/.test(text.slice(end));
    const dot = /\.\s*$/.exec(head);
    let overloads: JavaMember[] = [];
    if (dot) {
        const receiver = resolve(chain(head.slice(0, dot.index)), index, locals);
        const types = receiver ? [receiver.type] : FALLBACK;
        overloads = types.flatMap((type) => index.members.get(type)?.get(word) ?? []);
    } else if (called) overloads = index.members.get(FUNCTIONS)?.get(word) ?? [];
    if (overloads.length) return {start, end, contents: documentation(overloads)};
    const type = api.types.get(word);
    if (type) return {start, end, contents: `\`${type.qualifiedName}\` (${type.kind.toLowerCase()})`};
    if (locals.has(word)) return {start, end, contents: `\`${locals.get(word)} ${word}\``};
    return undefined;
}
