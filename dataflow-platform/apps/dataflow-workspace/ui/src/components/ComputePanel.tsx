import {Stack, Text, Tooltip} from '@mantine/core';
import {useState} from 'react';
import {useCompute, useComputePolicy, useRelease, useUpdateCompute} from '../api/hooks';
import type {ComputePolicy, ProjectCompute, TargetCompute} from '../api/types';
import {IconRocket} from '../icons';
import {
    type ComputeForm,
    driverSummary,
    executorSummary,
    formProblems,
    fromForm,
    policySummary,
    sparkSummary,
    toForm
} from '../lib/compute';
import {formatAgo, shortCommit} from '../lib/format';
import {Action} from './actions/Action';
import {ComputeFields} from './ComputeFields';
import {ErrorState} from './ErrorState';
import {FormModal} from './FormModal';
import {SettingRow, SettingsEmpty, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';
import {notifyDone, notifyFailure} from './notify';

type Editing = { project: ProjectCompute; target: TargetCompute };

export function ComputePanel() {
    const {data, error, isLoading} = useCompute();
    const {data: policy = {}} = useComputePolicy();
    const [editing, setEditing] = useState<Editing>();
    if (isLoading) return <SettingsLoading/>;
    if (error || !data) return <ErrorState error={error}/>;
    if (data.length === 0) return <SettingsEmpty>No project</SettingsEmpty>;
    return (
        <>
            {data.map((project) => (
                <ProjectRows key={project.folderId} project={project} onEdit={(target) => setEditing({project, target})}/>
            ))}
            <PolicyRows policy={policy}/>
            {editing && <ComputeModal key={`${editing.project.folderId}/${editing.target.name}`} {...editing} policy={policy}
                                      onClose={() => setEditing(undefined)}/>}
        </>
    );
}

function PolicyRows({policy}: { policy: ComputePolicy }) {
    const limits = policySummary(policy);
    return (
        <Panel flush title="Limits">
            <SettingRow label="Largest run">
                <SettingValue muted={!limits.length}>{limits.length ? limits.join(' · ') : 'None'}</SettingValue>
            </SettingRow>
        </Panel>
    );
}

function ProjectRows({project, onEdit}: { project: ProjectCompute; onEdit: (target: TargetCompute) => void }) {
    const release = useRelease();
    const [releasing, setReleasing] = useState<TargetCompute>();
    const head = project.head?.id;
    const confirmRelease = () => releasing && release.mutate({folderId: project.folderId, target: releasing.name}, {
        onSuccess: (view) => {
            setReleasing(undefined);
            notifyDone({
                title: `Released ${shortCommit(view.commit)}`,
                message: `${view.datasets.length} pipelines use it from their next run`
            });
        },
        onError: notifyFailure('Not released'),
    });
    const own = project.targets.flatMap((target) => target.pipelines);
    if (project.problem) {
        return (
            <Panel flush title={project.project}>
                <SettingRow label="Not readable" hint={project.problem}><SettingValue muted>{project.folder}</SettingValue></SettingRow>
            </Panel>
        );
    }
    return (
        <Panel flush title={project.project}>
            {project.targets.map((target) => {
                const behind = !!head && target.production && target.releasedCommit !== head;
                const size = [driverSummary(target.compute), executorSummary(target.compute) && `executors ${executorSummary(target.compute)}`,
                    ...sparkSummary(target.compute)].filter(Boolean);
                return (
                    <SettingRow key={target.name} label={target.name}
                                hint={target.releasedCommit ? `Released ${shortCommit(target.releasedCommit)} ${formatAgo(target.releasedAt)}` : 'Never released'}>
                        <SettingValue muted={!size.length}>{size.length ? size.join(' · ') : 'Default'}</SettingValue>
                        {behind && project.rights.release && (
                            <Action size="sm" icon={IconRocket} label="Release" onClick={() => setReleasing(target)}/>
                        )}
                        <Action size="sm" label="Edit" disabled={!project.rights.edit} onClick={() => onEdit(target)}/>
                    </SettingRow>
                );
            })}
            {own.length > 0 && (
                <SettingRow label="Own size">
                    {own.map((pipeline) => (
                        <Tooltip key={pipeline.dataset} label={[driverSummary(pipeline.compute), executorSummary(pipeline.compute),
                            ...sparkSummary(pipeline.compute)].filter(Boolean).join(' · ')}>
                            <span className="df-chip">{pipeline.dataset}</span>
                        </Tooltip>
                    ))}
                </SettingRow>
            )}
            <FormModal opened={!!releasing} onClose={() => setReleasing(undefined)}
                       title={`Release ${project.project} to ${releasing?.name ?? ''}?`} submit="Release"
                       loading={release.isPending} onSubmit={confirmRelease}>
                <Text size="sm">{head && `${shortCommit(head)} replaces ${releasing?.releasedCommit ? shortCommit(releasing.releasedCommit) : 'nothing released yet'}.`}</Text>
            </FormModal>
        </Panel>
    );
}

function ComputeModal({project, target, policy, onClose}: Editing & { policy: ComputePolicy; onClose: () => void }) {
    const update = useUpdateCompute();
    const [form, setForm] = useState<ComputeForm>(() => toForm(target.compute));
    const problems = formProblems(form, policy);

    const save = () => update.mutate({folderId: project.folderId, target: target.name, compute: fromForm(form, target.compute)}, {
        onSuccess: (view) => {
            notifyDone({
                title: `Committed ${view.head ? shortCommit(view.head.id) : ''}`,
                message: target.production ? 'Release it to apply the new size' : 'Pushed to the folder\'s branch',
            });
            onClose();
        },
        onError: notifyFailure('Not saved'),
    });

    return (
        <FormModal opened onClose={onClose} size="md" title={`Compute · ${project.project} ${target.name}`} submit="Commit and push"
                   submitDisabled={Object.keys(problems).length > 0} loading={update.isPending} onSubmit={save}>
            <Stack gap="lg">
                <ComputeFields form={form} problems={problems} onChange={setForm}/>
                <Text size="xs" c="dimmed">
                    Commits dataflow.yml to {target.branch ?? 'the folder\'s branch'} and pushes it. Empty fields use the install default.
                </Text>
            </Stack>
        </FormModal>
    );
}
