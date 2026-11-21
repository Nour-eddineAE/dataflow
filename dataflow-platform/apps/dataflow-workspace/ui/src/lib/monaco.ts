import {loader} from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import {api} from '../api/client';
import type {FunctionDoc, Schema, SqlFunction} from '../api/types';
import {hoverText} from './functionDocs';
import {
    javaHover,
    javaIndex,
    type JavaIndex,
    javaSignatures,
    javaSuggestions,
    type JavaType,
    type SuggestionKind
} from './javaCompletion';
import {specCompletions, specDoc} from './specCompletion';
import {suggestions} from './sqlErrors';
import {functionsStartingWith} from './functionMatch';

self.MonacoEnvironment = {getWorker: () => new EditorWorker()};

const SQL_KEYWORDS = [
    'SELECT', 'FROM', 'WHERE', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'JOIN', 'LEFT JOIN', 'INNER JOIN', 'ON', 'AS',
    'AND', 'OR', 'NOT', 'IN', 'IS NULL', 'IS NOT NULL', 'BETWEEN', 'LIKE', 'ILIKE', 'DISTINCT', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
    'WITH', 'UNION ALL', 'ASC', 'DESC', 'SETTINGS', 'FORMAT',
];

let schema: Schema = {};
let functions: SqlFunction[] = [];
let functionNames = new Set<string>();
const docs = new Map<string, Promise<FunctionDoc | null>>();
const COLUMNS_AGE = 60_000;
let javaApi: Promise<JavaIndex> | undefined;
let javaColumns: { at: number; names: Promise<string[]> } | undefined;

export function setSqlSchema(next: Schema | undefined) {
    schema = next ?? {};
}

export function setSqlFunctions(next: SqlFunction[] | undefined) {
    functions = next ?? [];
    functionNames = new Set(functions.map((fn) => fn.name));
}

function functionDoc(name: string) {
    if (!docs.has(name)) docs.set(name, api.get<FunctionDoc>(`/sql/functions/${encodeURIComponent(name)}`).catch(() => null));
    return docs.get(name)!;
}

function sqlCompletions(model: monaco.editor.ITextModel, position: monaco.Position): monaco.languages.CompletionList {
    const word = model.getWordUntilPosition(position);
    const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
    const before = model.getValueInRange(new monaco.Range(position.lineNumber, 1, position.lineNumber, word.startColumn));
    const qualifier = /([A-Za-z_]\w*)\.$/.exec(before)?.[1];
    const tables = Object.keys(schema);
    const {Kind} = {Kind: monaco.languages.CompletionItemKind};

    if (qualifier) {
        const inDatabase = tables.filter((name) => name.startsWith(`${qualifier}.`));
        if (inDatabase.length) {
            return {
                suggestions: inDatabase.map((name) => ({
                    label: name.slice(qualifier.length + 1),
                    kind: Kind.Struct,
                    insertText: name.slice(qualifier.length + 1),
                    range,
                    detail: 'table'
                }))
            };
        }
    }

    const text = model.getValue();
    const referenced = tables.filter((name) => text.includes(name));
    const columns = [...new Set(referenced.flatMap((name) => schema[name].map((column) => column.name)))];
    const databases = [...new Set(tables.map((name) => name.split('.')[0]))];
    return {
        suggestions: [
            ...columns.map((name) => ({
                label: name,
                kind: Kind.Field,
                insertText: name,
                range,
                detail: 'column',
                sortText: `0${name}`
            })),
            ...tables.map((name) => ({
                label: name,
                kind: Kind.Struct,
                insertText: name,
                range,
                detail: 'table',
                sortText: `1${name}`
            })),
            ...databases.map((name) => ({
                label: name,
                kind: Kind.Module,
                insertText: name,
                range,
                detail: 'database',
                sortText: `2${name}`
            })),
            ...SQL_KEYWORDS.map((name) => ({
                label: name,
                kind: Kind.Keyword,
                insertText: name,
                range,
                sortText: `3${name}`
            })),
            ...functionsStartingWith(functions, word.word).map((fn) => ({
                label: fn.name,
                kind: Kind.Function,
                insertText: `${fn.name}($0)`,
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                range,
                detail: fn.aggregate ? 'aggregate function' : 'function',
                sortText: `4${fn.name}`,
            })),
        ],
        incomplete: true,
    };
}

monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.'],
    provideCompletionItems: sqlCompletions
});

monaco.languages.registerCompletionItemProvider('yaml', {
    triggerCharacters: [' ', '-'],
    provideCompletionItems: specCompletions(monaco)
});

export function javaApiIndex() {
    javaApi ??= api.get<JavaType[]>('/editor/java').then(javaIndex).catch(() => {
        javaApi = undefined;
        return javaIndex([]);
    });
    return javaApi;
}

function columnNames() {
    if (!javaColumns || Date.now() - javaColumns.at > COLUMNS_AGE) {
        javaColumns = {
            at: Date.now(),
            names: api.get<Schema>('/catalog/schema')
                .then((tables) => [...new Set(Object.values(tables).flat().map((column) => column.name))].sort())
                .catch(() => []),
        };
    }
    return javaColumns.names;
}

const JAVA_KINDS: Record<SuggestionKind, monaco.languages.CompletionItemKind> = {
    method: monaco.languages.CompletionItemKind.Method,
    function: monaco.languages.CompletionItemKind.Function,
    field: monaco.languages.CompletionItemKind.Field,
    constant: monaco.languages.CompletionItemKind.Constant,
    type: monaco.languages.CompletionItemKind.Class,
    annotation: monaco.languages.CompletionItemKind.Interface,
    keyword: monaco.languages.CompletionItemKind.Keyword,
    variable: monaco.languages.CompletionItemKind.Variable,
    column: monaco.languages.CompletionItemKind.Field,
};

monaco.languages.registerCompletionItemProvider('java', {
    triggerCharacters: ['.', '@', '"'],
    provideCompletionItems: async (model, position) => {
        const [index, columns] = await Promise.all([javaApiIndex(), columnNames()]);
        const word = model.getWordUntilPosition(position);
        const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
        return {
            suggestions: javaSuggestions(model.getValue(), model.getOffsetAt(position), index, columns).map((suggestion) => ({
                label: suggestion.label,
                kind: JAVA_KINDS[suggestion.kind],
                insertText: suggestion.insertText,
                insertTextRules: suggestion.snippet ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
                detail: suggestion.detail,
                documentation: suggestion.documentation ? {value: suggestion.documentation} : undefined,
                tags: suggestion.deprecated ? [monaco.languages.CompletionItemTag.Deprecated] : undefined,
                sortText: suggestion.sortText,
                range,
                additionalTextEdits: suggestion.importLine
                    ? [{
                        range: new monaco.Range(suggestion.importLine.line, 1, suggestion.importLine.line, 1),
                        text: suggestion.importLine.text
                    }]
                    : undefined,
                command: suggestion.insertText.endsWith('($0)') ? {
                    id: 'editor.action.triggerParameterHints',
                    title: ''
                } : undefined,
            })),
        };
    },
});

function parameterRanges(label: string, parameters: string[]): [number, number][] {
    let start = label.indexOf('(') + 1;
    return parameters.map((parameter) => {
        const range: [number, number] = [start, start + parameter.length];
        start = range[1] + 2;
        return range;
    });
}

monaco.languages.registerSignatureHelpProvider('java', {
    signatureHelpTriggerCharacters: ['(', ','],
    signatureHelpRetriggerCharacters: [')'],
    provideSignatureHelp: async (model, position) => {
        const help = javaSignatures(model.getValue(), model.getOffsetAt(position), await javaApiIndex());
        if (!help) return null;
        return {
            value: {
                signatures: help.signatures.map((candidate) => ({
                    label: candidate.label,
                    documentation: candidate.documentation,
                    parameters: parameterRanges(candidate.label, candidate.parameters).map((label) => ({label})),
                })),
                activeSignature: help.active,
                activeParameter: help.parameter,
            },
            dispose: () => undefined,
        };
    },
});

