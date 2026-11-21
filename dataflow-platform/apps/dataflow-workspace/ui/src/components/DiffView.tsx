import {DiffEditor} from '@monaco-editor/react';
import {useComputedColorScheme} from '@mantine/core';
import '../lib/monaco';

export function DiffView({original, current, height = '100%', sideBySide = false}: {
    original: string;
    current: string;
    height?: string;
    sideBySide?: boolean;
}) {
    const scheme = useComputedColorScheme('light');
    return (
        <DiffEditor
            original={original}
            modified={current}
            language="yaml"
            theme={scheme === 'dark' ? 'dataflow-dark' : 'dataflow-light'}
            height={height}
            loading={null}
            options={{
                readOnly: true,
                renderSideBySide: sideBySide,
                minimap: {enabled: false},
                fontSize: 13,
                lineHeight: 20,
                fontFamily: '"JetBrains Mono Variable", "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace',
                scrollBeyondLastLine: false,
                automaticLayout: true,
                renderOverviewRuler: false,
                hideUnchangedRegions: {enabled: true, contextLineCount: 3, minimumLineCount: 4, revealLineCount: 10},
                lineNumbersMinChars: 3,
                scrollbar: {verticalScrollbarSize: 10, horizontalScrollbarSize: 10, useShadows: false},
                renderIndicators: true,
                diffWordWrap: 'off',
            }}
        />
    );
}
