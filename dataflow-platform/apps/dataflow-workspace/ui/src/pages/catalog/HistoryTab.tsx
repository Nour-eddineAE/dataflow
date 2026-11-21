import {Group, Select, Stack, Table, Text} from '@mantine/core';
import {ViewModal} from '../../components/FormModal';
import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useTableChanges, useTableHistory} from '../../api/hooks';
import type {Commit} from '../../api/types';
import {DataGrid} from '../../components/DataGrid';
import {Empty, ErrorState, Loading} from '../../components/ErrorState';
import {Panel} from '../../components/Panel';
import {Action} from '../../components/actions/Action';
import {IconAction} from '../../components/actions/IconAction';
import {IconArrowsDiff, IconTerminal2} from '../../icons';
import {formatNumber, formatTime, nounFor} from '../../lib/format';
import {links} from '../../lib/links';
import {changeSummary, labelChanges, type VersionRange, versionRangeTitle} from './versionRange';

const ACTIONS_WIDTH = {plain: 48, withChanges: 80};
const VERSION_SELECT_WIDTH = 120;

export function HistoryTab({dataset, tableId, sqlName, changeFeed}: {
    dataset: string;
    tableId: string;
    sqlName?: string | null;
    changeFeed: boolean
}) {
    const navigate = useNavigate();
    const [compared, setCompared] = useState<VersionRange>();
    const {data, error, isLoading} = useTableHistory(dataset, tableId, true);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.length) return <Empty title="No history"/>;
    return (
        <>
            {changeFeed && data.length > 1 && <CompareVersions commits={data} onCompare={setCompared}/>}
            <Panel flush>
                <Table.ScrollContainer minWidth={720}>
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th w={90}>Version</Table.Th>
                                <Table.Th w={200}>Timestamp</Table.Th>
                                <Table.Th w={200}>Operation</Table.Th>
                                <Table.Th>Metrics</Table.Th>
                                {sqlName && <Table.Th w={changeFeed ? ACTIONS_WIDTH.withChanges : ACTIONS_WIDTH.plain}/>}
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {data.map((commit) => (
                                <Table.Tr key={commit.version}>
                                    <Table.Td><Text size="sm" className="df-num">{commit.version}</Text></Table.Td>
                                    <Table.Td><Text size="sm"
                                                    className="df-num">{formatTime(commit.timestamp)}</Text></Table.Td>
                                    <Table.Td><Text size="sm">{commit.operation}</Text></Table.Td>
                                    <Table.Td><Text size="sm" c="dimmed">{metrics(commit)}</Text></Table.Td>
                                    {sqlName && (
                                        <Table.Td>
                                            <Group gap={2} wrap="nowrap">
                                                {changeFeed && (
                                                    <IconAction label={`Changes in version ${commit.version}`} icon={IconArrowsDiff}
                                                                onClick={() => setCompared({from: commit.version, to: commit.version})}/>
                                                )}
                                                <IconAction label={`Query version ${commit.version}`} icon={IconTerminal2}
                                                            onClick={() => navigate(links.query(atVersion(sqlName, commit.version)))}/>
                                            </Group>
                                        </Table.Td>
                                    )}
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                </Table.ScrollContainer>
                <ViewModal opened={compared !== undefined} onClose={() => setCompared(undefined)}
                           title={compared && versionRangeTitle(compared)} size="full">
                    {compared && <Changes dataset={dataset} tableId={tableId} range={compared}/>}
                </ViewModal>
            </Panel>
        </>
    );
}

function CompareVersions({commits, onCompare}: { commits: Commit[]; onCompare: (range: VersionRange) => void }) {
    const versions = commits.map((commit) => String(commit.version));
    const [from, setFrom] = useState<string | null>(null);
    const [to, setTo] = useState<string | null>(null);
    const ready = from !== null && to !== null && Number(from) <= Number(to);
    return (
        <Group gap="xs" mb="snug" justify="flex-end">
            <Select size="xs" w={VERSION_SELECT_WIDTH} placeholder="From version" aria-label="From version"
                    data={versions.filter((version) => to === null || Number(version) <= Number(to))}
                    value={from} onChange={setFrom}/>
            <Select size="xs" w={VERSION_SELECT_WIDTH} placeholder="To version" aria-label="To version"
                    data={versions.filter((version) => from === null || Number(version) >= Number(from))}
                    value={to} onChange={setTo}/>
            <Action size="sm" icon={IconArrowsDiff} label="Compare" disabled={!ready}
                    onClick={() => onCompare({from: Number(from), to: Number(to)})}/>
        </Group>
    );
}

function Changes({dataset, tableId, range}: { dataset: string; tableId: string; range: VersionRange }) {
    const {data, error, isLoading} = useTableChanges(dataset, tableId, range);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data) return null;
    const summary = changeSummary(data.counts);
    return (
        <Stack gap="sm">
            <Group gap="lg">
                {summary.length ? summary.map((entry) => (
                    <Text key={entry.label} size="sm"><b>{formatNumber(entry.rows)}</b> {entry.label}</Text>
                )) : <Text size="sm" c="dimmed">No row changed</Text>}
                {data.rows.truncated && <Text size="sm" c="dimmed">first {data.rows.rows.length} rows shown</Text>}
            </Group>
            {data.rows.rows.length > 0 && <DataGrid result={labelChanges(data.rows)} height={420}/>}
        </Stack>
    );
}

function atVersion(sqlName: string, version: number) {
    return `SELECT *\nFROM ${sqlName}\nLIMIT 100\nSETTINGS delta_lake_snapshot_version = ${version}`;
}

const METRICS: [string, string, string][] = [
    ['numOutputRows', 'row', 'written'], ['numTargetRowsInserted', 'row', 'inserted'], ['numTargetRowsUpdated', 'row', 'updated'],
    ['numAddedFiles', 'file', 'added'], ['numRemovedFiles', 'file', 'removed'], ['numFiles', 'file', ''],
    ['numDeletedFiles', 'file', 'deleted'],
];

function metrics(commit: Commit) {
    let values: Record<string, string> = {};
    try {
        values = JSON.parse(commit.metrics ?? '{}');
    } catch {
        return '';
    }
    return METRICS.filter(([key]) => values[key] !== undefined && values[key] !== '0')
        .map(([key, noun, verb]) => {
            const count = Number(values[key]);
            return `${formatNumber(count)} ${nounFor(count, noun)}${verb ? ` ${verb}` : ''}`;
        })
        .join(' · ');
}
