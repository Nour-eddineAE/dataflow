import {Anchor, Table, Text} from '@mantine/core';
import {Link} from 'react-router-dom';
import {useMe, useQueryHistory} from '../../api/hooks';
import type {QueryRun} from '../../api/types';
import {Empty, ErrorState} from '../../components/ErrorState';
import {NumberCell} from '../../components/NumberCell';
import {Panel} from '../../components/Panel';
import {StatusBadge} from '../../components/StatusBadge';
import {TableSkeleton} from '../../components/TableSkeleton';
import {formatAgo, formatCompact, formatDuration, formatTime} from '../../lib/format';
import {links} from '../../lib/links';

const FINISHED = 'QueryFinish';
const ONE_LINE = {whiteSpace: 'nowrap'} as const;
const ERROR_WIDTH = 320;

export function QueryHistory({filter}: { filter: string }) {
    const {data, error, isLoading} = useQueryHistory();
    const {data: me} = useMe();
    const term = filter.trim().toLowerCase();
    const runs = (data ?? []).filter((run) => !term || `${run.query} ${run.person}`.toLowerCase().includes(term));
    return (
        <Panel flush>
            {isLoading ? <TableSkeleton rows={4}/> : error ? <ErrorState error={error}/> : !runs.length ?
                <Empty title="No query in the last 7 days"/> : (
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th w={1} style={ONE_LINE}>When</Table.Th>
                                {me?.admin && <Table.Th w={1} style={ONE_LINE}>Person</Table.Th>}
                                <Table.Th>Query</Table.Th>
                                <Table.Th w={1} style={ONE_LINE}>Status</Table.Th>
                                <Table.Th w={1} ta="right" style={ONE_LINE}>Duration</Table.Th>
                                <Table.Th w={1} ta="right" style={ONE_LINE}>Rows read</Table.Th>
                                <Table.Th w={1} ta="right" style={ONE_LINE}>Rows out</Table.Th>
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {runs.map((run, index) => <HistoryRow key={`${run.time}-${index}`} run={run} person={!!me?.admin}/>)}
                        </Table.Tbody>
                    </Table>
                )}
        </Panel>
    );
}

function HistoryRow({run, person}: { run: QueryRun; person: boolean }) {
    const failed = run.status !== FINISHED;
    return (
        <Table.Tr>
            <Table.Td style={ONE_LINE}><Text size="sm" c="dimmed" title={formatTime(run.time)}>{formatAgo(run.time)}</Text></Table.Td>
            {person && <Table.Td style={ONE_LINE}><Text size="sm" c="dimmed">{run.person}</Text></Table.Td>}
            <Table.Td maw={0}>
                <Anchor component={Link} to={links.query(run.query)} size="sm" className="df-link df-mono"
                        title="Open in the SQL editor" display="block" truncate>{run.query}</Anchor>
            </Table.Td>
            <Table.Td maw={ERROR_WIDTH}>
                <StatusBadge status={failed ? 'FAILURE' : 'SUCCESS'}/>
                {failed && run.error && <Text size="xs" c="dimmed" lineClamp={2} title={run.error}>{run.error}</Text>}
            </Table.Td>
            <NumberCell>{formatDuration(run.durationMs)}</NumberCell>
            <NumberCell>{formatCompact(run.rowsRead)}</NumberCell>
            <NumberCell>{failed ? '—' : formatCompact(run.resultRows)}</NumberCell>
        </Table.Tr>
    );
}
