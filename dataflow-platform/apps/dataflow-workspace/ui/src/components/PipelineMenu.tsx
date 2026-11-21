import {Menu, Select, Stack, Text, TextInput} from '@mantine/core';
import {useDisclosure} from '@mantine/hooks';
import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useLaunch, usePartitions, useSchedule, useToggleSchedule} from '../api/hooks';
import type {DatasetSummary} from '../api/types';
import {IconDots, IconPlayerPause, IconPlayerPlay, IconRefresh} from '../icons';
import {ICON} from '../lib/icons';
import {links} from '../lib/links';
import {statusTone} from '../lib/status';
import {FormModal} from './FormModal';
import {IconAction} from './actions/IconAction';
import {notifyFailure} from './notify';
import {COLOR} from '../lib/colors';

export function PipelineMenu({summary}: { summary: DatasetSummary }) {
    const [refreshing, {open, close}] = useDisclosure(false);
    return (
        <>
            <Menu position="bottom-end" width={190}>
                <Menu.Target>
                    <IconAction label="More" icon={IconDots} outlined size={32}/>
                </Menu.Target>
                <Menu.Dropdown>
                    {summary.schedule && <ScheduleItem dataset={summary.dataset}/>}
                    {summary.source && (
                        <>
                            {summary.schedule && <Menu.Divider/>}
                            <Menu.Item leftSection={<IconRefresh {...ICON}/>} color={COLOR.danger} onClick={open}>Full refresh</Menu.Item>
                        </>
                    )}
                </Menu.Dropdown>
            </Menu>
            <FullRefreshModal summary={summary} opened={refreshing} onClose={close}/>
        </>
    );
}

function ScheduleItem({dataset}: { dataset: string }) {
    const {data: state} = useSchedule(dataset, true);
    const toggle = useToggleSchedule(dataset);
    const change = (action: 'pause' | 'resume') => toggle.mutate(action, {
        onError: notifyFailure(`Schedule not ${action}d`),
    });
    return state?.running ? (
        <Menu.Item leftSection={<IconPlayerPause {...ICON}/>} disabled={toggle.isPending}
                   onClick={() => change('pause')}>Pause schedule</Menu.Item>
    ) : (
        <Menu.Item leftSection={<IconPlayerPlay {...ICON}/>} disabled={!state || toggle.isPending}
                   onClick={() => change('resume')}>Resume schedule</Menu.Item>
    );
}

function FullRefreshModal({summary, opened, onClose}: {
    summary: DatasetSummary;
    opened: boolean;
    onClose: () => void
}) {
    const navigate = useNavigate();
    const launch = useLaunch();
    const dataset = summary.dataset;
    const partitioned = !!summary.partitions;
    const {data: partitions} = usePartitions(dataset, partitioned && opened);
    const [typed, setTyped] = useState('');
    const [partition, setPartition] = useState<string | null>(null);
    const ready = typed === dataset && (!partitioned || !!partition);
    const refresh = () => launch.mutate({dataset, partition: partition ?? undefined, fullRefresh: true}, {
        onSuccess: ({runId}) => {
            onClose();
            navigate(links.run(runId));
        },
        onError: notifyFailure('Full refresh not started'),
    });
    return (
        <FormModal opened={opened} onClose={onClose} title={`Full refresh of ${dataset}`} submit="Rebuild" danger
                   submitDisabled={!ready} loading={launch.isPending} onSubmit={refresh}>
            <Stack>
                <Text size="sm">
                    Deletes bronze, silver, quarantine and history, then rebuilds them from the landed files. Their
                    tables are unavailable until the run ends, and versions of a record are only as old as the files
                    that are still landed.
                </Text>
                {partitioned && (
                    <Select
                        label="Partition to fetch"
                        searchable
                        data={(partitions?.partitions ?? []).slice().reverse().map((entry) => ({
                            value: entry.key,
                            label: `${entry.key} · ${statusTone(entry.status).label}`,
                        }))}
                        value={partition}
                        onChange={setPartition}
                        comboboxProps={{withinPortal: true}}
                    />
                )}
                <TextInput label={`Type ${dataset} to confirm`} value={typed} data-autofocus
                           onChange={(event) => setTyped(event.currentTarget.value)}/>
            </Stack>
        </FormModal>
    );
}
