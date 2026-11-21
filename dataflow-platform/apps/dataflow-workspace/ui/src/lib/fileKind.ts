import type {EditorLanguage} from '../components/CodeEditor';
import {NAME} from './names';

const SPEC = new RegExp(`^pipelines/(${NAME})/pipeline\\.yml$`);

const LANGUAGES: [RegExp, EditorLanguage][] = [
    [/\.ya?ml$/, 'yaml'],
    [/\.java$/, 'java'],
    [/\.sql$/, 'sql'],
    [/\.(md|markdown)$/i, 'markdown'],
    [/\.json$/, 'json'],
    [/\.py$/, 'python'],
];

export const DIFF = {value: 'diff', label: 'Diff'};

export const VIEWS = {
    spec: [{value: 'design', label: 'Design'}, {value: 'code', label: 'Code'}, DIFF],
    markdown: [{value: 'edit', label: 'Edit'}, {value: 'split', label: 'Split'}, {value: 'preview', label: 'Preview'}, DIFF],
    file: [{value: 'edit', label: 'Edit'}, DIFF],
};

export type FileKind = keyof typeof VIEWS;

export type FileType = { dataset?: string; language: EditorLanguage; kind: FileKind };

export function fileType(file: string): FileType {
    const dataset = SPEC.exec(file)?.[1];
    const language = LANGUAGES.find(([pattern]) => pattern.test(file))?.[1] ?? 'plaintext';
    const kind: FileKind = dataset ? 'spec' : language === 'markdown' ? 'markdown' : 'file';
    return {dataset, language, kind};
}
