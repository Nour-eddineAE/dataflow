import {Loader, Select} from '@mantine/core';
import {useDisclosure} from '@mantine/hooks';
import {IconPlayerPlay} from '../icons';
import {type ReactNode, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {isRunning, useLaunch, usePartitions} from '../api/hooks';
import type {DatasetSummary} from '../api/types';
import {links} from '../lib/links';
import {statusTone} from '../lib/status';
import {FormModal} from './FormModal';

import {Action} from './actions/Action';
import {ICON_TINY} from '../lib/icons';
import {notifyFailure} from './notify';
import {newestNotRun} from '../lib/partitions';

export function useRunDataset(dataset: DatasetSummary): { start: () => void; pending: boolean; modal: ReactNode } {
    const navigate = useNavigate();
    const launch = useLaunch();
    const [opened, {open, close}] = useDisclosure(false);
    const partitioned = !!dataset.partitions;
    const {data: partitions} = usePartitions(dataset.dataset, partitioned && opened);
    const [picked, setPicked] = useState<string | null>(null);
    const partition = picked ?? newestNotRun(partitions?.partitions ?? []) ?? null;

    const launchRun = (chosen?: string) =>
        launch.mutate(
            {dataset: dataset.dataset, partition: chosen},
            {
                onSuccess: ({runId}) => {
                    close();
                    navigate(links.run(runId));
                },
                onError: notifyFailure('Run not started'),
            },
        );

    const modal = (
        <FormModal opened={opened} onClose={close} title={`Run ${dataset.dataset}`} submit="Run"
                   submitDisabled={!partition}
                   loading={launch.isPending} onSubmit={() => partition && launchRun(partition)}>
            <Select
                label="Partition"
                searchable
                data={(partitions?.partitions ?? []).slice().reverse().map((entry) => ({
                    value: entry.key,
                    label: `${entry.key} · ${statusTone(entry.status).label}`,
                }))}
                value={partition}
                onChange={setPicked}
                comboboxProps={{withinPortal: true}}
                data-autofocus
            />
        </FormModal>
    );

    return {start: () => (partitioned ? open() : launchRun()), pending: launch.isPending && !opened, modal};
}

export function RunButton({dataset}: { dataset: DatasetSummary }) {
    const navigate = useNavigate();
    const run = useRunDataset(dataset);
    const active = dataset.lastRun && isRunning(dataset.lastRun.status) ? dataset.lastRun : undefined;
    if (active) {
        return (
            <Action label="Running" leading={<Loader {...ICON_TINY}/>} onClick={() => navigate(links.run(active.runId))}/>
        );
    }
    return (
        <>
            <Action tone="primary" icon={IconPlayerPlay} label="Run now" loading={run.pending} disabled={!dataset.valid}
                    onClick={run.start}/>
            {run.modal}
        </>
    );
}
