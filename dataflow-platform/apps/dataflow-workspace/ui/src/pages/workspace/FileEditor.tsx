import {Box, Menu, SegmentedControl, Text} from '@mantine/core';
import {useDebouncedValue} from '@mantine/hooks';
import {
    IconArrowBackUp,
    IconArrowForwardUp,
    IconDeviceFloppy,
    IconDots,
    IconFileCode,
    IconMarkdown,
    IconRestore,
    IconTrash,
} from '../../icons';
import {useEffect, useRef, useState} from 'react';
import {useDraftCheck} from '../../api/hooks';
import {IconAction} from '../../components/actions/IconAction';
import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {useShortcuts} from '../../components/actions/useShortcuts';
import {DocumentState} from '../../components/DocumentState';
import {Empty, ErrorState, Loading} from '../../components/ErrorState';
import {FormModal} from '../../components/FormModal';
import {SHORTCUTS} from '../../components/actions/shortcut';
import {Toolbar, ToolbarDivider, ToolbarGroup} from '../../components/layout/Toolbar';
import {DIFF, fileType, VIEWS} from '../../lib/fileKind';
import {load, save as store} from '../../lib/storage';
import {FileDiff} from '../editor/FileDiff';
import {SpecTasks} from '../editor/SpecTasks';
import {TaskPane} from '../editor/TaskPane';
import type {RunningTask} from '../editor/TaskOutput';
import {TaskTarget} from '../editor/taskTarget';
import {PipelineDesigner} from './designer/PipelineDesigner';
import {PlainFile} from './PlainFile';
import {SpecCode} from './SpecCode';
import {Validity} from './Validity';
import {ICON} from '../../lib/icons';
import {Action} from '../../components/actions/Action';
import './FileEditor.css';
import {TYPING_PAUSE} from '../../lib/timing';
import {COLOR} from '../../lib/colors';
import {useFileDocument} from './useFileDocument';

