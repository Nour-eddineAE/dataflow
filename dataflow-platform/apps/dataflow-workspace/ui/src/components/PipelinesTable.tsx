import {Anchor, Group, Menu, Table, Text} from '@mantine/core';
import {IconDots, IconPlayerPlay} from '../icons';
import {Link, useNavigate} from 'react-router-dom';
import type {DatasetSummary} from '../api/types';
import {describeTrigger, formatAgo} from '../lib/format';
import {links} from '../lib/links';
import {usePrefetch, useSchedules} from '../api/hooks';
import {useRunDataset} from './RunButton';
import {EditSpecMenuItem} from './EditSpecButton';
import {StatusBadge} from './StatusBadge';

import {Hint} from './Hint';
import {IconAction} from './actions/IconAction';

export function PipelinesTable({datasets}: { datasets: DatasetSummary[] }) {
    const navigate = useNavigate();
    const prefetch = usePrefetch();
    const {data: running} = useSchedules();
    return (
        <Table.ScrollContainer minWidth={820}>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th>Name</Table.Th>
                        <Table.Th>Owner</Table.Th>
                        <Table.Th>Schedule</Table.Th>
                        <Table.Th>Last run</Table.Th>
                        <Table.Th>Status</Table.Th>
                        <Table.Th w={48}/>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {datasets.map((dataset) => (
                        <Table.Tr key={dataset.dataset} className="df-row-link"
                                  onClick={() => navigate(links.pipeline(dataset.dataset))}
                                  onMouseEnter={() => dataset.valid && prefetch.pipeline(dataset.dataset)}>
                            <Table.Td maw={420}>
                                <Hint label={dataset.description}>
                                    <Anchor component={Link} to={links.pipeline(dataset.dataset)} size="sm"
                                            className="df-link" onClick={(event) => event.stopPropagation()}>
                                        {dataset.dataset}
                                    </Anchor>
                                </Hint>
                            </Table.Td>
                            <Table.Td><Text size="sm">{dataset.owner ?? '—'}</Text></Table.Td>
                            <Table.Td>
                                <Group gap="snug" wrap="nowrap">
                                    <Text size="sm">{describeTrigger(dataset.schedule, (dataset.counts.inputs ?? 0) > 0)}</Text>
                                    {running?.[dataset.dataset] === false &&
                                        <StatusBadge status="PAUSED"/>}
                                </Group>
                                {dataset.partitions && <Text size="xs" c="dimmed">{dataset.partitions}</Text>}
                            </Table.Td>
                            <Table.Td>
                                <Text size="sm" c={dataset.lastRun ? undefined : 'dimmed'}>
                                    {dataset.lastRun ? formatAgo(dataset.lastRun.startedAt ?? dataset.lastRun.createdAt) : '—'}
                                </Text>
                            </Table.Td>
                            <Table.Td>
                                {dataset.valid ? <StatusBadge status={dataset.lastRun?.status}/> :
                                    <StatusBadge status="FAILED"/>}
                                {!dataset.valid && <Text size="xs" c="dimmed">Invalid spec</Text>}
                            </Table.Td>
                            <Table.Td onClick={(event) => event.stopPropagation()}>
                                <RowMenu dataset={dataset}/>
                            </Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Table.ScrollContainer>
    );
}

function RowMenu({dataset}: { dataset: DatasetSummary }) {
    const run = useRunDataset(dataset);
    return (
        <Group gap={2} wrap="nowrap" justify="flex-end">
            <IconAction label="Run now" icon={IconPlayerPlay} disabled={!dataset.valid} loading={run.pending} onClick={run.start}/>
            <Menu position="bottom-end" width={160}>
                <Menu.Target>
                    <IconAction label="More" icon={IconDots}/>
                </Menu.Target>
                <Menu.Dropdown>
                    <EditSpecMenuItem dataset={dataset.dataset}/>
                </Menu.Dropdown>
            </Menu>
            {run.modal}
        </Group>
    );
}
