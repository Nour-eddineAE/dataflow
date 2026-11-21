import {useRef} from 'react';
import {useSchema} from '../../api/hooks';
import type {Problem} from '../../api/types';
import {IconAction} from '../../components/actions/IconAction';
import {CodeEditor, type EditorHandle} from '../../components/CodeEditor';
import {Pane, PaneLayout, PaneMain} from '../../components/layout/Pane';
import {SchemaBrowser} from '../../components/SchemaBrowser';
import {IconDatabase, IconRefresh} from '../../icons';
import {insertAtCursor} from '../../lib/editorInsert';

export function SqlFile({text, problems, onChange, onSave}: {
    text: string;
    problems?: Problem[];
    onChange: (text: string) => void;
    onSave: () => void;
}) {
    const schema = useSchema();
    const editor = useRef<EditorHandle | null>(null);
    return (
        <PaneLayout>
            <Pane id="file.schema" side="left" title="Schema" icon={IconDatabase} defaultSize={280} min={240} max={480}
                  actions={<IconAction label="Refresh" icon={IconRefresh} loading={schema.isFetching} onClick={() => void schema.refetch()}/>}>
                <SchemaBrowser schema={schema.data} error={schema.error} onInsert={(table) => insertAtCursor(editor.current, table)}/>
            </Pane>
            <PaneMain>
                <CodeEditor value={text} onChange={onChange} language="sql" schema={schema.data} problems={problems} onSave={onSave}
                            onEditor={(instance) => {
                                editor.current = instance;
                            }}/>
            </PaneMain>
        </PaneLayout>
    );
}