export function FileEditor({folderId, file, onClosed}: { folderId: string; file: string; onClosed: () => void }) {
    const editing = useFileDocument(folderId, file, onClosed);
    const {loaded, history, document, text, saved, dirty, drafted, busy, change, setText} = editing;
    const {dataset, language, kind} = fileType(file);
    const [deleting, setDeleting] = useState(false);
    const [task, setTask] = useState<RunningTask>();
    const [taskShown, setTaskShown] = useState(true);
    const showTask = (started: RunningTask) => {
        setTask(started);
        setTaskShown(true);
    };
    const [view, setView] = useState(() => load(`df.view.${kind}`, VIEWS[kind][0].value));
    const frame = useRef<HTMLDivElement>(null);
    useEffect(() => store(`df.view.${kind}`, view), [kind, view]);

    const [debounced] = useDebouncedValue(text, TYPING_PAUSE);
    const check = useDraftCheck(dataset, debounced, !!dataset);
    const checking = !!dataset && (check.isFetching || debounced !== text);

    const onSave = () => editing.save();
    useShortcuts(frame, {[SHORTCUTS.save]: onSave, 'Mod+Z': editing.undo, 'Mod+Shift+Z': editing.redo, 'Mod+Y': editing.redo});

    if (loaded.error) return <Box p="xl"><ErrorState error={loaded.error}/></Box>;
    if (!loaded.data || !history || !document) return <Loading/>;
    if (loaded.data.state === 'DELETED') {
        return <Empty title={`${file} is deleted in a draft`}
                      action={<Action label="Restore" loading={busy} onClick={editing.discard}/>}/>;
    }

    const FileGlyph = language === 'markdown' ? IconMarkdown : IconFileCode;
    return (
        <Box ref={frame} h="100%" style={{display: 'flex', flexDirection: 'column'}}>
            <Toolbar>
                <ToolbarGroup grow>
                    <FileGlyph {...ICON} style={{flex: 'none'}}/>
                    <Text size="sm" fw={600} truncate title={file}>{file.split('/').pop()}</Text>
                    <DocumentState status={dirty ? 'unsaved' : drafted ? 'draft' : 'saved'}/>
                </ToolbarGroup>
                <SegmentedControl size="xs" value={view} onChange={setView} data={VIEWS[kind]}/>
                <ToolbarDivider/>
                <ToolbarGroup>
                    <IconAction label="Undo" icon={IconArrowBackUp} shortcut="Mod+Z" disabled={!history.past.length}
                                onClick={editing.undo}/>
                    <IconAction label="Redo" icon={IconArrowForwardUp} shortcut="Mod+Y" disabled={!history.future.length}
                                onClick={editing.redo}/>
                </ToolbarGroup>
                {dataset && (
                    <>
                        <ToolbarDivider/>
                        <ToolbarGroup>
                            <Validity checking={checking} result={check.data}/>
                            <SpecTasks folderId={folderId} dataset={dataset} text={text} task={task} onTask={showTask}
                                       onShow={() => setTaskShown(true)}/>
                        </ToolbarGroup>
                    </>
                )}
                <ToolbarDivider/>
                <ToolbarGroup>
                    <ToolbarButton label="Discard" icon={IconRestore} disabled={busy || !(dirty || drafted)} loading={editing.discarding}
                                   hint={dirty ? 'Back to the last save' : 'Drop the draft'} onClick={editing.discard}/>
                    <ToolbarButton label="Save" icon={IconDeviceFloppy} primary shortcut={SHORTCUTS.save} disabled={busy || !dirty}
                                   loading={editing.saving} onClick={onSave}/>
                    <Menu position="bottom-end" width={180}>
                        <Menu.Target>
                            <IconAction label="More" icon={IconDots}/>
                        </Menu.Target>
                        <Menu.Dropdown>
                            <Menu.Item color={COLOR.danger} leftSection={<IconTrash {...ICON}/>} disabled={busy}
                                       onClick={() => setDeleting(true)}>Delete file</Menu.Item>
                        </Menu.Dropdown>
                    </Menu>
                </ToolbarGroup>
            </Toolbar>
            <div className="df-pane-main">
                <Box style={{flex: 1, minHeight: 0}}>
                    <TaskTarget value={showTask}>
                        {view === DIFF.value ? (
                            <FileDiff saved={saved} committed={loaded.data.committed} current={text}/>
                        ) : dataset ? (
                            view === 'design'
                                ? <PipelineDesigner dataset={dataset} folderId={folderId} document={document} onChange={change}
                                                    problems={check.data?.valid === false ? check.data.problems : []}
                                                    warnings={check.data?.warnings ?? []} checking={checking}/>
                                : <SpecCode folderId={folderId} dataset={dataset} text={text} checked={debounced} result={check.data} onChange={setText}
                                            onSave={onSave}/>
                        ) : (
                            <PlainFile kind={kind} view={view} problems={loaded.data.problems} language={language} text={text}
                                       onChange={setText} onSave={onSave}/>
                        )}
                    </TaskTarget>
                </Box>
                {task && <TaskPane task={task} shown={taskShown} onShownChange={setTaskShown} onClose={() => setTask(undefined)}/>}
            </div>
            <FormModal opened={deleting} onClose={() => setDeleting(false)} title={`Delete ${file.split('/').pop()}?`}
                       submit="Delete" danger loading={editing.removing}
                       onSubmit={() => editing.remove(() => setDeleting(false))}>
                <Text size="sm" className="df-mono">{file}</Text>
            </FormModal>
            <FormModal opened={editing.blocker.state === 'blocked'} onClose={() => editing.blocker.reset?.()} title="Leave with unsaved changes?"
                       submit="Save and leave" cancel="Keep editing" loading={editing.saving}
                       onSubmit={() => editing.save(() => editing.blocker.proceed?.())}
                       alternative={<Action danger label="Discard" onClick={() => editing.blocker.proceed?.()}/>}/>
        </Box>
    );
}
