import {Anchor, Box, Center, CopyButton, Group, Loader, ScrollArea, Stack, Table, Tabs, Text, VisuallyHidden} from '@mantine/core';
import {IconCheck, IconCopy} from '../../icons';
import {type ReactNode, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {taskFinished, useTask} from '../../api/hooks';
import type {ImpactResult, PreviewResult, QueryResult, SampleOrigin, Tabular, TaskKind} from '../../api/types';
import {DataGrid} from '../../components/DataGrid';
import {ErrorState} from '../../components/ErrorState';
import {actionLabel, failingColor} from '../../components/QualityRules';
import {StatusIcon} from '../../components/StatusBadge';
import {formatNumber, shortRunId} from '../../lib/format';
import {links} from '../../lib/links';
import {statusTone} from '../../lib/status';

import {Action} from '../../components/actions/Action';
import {CLOCK_TICK, SECOND} from '../../lib/timing';
import {NumberCell} from '../../components/NumberCell';
import {COLOR} from '../../lib/colors';
import {ICON_SMALL, ICON_TINY} from '../../lib/icons';

export type RunningTask = { kind: EditorTask; taskId: string; runId: string; startedAt: number; title?: string; focus?: string };

export type EditorTask = Exclude<TaskKind, 'infer'>;

export const TASK_TITLES: Record<EditorTask, string> = {preview: 'Preview', impact: 'Data impact'};

const ORIGINS: Record<SampleOrigin, string> = {BRONZE: 'bronze', SOURCE: 'the source'};
const STARTING = 'Starting';

export function TaskSummary({task}: { task: RunningTask }) {
    const {data, error} = useTask(task);
    const finished = taskFinished(data?.status);
    const elapsed = useElapsed(task.startedAt, !finished && !error);
    const result = data?.result;
    const title = task.title ?? TASK_TITLES[task.kind];
    const outcome = result || finished ? (result?.status === 'done' ? 'SUCCESS' : 'FAILURE') : undefined;
    const state = outcome ?? data?.status;
    return (
        <Group gap="sm" wrap="nowrap" style={{minWidth: 0}}>
            <Text fw={600} size="sm">{title}</Text>
            <VisuallyHidden role="status">{`${title}: ${state ? statusTone(state).label : STARTING}`}</VisuallyHidden>
            {outcome ? (
                <Group gap="snug" wrap="nowrap">
                    <StatusIcon status={outcome} {...ICON_SMALL}/>
                    <Text size="xs" c="dimmed">
                        {result ? summary(result.result as PreviewResult | ImpactResult | undefined) : 'No result'} · {elapsed} s
                    </Text>
                </Group>
            ) : (
                <Group gap="snug" wrap="nowrap">
                    <Loader {...ICON_TINY}/>
                    <Text size="xs" c="dimmed">{data ? statusTone(data.status).label : STARTING} · {elapsed} s</Text>
                </Group>
            )}
            <Anchor component={Link} to={links.run(task.runId)} size="xs" c="dimmed">Run {shortRunId(task.runId)}</Anchor>
        </Group>
    );
}

export function TaskResult({task}: { task: RunningTask }) {
    const {data, error} = useTask(task);
    const finished = taskFinished(data?.status);
    const result = data?.result;
    if (error) return <Box p="md"><ErrorState error={error}/></Box>;
    if (!result) {
        return finished ? (
            <Box p="md">
                <ErrorState title="No result"
                            error={`The engine stopped (${statusTone(data?.status ?? '').label.toLowerCase()}) before writing a result. The run's log says why.`}/>
            </Box>
        ) : <Waiting status={data?.status}/>;
    }
    if (result.status === 'failed') return <Box p="md"><ErrorState error={result.error ?? 'The task failed'}/></Box>;
    return task.kind === 'preview' ? <PreviewView preview={result.result as PreviewResult} focus={task.focus}/>
        : <ImpactView impact={result.result as ImpactResult}/>;
}

function useElapsed(since: number, ticking: boolean) {
    const [now, setNow] = useState(() => Date.now());
    useEffect(() => {
        if (!ticking) return;
        const timer = window.setInterval(() => setNow(Date.now()), CLOCK_TICK);
        return () => window.clearInterval(timer);
    }, [ticking]);
    return Math.max(0, Math.round((now - since) / SECOND));
}

function summary(result: PreviewResult | ImpactResult | undefined) {
    if (!result) return 'No result';
    if (!result.origin) return `${result.gold.length} tables built from the inputs`;
    return `${formatNumber(result.rows)} rows sampled from ${ORIGINS[result.origin]}`;
}

function Waiting({status}: { status?: string }) {
    return (
        <Center h="100%">
            <Stack align="center" gap="snug">
                <Loader size="sm"/>
                <Text size="sm"
                      c="dimmed">{status === 'STARTED' ? 'The engine is running the draft' : 'Waiting for the engine to start'}</Text>
            </Stack>
        </Center>
    );
}

function grid(tabular: Tabular): QueryResult {
    return {columns: tabular.columns, rows: tabular.rows, truncated: false, elapsedMs: 0, rowsRead: 0, bytesRead: 0};
}

function OutputTabs({tabs, focus}: { tabs: { value: string; label: ReactNode; content: ReactNode }[]; focus?: string }) {
    return (
        <Tabs defaultValue={tabs.find((tab) => tab.value === focus)?.value ?? tabs[0]?.value} h="100%" style={{display: 'flex', flexDirection: 'column'}}>
            <Tabs.List px="sm" style={{flex: 'none'}}>
                {tabs.map((tab) => <Tabs.Tab key={tab.value} value={tab.value}>{tab.label}</Tabs.Tab>)}
            </Tabs.List>
            <Box style={{flex: 1, minHeight: 0}}>
                {tabs.map((tab) => (
                    <Tabs.Panel key={tab.value} value={tab.value} h="100%">
                        <ScrollArea h="100%" p="sm">{tab.content}</ScrollArea>
                    </Tabs.Panel>
                ))}
            </Box>
        </Tabs>
    );
}

function PreviewView({preview, focus}: { preview: PreviewResult; focus?: string }) {
    const failing = preview.rules.filter((rule) => rule.failing > 0).length;
    const gold = preview.gold.map((table) => ({
        value: `gold-${table.name}`,
        label: `gold.${table.name}`,
        content: table.error ? <ErrorState error={table.error}/>
            : table.sample ?
                <Stack gap="snug"><Text size="xs" c="dimmed">{formatNumber(table.rows)} rows</Text><DataGrid
                    result={grid(table.sample)} height={270}/></Stack> : null,
    }));
    if (!preview.silver || !preview.quarantine) return <OutputTabs tabs={gold} focus={focus}/>;
    const quarantine = preview.quarantine;
    return (
        <OutputTabs focus={focus} tabs={[
            {value: 'silver', label: 'Silver', content: <DataGrid result={grid(preview.silver)} height={300}/>},
            {
                value: 'rules',
                label: `Rules${failing ? ` (${failing} failing)` : ''}`,
                content: (
                    <Table>
                        <Table.Thead>
                            <Table.Tr><Table.Th>Rule</Table.Th><Table.Th>On failure</Table.Th><Table.Th ta="right">Failing
                                rows</Table.Th><Table.Th ta="right">Checked rows</Table.Th></Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {preview.rules.map((rule) => (
                                <Table.Tr key={rule.name}>
                                    <Table.Td><Text size="sm">{rule.name}</Text></Table.Td>
                                    <Table.Td><Text size="sm">{actionLabel(rule.action)}</Text></Table.Td>
                                    <NumberCell color={failingColor(rule.action, rule.failing)}>{formatNumber(rule.failing)}</NumberCell>
                                    <NumberCell>{formatNumber(rule.evaluated)}</NumberCell>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                ),
            },
            {
                value: 'quarantine',
                label: `Quarantine (${quarantine.rows.length})`,
                content: quarantine.rows.length ? <DataGrid result={grid(quarantine)} height={300}/> :
                    <Text size="sm" c="dimmed" p="sm">No row would be dropped</Text>,
            },
            ...gold,
            {value: 'source', label: 'Source columns', content: <SourceColumns preview={preview}/>},
        ]}/>
    );
}

function SourceColumns({preview}: { preview: PreviewResult }) {
    const raw = preview.rawColumns.filter((column) => !column.name.startsWith('_'));
    const yaml = raw.map((column) => `    - { name: ${camel(column.name)}, type: string, from: ${column.name} }`).join('\n');
    return (
        <Stack gap="sm">
            <Group justify="space-between">
                <Text size="xs" c="dimmed">{raw.length} columns in
                    the {preview.origin === 'BRONZE' ? 'bronze table' : 'source'}</Text>
                <CopyButton value={yaml}>
                    {({copied, copy}) => (
                        <Action size="sm" icon={copied ? IconCheck : IconCopy} label={copied ? 'Copied' : 'Copy as silver columns'}
                                onClick={copy}/>
                    )}
                </CopyButton>
            </Group>
            <Table>
                <Table.Thead><Table.Tr><Table.Th>Name</Table.Th><Table.Th>Type</Table.Th></Table.Tr></Table.Thead>
                <Table.Tbody>
                    {raw.map((column) => (
                        <Table.Tr key={column.name}>
                            <Table.Td><Text size="sm">{column.name}</Text></Table.Td>
                            <Table.Td><Text size="sm" c="dimmed">{column.type}</Text></Table.Td>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Stack>
    );
}

function camel(name: string) {
    return name.replace(/[^A-Za-z0-9]+(.)/g, (_, next: string) => next.toUpperCase()).replace(/^[^A-Za-z]+/, '').replace(/^./, (first) => first.toLowerCase());
}

function ImpactView({impact}: { impact: ImpactResult }) {
    const tables = {value: 'gold', label: 'Gold', content: <GoldImpact impact={impact}/>};
    if (!impact.origin) return <OutputTabs tabs={[tables]}/>;
    const changed = impact.columns.filter((column) => column.state !== 'KEPT' || column.changedRows > 0);
    return (
        <OutputTabs tabs={[
            {value: 'summary', label: 'Summary', content: <ImpactSummary impact={impact}/>},
            {
                value: 'columns',
                label: `Columns (${changed.length})`,
                content: (
                    <Table>
                        <Table.Thead><Table.Tr><Table.Th>Column</Table.Th><Table.Th>Change</Table.Th><Table.Th
                            ta="right">Rows with a new value</Table.Th></Table.Tr></Table.Thead>
                        <Table.Tbody>
                            {impact.columns.map((column) => (
                                <Table.Tr key={column.name}>
                                    <Table.Td><Text size="sm">{column.name}</Text></Table.Td>
                                    <Table.Td><Text size="sm"
                                                    c={column.state === 'KEPT' && !column.changedRows ? 'dimmed' : undefined}>{columnState(column)}</Text></Table.Td>
                                    <NumberCell>{column.state === 'KEPT' ? formatNumber(column.changedRows) : '—'}</NumberCell>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                ),
            },
            {
                value: 'rules',
                label: 'Rules',
                content: (
                    <Table>
                        <Table.Thead><Table.Tr><Table.Th>Rule</Table.Th><Table.Th>On failure</Table.Th><Table.Th
                            ta="right">Failing now</Table.Th><Table.Th ta="right">Failing with the
                            draft</Table.Th></Table.Tr></Table.Thead>
                        <Table.Tbody>
                            {impact.rules.map((rule) => (
                                <Table.Tr key={rule.name}>
                                    <Table.Td><Text size="sm">{rule.name}</Text></Table.Td>
                                    <Table.Td><Text
                                        size="sm">{rule.actionAfter ? actionLabel(rule.actionAfter) : 'Removed'}</Text></Table.Td>
                                    <NumberCell>{formatNumber(rule.failingBefore)}</NumberCell>
                                    <NumberCell color={rule.actionAfter ? failingColor(rule.actionAfter, rule.failingAfter ?? 0) : undefined}>
                                        {formatNumber(rule.failingAfter)}
                                    </NumberCell>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                ),
            },
            tables,
        ]}/>
    );
}

function GoldImpact({impact}: { impact: ImpactResult }) {
    return (
        <Table>
            <Table.Thead><Table.Tr><Table.Th>Table</Table.Th><Table.Th ta="right">Rows
                now</Table.Th><Table.Th ta="right">Rows with the draft</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
                {impact.gold.map((gold) => (
                    <Table.Tr key={gold.name}>
                        <Table.Td><Text size="sm">gold.{gold.name}</Text>{gold.error &&
                            <Text size="xs" c={COLOR.danger}>{gold.error}</Text>}</Table.Td>
                        <NumberCell>{formatNumber(gold.rowsBefore)}</NumberCell>
                        <NumberCell>{formatNumber(gold.rowsAfter)}</NumberCell>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}

function columnState(column: ImpactResult['columns'][number]) {
    if (column.state === 'ADDED') return 'Added';
    if (column.state === 'REMOVED') return 'Removed';
    return column.changedRows ? 'Values change' : 'Unchanged';
}

function ImpactSummary({impact}: { impact: ImpactResult }) {
    const keys = impact.keys;
    const rows: [string, ReactNode][] = [
        ['Rows sampled', formatNumber(impact.rows)],
        ['Facts', keys.changed ? `${formatNumber(keys.factsBefore)} → ${formatNumber(keys.factsAfter)}` : formatNumber(keys.factsAfter)],
        ['Facts merged by the new key', keys.changed ? formatNumber(keys.merged) : '—'],
        ['Facts split by the new key', keys.changed ? formatNumber(keys.split) : '—'],
        ['Facts that get a new version', formatNumber(impact.changedFacts)],
        ['Columns with new values', formatNumber(impact.columns.filter((column) => column.state === 'KEPT' && column.changedRows > 0).length)],
    ];
    return (
        <Table maw={560}>
            <Table.Tbody>
                {rows.map(([label, value]) => (
                    <Table.Tr key={label}>
                        <Table.Td><Text size="sm" c="dimmed">{label}</Text></Table.Td>
                        <NumberCell>{value}</NumberCell>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}
