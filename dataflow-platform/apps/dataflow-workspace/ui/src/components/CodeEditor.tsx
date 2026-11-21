import Editor, {type OnMount} from '@monaco-editor/react';
import {useComputedColorScheme} from '@mantine/core';
import {useEffect, useEffectEvent, useState} from 'react';
import type {Problem, Schema} from '../api/types';
import {javaApiIndex, monaco, setSqlSchema} from '../lib/monaco';

const WIDE_INDENT = 4;

export type EditorHandle = monaco.editor.IStandaloneCodeEditor;

export type EditorLanguage = 'sql' | 'yaml' | 'java' | 'markdown' | 'json' | 'python' | 'plaintext';

type Props = {
    value: string;
    path?: string;
    onChange?: (value: string) => void;
    language: EditorLanguage;
    schema?: Schema;
    problems?: Problem[];
    warnings?: Problem[];
    onRun?: () => void;
    readOnly?: boolean;
    height?: string;
    onEditor?: (editor: EditorHandle) => void;
    onSave?: () => void;
};

const OWNER = 'dataflow';

export function CodeEditor({
                               value,
                               path,
                               onChange,
                               language,
                               schema,
                               problems,
                               warnings,
                               onRun,
                               readOnly,
                               height = '100%',
                               onEditor,
                               onSave
                           }: Props) {
    const scheme = useComputedColorScheme('light');
    const [editor, setEditor] = useState<EditorHandle | null>(null);
    const run = useEffectEvent(() => onRun?.());
    const save = useEffectEvent(() => onSave?.());

    useEffect(() => {
        if (language === 'sql') setSqlSchema(schema);
    }, [language, schema]);

    useEffect(() => {
        if (language === 'java' && !readOnly) void javaApiIndex();
    }, [language, readOnly]);

    useEffect(() => {
        if (!editor) return;
        const actions = [
            editor.addAction({
                id: 'dataflow.run',
                label: 'Run',
                keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
                run: () => run(),
            }),
            editor.addAction({
                id: 'dataflow.save',
                label: 'Save',
                keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS],
                run: () => save(),
            }),
        ];
        return () => actions.forEach((action) => action.dispose());
    }, [editor]);

    useEffect(() => {
        const model = editor?.getModel();
        if (!model) return;
        const marks = (list: Problem[] | undefined, severity: monaco.MarkerSeverity) => (list ?? [])
            .filter((problem) => problem.line && problem.line <= model.getLineCount())
            .map((problem) => ({
                severity,
                message: problem.message,
                startLineNumber: problem.line!,
                endLineNumber: problem.line!,
                startColumn: model.getLineFirstNonWhitespaceColumn(problem.line!) || 1,
                endColumn: model.getLineMaxColumn(problem.line!),
            }));
        monaco.editor.setModelMarkers(model, OWNER, [...marks(problems, monaco.MarkerSeverity.Error), ...marks(warnings, monaco.MarkerSeverity.Warning)]);
    }, [editor, problems, warnings, value]);

    const mount: OnMount = (instance) => {
        setEditor(instance);
        onEditor?.(instance);
    };

    return (
        <Editor
            value={value}
            path={path}
            keepCurrentModel={path !== undefined}
            language={language}
            theme={scheme === 'dark' ? 'dataflow-dark' : 'dataflow-light'}
            height={height}
            onChange={(next) => onChange?.(next ?? '')}
            onMount={mount}
            loading={null}
            options={{
                readOnly,
                domReadOnly: readOnly,
                minimap: {enabled: false},
                fontSize: 13,
                lineHeight: 20,
                fontFamily: '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
                fontLigatures: false,
                scrollBeyondLastLine: false,
                automaticLayout: true,
                renderLineHighlight: readOnly ? 'none' : 'line',
                padding: {top: 10, bottom: 10},
                tabSize: language === 'java' || language === 'python' ? WIDE_INDENT : 2,
                wordWrap: language === 'sql' ? 'on' : 'off',
                folding: language !== 'sql',
                lineNumbersMinChars: 3,
                glyphMargin: false,
                fixedOverflowWidgets: true,
                overviewRulerLanes: 0,
                hideCursorInOverviewRuler: true,
                scrollbar: {verticalScrollbarSize: 10, horizontalScrollbarSize: 10, useShadows: false},
                quickSuggestions: readOnly ? false : {
                    other: true,
                    strings: language === 'yaml' || language === 'java',
                    comments: false
                },
                suggestOnTriggerCharacters: !readOnly,
                snippetSuggestions: 'top',
                tabCompletion: 'on',
                contextmenu: !readOnly,
                stickyScroll: {enabled: false},
            }}
        />
    );
}
