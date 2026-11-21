import {IconEye} from '../../icons';
import {useDeferredValue} from 'react';
import {CodeEditor} from '../../components/CodeEditor';
import {Pane, PaneLayout, PaneMain} from '../../components/layout/Pane';
import {MarkdownPreview} from './MarkdownPreview';
import './MarkdownEditor.css';

export type MarkdownMode = 'edit' | 'split' | 'preview';

export function MarkdownEditor({text, mode, onChange, onSave}: {
    text: string;
    mode: MarkdownMode;
    onChange: (value: string) => void;
    onSave: () => void;
}) {
    const preview = useDeferredValue(text);
    if (mode === 'preview') return <MarkdownPreview text={preview}/>;
    const editor = <CodeEditor language="markdown" value={text} onChange={onChange} onSave={onSave}/>;
    if (mode === 'edit') return editor;
    return (
        <PaneLayout>
            <PaneMain>{editor}</PaneMain>
            <Pane id="markdown.preview" side="right" title="Preview" icon={IconEye} collapsible={false} defaultSize={520} min={280} max={960}>
                <MarkdownPreview text={preview}/>
            </Pane>
        </PaneLayout>
    );
}
