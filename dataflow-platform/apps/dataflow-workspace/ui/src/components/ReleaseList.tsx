import {Badge, Stack, Table, Text} from '@mantine/core';
import {useState} from 'react';
import {useReleaseHistory, useReleases} from '../api/hooks';
import type {ReleaseView} from '../api/types';
import {formatAgo, shortCommit} from '../lib/format';
import {Empty, ErrorState} from './ErrorState';
import {Panel} from './Panel';
import {TableSkeleton} from './TableSkeleton';

export function ReleaseList() {
    const {data, error, isLoading} = useReleases();
    const [project, setProject] = useState<string>();
    if (isLoading) return <TableSkeleton/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.length) return <Empty title="Nothing released yet"/>;
    return (
        <Stack gap="lg">
            <Panel flush title="Current releases">
                <ReleaseTable releases={data} onSelect={setProject}/>
            </Panel>
            {project && <ReleaseHistory project={project}/>}
        </Stack>
    );
}

function ReleaseTable({releases, onSelect}: { releases: ReleaseView[]; onSelect: (project: string) => void }) {
    return (
        <Table>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Project</Table.Th>
                    <Table.Th>Target</Table.Th>
                    <Table.Th>Commit</Table.Th>
                    <Table.Th>Datasets</Table.Th>
                    <Table.Th>Released</Table.Th>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {releases.map((release) => (
                    <Table.Tr key={`${release.project}/${release.target}`} className="df-row-link"
                              onClick={() => onSelect(release.project)}>
                        <Table.Td><Text size="sm" fw={500}>{release.project}</Text></Table.Td>
                        <Table.Td><Badge>{release.target}</Badge></Table.Td>
                        <Table.Td><Text size="sm" ff="monospace">{shortCommit(release.commit)}</Text></Table.Td>
                        <Table.Td><Text size="sm">{release.datasets.join(', ') || '—'}</Text></Table.Td>
                        <Table.Td><Text size="sm">{formatAgo(release.releasedAt)}</Text><Text size="xs"
                                                                                              c="dimmed">by {release.releasedBy}</Text></Table.Td>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}

function ReleaseHistory({project}: { project: string }) {
    const {data, error, isLoading} = useReleaseHistory(project);
    return (
        <Panel flush title={`History of ${project}`}>
            {isLoading ? <TableSkeleton rows={3}/> : error ? <ErrorState error={error}/> : (
                <Table>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>Target</Table.Th>
                            <Table.Th>Commit</Table.Th>
                            <Table.Th>Released</Table.Th>
                            <Table.Th>By</Table.Th>
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                        {(data ?? []).map((record) => (
                            <Table.Tr key={`${record.target}/${record.releasedAt}`}>
                                <Table.Td><Text size="sm">{record.target}</Text></Table.Td>
                                <Table.Td><Text size="sm" ff="monospace">{shortCommit(record.commit)}</Text></Table.Td>
                                <Table.Td><Text size="sm">{formatAgo(record.releasedAt)}</Text></Table.Td>
                                <Table.Td><Text size="sm">{record.releasedBy}</Text></Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            )}
        </Panel>
    );
}
