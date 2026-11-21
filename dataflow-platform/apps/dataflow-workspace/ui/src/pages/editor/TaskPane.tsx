import {IconPlayerPlay, IconX} from '../../icons';
import {IconAction} from '../../components/actions/IconAction';
import {BOTTOM_PANE_MAX, Pane} from '../../components/layout/Pane';
import {type RunningTask, TASK_TITLES, TaskResult, TaskSummary} from './TaskOutput';

const DEFAULT_HEIGHT = 360;
const MIN_HEIGHT = 160;

export function TaskPane({task, shown, onShownChange, onClose}: {
    task: RunningTask;
    shown: boolean;
    onShownChange: (shown: boolean) => void;
    onClose: () => void;
}) {
    return (
        <Pane id="spec.task" side="bottom" title={TASK_TITLES[task.kind]} icon={IconPlayerPlay} defaultSize={DEFAULT_HEIGHT}
              min={MIN_HEIGHT} max={BOTTOM_PANE_MAX} collapsed={!shown} onCollapsedChange={(collapsed) => onShownChange(!collapsed)}
              header={<TaskSummary task={task}/>} actions={<IconAction label="Close" icon={IconX} onClick={onClose}/>}>
            <TaskResult key={task.taskId} task={task}/>
        </Pane>
    );
}