monaco.languages.registerHoverProvider('java', {
    provideHover: async (model, position) => {
        const hover = javaHover(model.getValue(), model.getOffsetAt(position), await javaApiIndex());
        if (!hover) return null;
        const start = model.getPositionAt(hover.start);
        const end = model.getPositionAt(hover.end);
        return {
            range: new monaco.Range(start.lineNumber, start.column, end.lineNumber, end.column),
            contents: [{value: hover.contents}]
        };
    },
});

monaco.languages.registerCodeActionProvider('sql', {
    provideCodeActions: (model, _range, context) => ({
        actions: context.markers.flatMap((marker) => suggestions(marker.message).map((name, index) => ({
            title: `Change to ${name}`,
            kind: 'quickfix',
            diagnostics: [marker],
            isPreferred: index === 0,
            edit: {
                edits: [{
                    resource: model.uri,
                    textEdit: {range: marker, text: name},
                    versionId: model.getVersionId()
                }]
            },
        }))),
        dispose: () => undefined,
    }),
});

monaco.languages.registerHoverProvider('sql', {
    provideHover: async (model, position) => {
        const word = model.getWordAtPosition(position);
        if (!word || !functionNames.has(word.word)) return null;
        const doc = await functionDoc(word.word);
        if (!doc) return null;
        return {
            range: new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn),
            contents: hoverText(doc).map((value) => ({value})),
        };
    },
});

monaco.languages.registerHoverProvider('yaml', {
    provideHover: (model, position) => {
        const doc = specDoc(model.getLineContent(position.lineNumber), position.column);
        if (!doc) return null;
        return {
            range: new monaco.Range(position.lineNumber, doc.start, position.lineNumber, doc.end),
            contents: [{value: doc.text}],
        };
    },
});

monaco.languages.registerDocumentFormattingEditProvider('sql', {
    provideDocumentFormattingEdits: async (model) => {
        const text = model.getValue();
        if (!text.trim()) return [];
        const formatted = await api.post<{ sql: string }>('/sql/format', {sql: text});
        return [{range: model.getFullModelRange(), text: formatted.sql}];
    },
});

const shared: monaco.editor.IColors = {
    'editorIndentGuide.background1': '#00000000',
};

monaco.editor.defineTheme('dataflow-light', {
    base: 'vs',
    inherit: true,
    rules: [],
    colors: {
        ...shared,
        'editor.background': '#ffffff',
        'editor.lineHighlightBackground': '#f4f6f8',
        'editor.lineHighlightBorder': '#00000000',
        'editorGutter.background': '#ffffff',
        'editorLineNumber.foreground': '#6f7782',
        'editorLineNumber.activeForeground': '#1f2328',
        'editor.selectionBackground': '#dde1fb',
        'editorSuggestWidget.selectedBackground': '#dde1fb',
    },
});

monaco.editor.defineTheme('dataflow-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [],
    colors: {
        ...shared,
        'editor.background': '#16181b',
        'editor.lineHighlightBackground': '#1d2024',
        'editor.lineHighlightBorder': '#00000000',
        'editorGutter.background': '#16181b',
        'editorLineNumber.foreground': '#8b929b',
        'editorLineNumber.activeForeground': '#e4e6ea',
        'editor.selectionBackground': '#4855c966',
        'editorSuggestWidget.selectedBackground': '#4855c966',
        'editorWidget.background': '#1c1e22',
        'editorSuggestWidget.background': '#1c1e22',
        'editorSuggestWidget.border': '#2d3136',
    },
});

loader.config({monaco});

export {monaco};
