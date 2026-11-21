import {useStartTask} from '../../../api/hooks';
import {type Spec, throughStep} from '../../../lib/specModel';
import {writeSpec} from '../../../lib/specYaml';
import {useTaskTarget} from '../../editor/taskTarget';
import {notifyFailure} from '../../../components/notify';

export type StepPreview = { preview: (table: string, index: number) => void; pending: boolean };

export function useStepPreview(spec: Spec, folderId: string): StepPreview {
    const startTask = useStartTask();
    const show = useTaskTarget();
    const preview = (table: string, index: number) => {
        const draft = throughStep(spec, table, index);
        startTask.mutate({kind: 'preview', dataset: spec.dataset ?? '', content: writeSpec(draft as Record<string, unknown>).text, folderId}, {
            onSuccess: ({taskId, runId}) => show({
                kind: 'preview', taskId, runId, startedAt: Date.now(),
                title: `${table} after step ${index + 1}`, focus: `gold-${table}`,
            }),
            onError: notifyFailure('Preview not started'),
        });
    };
    return {preview, pending: startTask.isPending};
}
