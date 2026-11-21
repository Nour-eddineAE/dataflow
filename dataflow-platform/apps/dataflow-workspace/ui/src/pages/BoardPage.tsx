import {Badge, Box, CloseButton, Group, ScrollArea, SegmentedControl, SimpleGrid, Skeleton, Stack, Table, Text, Tooltip, useComputedColorScheme} from '@mantine/core';
import {
    IconAlertTriangle,
    IconCircleCheck,
    IconClock,
    IconCode,
    IconHandStop,
    IconPlayerPause,
    IconTable
} from '../icons';
import {type ReactNode, useMemo, useState} from 'react';
import {useParams} from 'react-router-dom';
import {useBoard, useBoardChart, useBoardOverview} from '../api/hooks';
import type {BoardChart, BoardOverview, Freshness, TableBoard} from '../api/types';
import {EChart} from '../components/EChart';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {StatCard} from '../components/StatCard';
import {StatusIcon} from '../components/StatusBadge';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {
    barsOption,
    bucketLabels,
    flowsOption,
    histogramOption,
    quarantineOption,
    rulesOption,
    runsOption,
    sizeOption,
    timeOption
} from '../lib/boardOptions';
import type {Scheme} from '../lib/chartTheme';
import {cellText, formatAgo, formatBytes, formatNumber} from '../lib/format';
import {ICON, ICON_LARGE} from '../lib/icons';
import {IconAction} from '../components/actions/IconAction';
import {NumberCell} from '../components/NumberCell';
import {PAGE, links} from '../lib/links';
import {COLOR} from '../lib/colors';
import {FADE} from '../lib/motion';

const REFRESHING_OPACITY = 0.6;

const RANGES = [{value: 'all', label: 'All'}, {value: '1y', label: '1y'}, {value: '30d', label: '30d'}, {
    value: '7d',
    label: '7d'
}];

type Filters = Record<string, Record<string, string>>;

export function BoardPage() {
    const {dataset} = useParams();
    const board = useBoard(dataset);
    const overview = useBoardOverview(dataset);
    const scheme = useComputedColorScheme('light') as Scheme;
    const [range, setRange] = useState('all');
    const [filters, setFilters] = useState<Filters>({});

    const crumbs = [{label: 'Dashboards', to: PAGE.dashboards}];

    if (board.error) return <Page wide><PageHeader title={dataset} crumbs={crumbs}/><ErrorState error={board.error}/></Page>;
    if (!board.data) return <Loading/>;

    const filter = (table: string, column: string, value?: string) =>
        setFilters((current) => {
            const next = {...(current[table] ?? {})};
            if (value === undefined) delete next[column];
            else next[column] = value;
            return {...current, [table]: next};
        });

    return (
        <Page wide>
            <PageHeader title={dataset} crumbs={crumbs}
                        actions={<SegmentedControl value={range} onChange={setRange} data={RANGES}/>}/>
            <Stack gap="xl">
                {board.data.problems.map((problem) => <Text key={problem} size="sm" c={COLOR.danger}
                                                            className="df-mono">{problem}</Text>)}
                {overview.data && <OverviewSection overview={overview.data} scheme={scheme}
                                                   rows={board.data.tables.find((table) => table.table === 'current')?.rows}/>}
                {board.data.tables.length === 0 && <Empty title="Nothing written yet"/>}
                {board.data.tables.map((table) => (
                    <TableSection key={table.table} dataset={board.data.dataset} table={table} range={range}
                                  scheme={scheme}
                                  filters={filters[table.table] ?? {}}
                                  onFilter={(column, value) => filter(table.table, column, value)}/>
                ))}
            </Stack>
        </Page>
    );
}

