import {Box} from '@mantine/core';
import {useMemo, useRef, useState} from 'react';
import {useFolderLog, useSpecTree} from '../../api/hooks';
import type {DraftCheck} from '../../api/types';
import {CodeEditor, type EditorHandle} from '../../components/CodeEditor';
import {Pane, PaneLayout, PaneMain} from '../../components/layout/Pane';
import {IconAlertTriangle, IconArrowsDiff, IconHistory, IconListDetails, IconTable} from '../../icons';
import {insertAtCursor} from '../../lib/editorInsert';
import {goToLine} from '../../lib/goToLine';
import {warningLines} from '../../lib/warningLines';
import {CheckTab} from '../editor/CheckTab';
import {HistoryTab} from '../editor/HistoryTab';
import {ImpactTab} from '../editor/ImpactTab';
import {SpecTables} from '../editor/SpecTables';

const DETAILS = [
    {id: 'check', title: 'Problems', icon: IconAlertTriangle},
    {id: 'plan', title: 'Impact', icon: IconArrowsDiff},
    {id: 'history', title: 'History', icon: IconHistory},
] as const;
type Detail = typeof DETAILS[number]['id'];

type Props = {
    folderId: string;
    dataset: string;
    text: string;
    checked: string;
    result?: DraftCheck;
    onChange: (value: string) => void;
    onSave: () => void;
};

export function SpecCode({folderId, dataset, text, checked, result, onChange, onSave}: Props) {
    const [detail, setDetail] = useState<Detail>('check');
    const log = useFolderLog(folderId);
    const problems = useMemo(() => result?.problems, [result]);
    const tree = useSpecTree(checked, !!result?.warnings.length);
    const warnings = useMemo(() => (tree.data && result ? warningLines(checked, tree.data, result.warnings)
        : (result?.warnings ?? []).map((message) => ({message}))), [tree.data, result, checked]);
    const editor = useRef<EditorHandle | null>(null);
    return (
        <PaneLayout>
            <Pane id="spec.tables" side="left" title="Tables" icon={IconTable} defaultCollapsed defaultSize={280} min={240} max={480}>
                <SpecTables dataset={dataset} text={text} onInsert={(table) => insertAtCursor(editor.current, table)}/>
            </Pane>
            <PaneMain>
                <CodeEditor value={text} onChange={onChange} language="yaml" problems={problems} warnings={warnings} onSave={onSave}
                            onEditor={(instance) => {
                                editor.current = instance;
                            }}/>
            </PaneMain>
            <Pane id="spec.details" side="right" title="Details" icon={IconListDetails} defaultSize={420} min={300} max={720}
                  tools={DETAILS} tool={detail}
                  onToolChange={(next) => setDetail(DETAILS.find((entry) => entry.id === next)?.id ?? 'check')}>
                <Box h="100%" style={{overflow: 'auto'}} p={detail === 'check' ? 'xs' : 'md'}>
                    {detail === 'check' && <CheckTab result={result} warnings={warnings} onLine={(line) => goToLine(editor.current, line)}/>}
                    {detail === 'plan' && <ImpactTab dataset={dataset} result={result}/>}
                    {detail === 'history' && <HistoryTab log={log.data}/>}
                </Box>
            </Pane>
        </PaneLayout>
    );
}
