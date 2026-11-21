import {Box, Group, Progress, Table, Text, Tooltip} from '@mantine/core';
import {useColumnUsage} from '../api/hooks';
import type {ColumnProfile, TableProfile} from '../api/types';
import {formatBytes, formatCompact, formatNumber, formatPercent} from '../lib/format';
import {shortType} from '../lib/types';
import {Panel} from './Panel';
import {NumberCell} from './NumberCell';
import {COLOR} from '../lib/colors';

export const BAR_HEIGHT = 4;

const BAR_WIDTH = 48;
const MIN_WIDTH = 640;
const BOUND_WIDTH = 180;

const SMALLEST_SHOWN = 0.1;

const EXACT_UP_TO = 65_536;

export function ProfileTable({profile, sqlName}: { profile: TableProfile; sqlName?: string | null }) {
    const {data: usage} = useColumnUsage(sqlName);
    const queries = new Map((usage ?? []).map((entry) => [entry.name, entry.queries]));
    return (
        <Panel flush title="Columns" actions={(
            <Text size="xs" c="dimmed" className="df-num">
                {formatNumber(profile.rows)} rows · {formatBytes(profile.bytesRead)} read in {profile.elapsedMs} ms
            </Text>
        )}>
            <Table.ScrollContainer minWidth={MIN_WIDTH}>
                <Table>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>Name</Table.Th>
                            <Table.Th w={110}>Type</Table.Th>
                            <Table.Th w={120}>Missing</Table.Th>
                            <Table.Th w={100} ta="right">Distinct</Table.Th>
                            <Table.Th>Min</Table.Th>
                            <Table.Th>Max</Table.Th>
                            {queries.size > 0 && <Table.Th w={90} ta="right" title="Queries in the last 30 days">Queries</Table.Th>}
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                        {profile.columns.map((column) => (
                            <Table.Tr key={column.name}>
                                <Table.Td><Text size="sm">{column.name}</Text></Table.Td>
                                <Table.Td><Text size="sm" c="dimmed">{shortType(column.type)}</Text></Table.Td>
                                <Table.Td><Missing column={column} rows={profile.rows}/></Table.Td>
                                <NumberCell>{distinct(column.distinct)}</NumberCell>
                                <Table.Td maw={BOUND_WIDTH}><Bound value={column.min}/></Table.Td>
                                <Table.Td maw={BOUND_WIDTH}><Bound value={column.max}/></Table.Td>
                                {queries.size > 0 && <NumberCell>{formatNumber(queries.get(column.name) ?? 0)}</NumberCell>}
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </Table.ScrollContainer>
        </Panel>
    );
}

function Missing({column, rows}: { column: ColumnProfile; rows: number }) {
    const share = rows ? (column.nulls / rows) * 100 : 0;
    return (
        <Group gap="sm" wrap="nowrap">
            {share > 0 ? <Progress value={share} size={BAR_HEIGHT} w={BAR_WIDTH} color={COLOR.warning} aria-label="Missing"/>
                : <Box w={BAR_WIDTH}/>}
            <Tooltip label={`${formatNumber(column.nulls)} rows`}>
                <Text size="sm" c={share > 0 ? undefined : 'dimmed'} className="df-num">{percent(share)}</Text>
            </Tooltip>
        </Group>
    );
}

function Bound({value}: { value?: string | null }) {
    if (value === null || value === undefined) return <Text size="sm" c="dimmed">—</Text>;
    const tidy = value.replace(/^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)\.0+$/, '$1');
    return <Text size="sm" truncate className="df-mono" fz="xs" title={tidy}>{tidy}</Text>;
}

function distinct(value?: number | null) {
    if (value === null || value === undefined) return '—';
    return value < EXACT_UP_TO ? formatNumber(value) : `≈ ${formatCompact(value)}`;
}

function percent(share: number) {
    if (share === 0) return '0%';
    if (share < SMALLEST_SHOWN) return `< ${SMALLEST_SHOWN}%`;
    return formatPercent(share);
}