function OverviewSection({overview, scheme, rows}: { overview: BoardOverview; scheme: Scheme; rows?: number }) {
    const last = overview.runs[0];
    const lastRules = overview.rules.filter((rule) => rule.runId === last?.runId);
    const failing = lastRules.filter((rule) => rule.failing > 0).length;
    const runs = useMemo(() => runsOption(overview, scheme), [overview, scheme]);
    const flows = useMemo(() => flowsOption(overview, scheme), [overview, scheme]);
    const rules = useMemo(() => rulesOption(overview, scheme), [overview, scheme]);
    const quarantine = useMemo(() => quarantineOption(overview, scheme), [overview, scheme]);
    const size = useMemo(() => sizeOption(overview.storage, scheme), [overview, scheme]);
    return (
        <Stack gap="md">
            <SectionTitle title="Overview"/>
            <SimpleGrid cols={{base: 2, md: 4}} spacing="md">
                <StatCard label="Freshness" value={<FreshnessValue freshness={overview.freshness}/>}/>
                <StatCard label="Last success"
                      value={overview.freshness.lastSuccess ? formatAgo(overview.freshness.lastSuccess) : '—'}/>
                <StatCard label="Current rows" value={formatNumber(rows)}/>
                <StatCard label="Rules failing, last run" value={lastRules.length ? `${failing} of ${lastRules.length}` : '—'}
                          icon={failing > 0 ? <StatusIcon status="FAILED" {...ICON_LARGE}/> : undefined}/>
            </SimpleGrid>
            <SimpleGrid cols={{base: 1, lg: 2}} spacing="md">
                <Panel framed title="Run duration">{overview.runs.length ? <EChart option={runs}/> : <Empty title="No data"/>}</Panel>
                <Panel framed title="Rows out per flow, last run">{overview.flows.length ? <EChart option={flows}/> :
                    <Empty title="No data"/>}</Panel>
                <Panel framed title="Failing rows per rule">{overview.rules.length ? <EChart option={rules}/> :
                    <Empty title="No data"/>}</Panel>
                <Panel framed title="Quarantine reasons">{overview.quarantine.length ? <EChart option={quarantine}/> :
                    <Empty title="No data"/>}</Panel>
            </SimpleGrid>
            {overview.storage.some((table) => table.sizeChanges.length > 0) && (
                <Panel framed title="Size per layer"><EChart option={size}/></Panel>
            )}
            {overview.storage.length > 0 && (
                <Panel framed title="Storage" flush>
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th>Table</Table.Th>
                                <Table.Th ta="right">Versions</Table.Th>
                                <Table.Th ta="right">Files</Table.Th>
                                <Table.Th ta="right">Size</Table.Th>
                                <Table.Th ta="right">Average file</Table.Th>
                                <Table.Th ta="right">Small files</Table.Th>
                                <Table.Th ta="right">Waiting for cleanup</Table.Th>
                                <Table.Th ta="right">Reclaimed</Table.Th>
                                <Table.Th>Updated</Table.Th>
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {overview.storage.map((table) => (
                                <Table.Tr key={table.table}>
                                    <Table.Td><Text size="sm" className="df-mono">{table.table}</Text></Table.Td>
                                    <NumberCell>{formatNumber(table.commits)}</NumberCell>
                                    <NumberCell>{formatNumber(table.files)}</NumberCell>
                                    <NumberCell>{formatBytes(table.bytes)}</NumberCell>
                                    <NumberCell color="dimmed">{table.files ? formatBytes(table.bytes / table.files) : '—'}</NumberCell>
                                    <NumberCell>{formatNumber(table.smallFiles)}</NumberCell>
                                    <NumberCell>{formatBytes(table.waitingBytes)}</NumberCell>
                                    <NumberCell color="dimmed">{table.reclaimedBytes ? formatBytes(table.reclaimedBytes) : '—'}</NumberCell>
                                    <Table.Td><Text size="sm"
                                                    c="dimmed">{table.updatedAt ? formatAgo(table.updatedAt) : '—'}</Text></Table.Td>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                </Panel>
            )}
        </Stack>
    );
}

function TableSection({dataset, table, range, scheme, filters, onFilter}: {
    dataset: string; table: TableBoard; range: string; scheme: Scheme; filters: Record<string, string>;
    onFilter: (column: string, value?: string) => void;
}) {
    return (
        <Stack gap="md">
            <Group gap="sm" wrap="wrap">
                <SectionTitle title={table.label}/>
                <Text size="xs" c="dimmed" className="df-mono">{table.sqlName}</Text>
                <Text size="xs" c="dimmed">{formatNumber(table.rows)} rows</Text>
                {Object.entries(filters).map(([column, value]) => (
                    <Badge key={column} rightSection={
                        <CloseButton size="xs" aria-label={`Remove ${column}`} onClick={() => onFilter(column)}/>}>
                        {column} = {value}
                    </Badge>
                ))}
            </Group>
            {table.charts.length === 0 ? <Empty title="No data"/> : (
                <SimpleGrid cols={{base: 1, lg: 2}} spacing="md">
                    {table.charts.map((chart) => (
                        <ChartCard key={chart.id} dataset={dataset} table={table} chart={chart}
                                   range={table.time ? range : 'all'}
                                   scheme={scheme} filters={filters} onFilter={onFilter}/>
                    ))}
                </SimpleGrid>
            )}
        </Stack>
    );
}

function ChartCard({dataset, table, chart, range, scheme, filters, onFilter}: {
    dataset: string;
    table: TableBoard;
    chart: BoardChart;
    range: string;
    scheme: Scheme;
    filters: Record<string, string>;
    onFilter: (column: string, value?: string) => void;
}) {
    const {data, error, isFetching} = useBoardChart(dataset, table.table, chart.id, range, filters);
    const [asTable, setAsTable] = useState(false);
    const option = useMemo(() => {
        if (!data) return undefined;
        if (chart.kind === 'TIME') return timeOption(chart, data, scheme);
        if (chart.kind === 'BARS') return barsOption(data, scheme, chart.measure ?? 'Rows');
        return histogramOption(chart, data, scheme);
    }, [chart, data, scheme]);
    const filterable = chart.kind === 'BARS' && table.filters.includes(chart.x);
    const actions = (
        <>
            <IconAction label={asTable ? 'Show the chart' : 'Show the table'} icon={IconTable} active={asTable}
                        onClick={() => setAsTable(!asTable)}/>
            {data && (
                <IconAction label="Open as SQL" icon={IconCode} to={links.query(data.sql)}/>
            )}
        </>
    );
    return (
        <Panel framed title={<Group gap="cozy" wrap="nowrap"><span>{chart.title}</span>{data?.grain &&
            <Text span size="xs" c="dimmed" fw={400}>per {data.grain}</Text>}</Group>} actions={actions}>
            <Box style={{opacity: isFetching && data ? REFRESHING_OPACITY : 1, transition: FADE}}>
                {error ? <ErrorState error={error}/>
                    : !data || !option ? <Skeleton h={240}/>
                        : data.rows.length === 0 ? <Empty title="No data"/>
                            : asTable ? <DataTable chart={chart} rows={data.rows}/>
                                : <EChart option={option}
                                          onSelect={filterable ? (value) => onFilter(chart.x, value) : undefined}/>}
            </Box>
        </Panel>
    );
}

function DataTable({chart, rows}: { chart: BoardChart; rows: unknown[][] }) {
    const labels = chart.kind === 'HISTOGRAM' ? bucketLabels(Number(chart.low), Number(chart.high)) : undefined;
    const headers = chart.kind === 'TIME' && chart.split ? [chart.x, chart.split, chart.measure ?? 'rows']
        : [chart.kind === 'HISTOGRAM' ? `${chart.x} from` : chart.x, chart.measure ?? 'rows'];
    return (
        <ScrollArea h={240} type="auto">
            <Table fz="xs" verticalSpacing={2} stickyHeader>
                <Table.Thead><Table.Tr>{headers.map((header) => <Table.Th key={header}>{header}</Table.Th>)}</Table.Tr></Table.Thead>
                <Table.Tbody>
                    {rows.map((row, index) => (
                        <Table.Tr key={index}>
                            {row.map((cell, column) => (
                                <Table.Td key={column}
                                          className="df-mono">{labels && column === 0 ? labels[Number(cell) - 1] : cellText(cell)}</Table.Td>
                            ))}
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </ScrollArea>
    );
}

const FRESHNESS: Record<Freshness['state'], { icon: ReactNode; label: string; color: string }> = {
    fresh: {icon: <IconCircleCheck {...ICON}/>, label: 'On time', color: COLOR.success},
    late: {icon: <IconAlertTriangle {...ICON}/>, label: 'Late', color: COLOR.warning},
    never: {icon: <IconClock {...ICON}/>, label: 'Never succeeded', color: COLOR.dimmed},
    manual: {icon: <IconHandStop {...ICON}/>, label: 'No schedule', color: COLOR.dimmed},
    paused: {icon: <IconPlayerPause {...ICON}/>, label: 'Schedule paused', color: COLOR.dimmed},
};

function FreshnessValue({freshness}: { freshness: Freshness }) {
    const state = FRESHNESS[freshness.state];
    return (
        <Tooltip
            label={freshness.expectedBy ? `Next success expected by ${new Date(freshness.expectedBy).toLocaleString()}` : state.label}
            disabled={!freshness.expectedBy}>
            <Group gap="snug" wrap="nowrap" style={{color: state.color}}>{state.icon}<span>{state.label}</span></Group>
        </Tooltip>
    );
}

function SectionTitle({title}: { title: string }) {
    return <Text fw={600} size="sm">{title}</Text>;
}

