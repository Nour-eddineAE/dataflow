import {Anchor, Table, Text, Tooltip} from '@mantine/core';
import {Link, useNavigate} from 'react-router-dom';
import {usePrefetch} from '../api/hooks';
import type {RunSummary} from '../api/types';
import {formatAgo, formatDuration, formatTime, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {StatusBadge} from './StatusBadge';

const TRIGGERS: Record<string, string> = {
    schedule: 'Schedule',
    manual: 'Manual',
    backfill: 'Backfill',
    inputs: 'Inputs landed',
    sensor: 'Sensor',
};

export const triggerLabel = (trigger: string) => TRIGGERS[trigger] ?? trigger;

export function RunsTable({runs, showDataset = true, failedIn}: { runs: RunSummary[]; showDataset?: boolean; failedIn?: Record<string, string> }) {
    const navigate = useNavigate();
    const prefetch = usePrefetch();
    return (
        <Table.ScrollContainer minWidth={760}>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th>Start time</Table.Th>
                        {showDataset && <Table.Th>Pipeline</Table.Th>}
                        <Table.Th>Run</Table.Th>
                        <Table.Th>Started by</Table.Th>
                        <Table.Th>Partition</Table.Th>
                        <Table.Th>Duration</Table.Th>
                        {failedIn && <Table.Th>Failed in</Table.Th>}
                        <Table.Th>Status</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {runs.map((run) => (
                        <Table.Tr key={run.runId} className="df-row-link" onClick={() => navigate(links.run(run.runId))}
                                  onMouseEnter={() => prefetch.run(run.runId)}>
                            <Table.Td>
                                <Tooltip label={formatAgo(run.startedAt ?? run.createdAt)}>
                                    <Text size="sm"
                                          className="df-num">{formatTime(run.startedAt ?? run.createdAt)}</Text>
                                </Tooltip>
                            </Table.Td>
                            {showDataset && (
                                <Table.Td>
                                    {run.dataset ? (
                                        <Anchor component={Link} to={links.pipeline(run.dataset)} size="sm"
                                                className="df-link" onClick={(event) => event.stopPropagation()}>
                                            {run.dataset}
                                        </Anchor>
                                    ) : (
                                        <Text size="sm">{run.job}</Text>
                                    )}
                                </Table.Td>
                            )}
                            <Table.Td>
                                <Anchor component={Link} to={links.run(run.runId)} size="sm" className="df-link df-mono"
                                        onClick={(event) => event.stopPropagation()}>{shortRunId(run.runId)}</Anchor>
                            </Table.Td>
                            <Table.Td><Text size="sm">{run.startedBy ?? triggerLabel(run.trigger)}</Text></Table.Td>
                            <Table.Td><Text size="sm"
                                            c={run.partition ? undefined : 'dimmed'}>{run.partition ?? '—'}</Text></Table.Td>
                            <Table.Td><Text size="sm"
                                            className="df-num">{formatDuration(run.durationMs)}</Text></Table.Td>
                            {failedIn && <Table.Td><Text size="sm">{failedIn[run.runId] ?? '—'}</Text></Table.Td>}
                            <Table.Td><StatusBadge status={run.status}/></Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Table.ScrollContainer>
    );
}
