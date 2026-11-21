import {Loader, Text} from '@mantine/core';
import {type Icon, IconArrowsDiff, IconPlayerPlay} from '../../icons';
import {useState} from 'react';
import {taskFinished, useStartTask, useTask} from '../../api/hooks';
import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {FormModal} from '../../components/FormModal';
import {ICON} from '../../lib/icons';
import {PreflightButton} from './PreflightButton';
import {type EditorTask, type RunningTask, TASK_TITLES} from './TaskOutput';
import {notifyFailure} from '../../components/notify';

const BUTTONS: { kind: EditorTask; label: string; icon: Icon; hint: string }[] = [
    {kind: 'preview', label: 'Preview', icon: IconPlayerPlay, hint: 'Run the draft on a sample'},
    {kind: 'impact', label: 'Impact', icon: IconArrowsDiff, hint: 'Compare the draft with the release on the same rows'},
];

const CONFIRMED: Record<EditorTask, boolean> = {preview: false, impact: true};

export function SpecTasks({folderId, dataset, text, task, onTask, onShow}: {
    folderId: string;
    dataset: string;
    text: string;
    task?: RunningTask;
    onTask: (task: RunningTask) => void;
    onShow: () => void;
}) {
    const startTask = useStartTask();
    const [confirming, setConfirming] = useState<EditorTask>();
    const {data: state} = useTask(task);
    const running = task && !taskFinished(state?.status) ? task.kind : undefined;

    const start = (kind: EditorTask) =>
        startTask.mutate({kind, dataset, content: text, folderId}, {
            onSuccess: ({taskId, runId}) => onTask({kind, taskId, runId, startedAt: Date.now()}),
            onError: notifyFailure('Not started'),
            onSettled: () => setConfirming(undefined),
        });

    const open = (kind: EditorTask) => {
        if (running === kind) {
            onShow();
        } else if (CONFIRMED[kind]) {
            setConfirming(kind);
        } else {
            start(kind);
        }
    };

    return (
        <>
            <PreflightButton folderId={folderId} dataset={dataset} text={text}/>
            {BUTTONS.map((button) => (
                <ToolbarButton key={button.kind} label={button.label} icon={button.icon}
                               leading={running === button.kind ? <Loader size={ICON.size}/> : undefined}
                               hint={running === button.kind ? `${TASK_TITLES[button.kind]} is running: show its output`
                                   : running ? `Wait for the ${TASK_TITLES[running].toLowerCase()} to finish` : button.hint}
                               disabled={startTask.isPending || (!!running && running !== button.kind)}
                               loading={startTask.isPending && startTask.variables?.kind === button.kind && !confirming}
                               onClick={() => open(button.kind)}/>
            ))}
            <FormModal opened={confirming === 'impact'} onClose={() => setConfirming(undefined)} title="Run data impact?"
                       submit="Run impact" loading={startTask.isPending} onSubmit={() => start('impact')}>
                <Text size="sm">
                    Starts an engine container that runs the released pipeline and this draft on the same rows,
                    then compares facts, columns, rules and tables.
                </Text>
                <Text size="sm" c="dimmed">It takes a minute or two. Nothing is written to the pipeline's tables.</Text>
            </FormModal>
        </>
    );
}
