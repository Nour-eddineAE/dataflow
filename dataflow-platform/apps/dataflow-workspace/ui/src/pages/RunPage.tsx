import {Anchor, Box, Group, ScrollArea, SegmentedControl, Table, Tabs, Text, Tooltip} from '@mantine/core';
import {IconActivity, IconChartBar, IconExternalLink, IconPlayerStop, IconRefresh} from '../icons';
import {type ReactNode, useMemo, useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {
    isRunning,
    useGraph,
    useLaunch,
    useRun,
    useRunComparison,
    useRunOutput,
    useStatus,
    useTerminate
} from '../api/hooks';
import type {FlowRun, GraphNode, PipelineGraph, RunDetail} from '../api/types';
import {CopyValue} from '../components/CopyValue';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {FlowGraph} from '../components/FlowGraph';
import {Notice} from '../components/Notice';
import {OutputLog} from '../components/OutputLog';
import {Page} from '../components/Page';
import {Meta, PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {actionLabel, failingColor} from '../components/QualityRules';
import {triggerLabel} from '../components/RunsTable';
import {StatusBadge, StatusIcon} from '../components/StatusBadge';
import {formatClock, formatDuration, formatNumber, formatTime, parseTime, shortRunId} from '../lib/format';
import {rowChange} from '../lib/rowChange';
import {links, tableOfNode, PAGE} from '../lib/links';
import {useRemember} from '../lib/recents';
import {statusTone, toneColor} from '../lib/status';
import {ICON_HERO, ICON_SMALL} from '../lib/icons';
import {Action} from '../components/actions/Action';
import {IconAction} from '../components/actions/IconAction';
import {RuntimeMeta} from '../components/RuntimeMeta';
import {NumberCell} from '../components/NumberCell';
import {COLOR, HAIRLINE} from '../lib/colors';
import {notifyFailure} from '../components/notify';
import {useRemainingHeight} from '../components/layout/useRemainingHeight';
import {runFailure} from '../lib/runFailure';

const SHORTEST_BAR = 0.5;

const TASKS: Record<string, string> = {preview: 'Preview', impact: 'Data impact'};

export function RunPage() {
    const {runId = ''} = useParams();
    const {data, error, isLoading} = useRun(runId);
    useRemember(data && {
        to: links.run(runId),
        label: `${data.run.dataset ?? data.run.job} ${shortRunId(runId)}`,
        kind: 'run'
    });
    const terminate = useTerminate();
    const launch = useLaunch();
    const navigate = useNavigate();
    const {data: status} = useStatus();

    const crumbs = [{label: 'Runs', to: PAGE.runs}, {label: shortRunId(runId)}];
    const icon = <IconActivity {...ICON_HERO}/>;

    if (isLoading) return <Loading/>;
    if (error || !data) return <Page wide><PageHeader crumbs={crumbs} icon={icon} title={`Run ${shortRunId(runId)}`}/><ErrorState error={error ?? 'Run not found'}/></Page>;
    const run = data.run;
    const running = isRunning(run.status);

    const again = () => launch.mutate({dataset: run.dataset!, partition: run.partition}, {
        onSuccess: (started) => navigate(links.run(started.runId)),
        onError: notifyFailure('Run not started'),
    });

    const stop = () => terminate.mutate(runId, {
        onError: notifyFailure('Not terminated'),
    });

    return (
        <Page wide>
            <PageHeader
                crumbs={crumbs}
                icon={icon}
                title={(
                    <Group gap="tight" wrap="nowrap">
                        <span>{`${run.task ? `${TASKS[run.task] ?? run.task} of ` : ''}${run.dataset ?? run.job} · ${shortRunId(run.runId)}`}</span>
                        <CopyValue value={run.runId} label="Copy run ID"/>
                    </Group>
                )}
                meta={(
                    <>
                        <Meta label="Status"><StatusBadge status={run.status}/></Meta>
                        {run.dataset &&
                            <Meta label="Pipeline"><Anchor component={Link} to={links.pipeline(run.dataset)} size="sm"
                                                           className="df-link">{run.dataset}</Anchor></Meta>}
                        <Meta label="Trigger">{run.task ? 'Editor' : triggerLabel(run.trigger)}</Meta>
                        {run.startedBy && <Meta label="Started by">{run.startedBy}</Meta>}
                        {run.partition && <Meta label="Partition">{run.partition}</Meta>}
                        <Meta label="Started">{formatTime(run.startedAt ?? run.createdAt)}</Meta>
                        <Meta label="Duration">{formatDuration(run.durationMs)}</Meta>
                        <RuntimeMeta runtime={data.runtime}/>
                    </>
                )}
                actions={
                    <>
                        {running && (
                            <Action danger icon={IconPlayerStop} label="Cancel run" loading={terminate.isPending} onClick={stop}/>
                        )}
                        {!running && !run.task && run.dataset && (
                            <Action icon={IconRefresh} label="Run again" loading={launch.isPending} onClick={again}/>
                        )}
                        {status?.links.tower && (
                            <IconAction label="Open in tower" icon={IconExternalLink} outlined size={32}
                                        href={`${status.links.tower}/runs/${run.runId}`} external/>
                        )}
                        {status?.links.sparkHistory && data.sparkApp && (
                            <IconAction label="Open in Spark UI" icon={IconChartBar} outlined size={32}
                                        href={`${status.links.sparkHistory}/history/${data.sparkApp}/`} external/>
                        )}
                    </>
                }
            />
            {data.error && <FailureNotice detail={data} error={data.error}/>}
            <Tabs defaultValue={run.task ? 'log' : 'graph'} keepMounted={false}>
                <Tabs.List mb="lg">
                    {!run.task && <Tabs.Tab value="graph">Graph</Tabs.Tab>}
                    {!run.task && <Tabs.Tab value="timeline">Timeline</Tabs.Tab>}
                    {!run.task && <Tabs.Tab value="quality">Quality</Tabs.Tab>}
                    <Tabs.Tab value="log">Log</Tabs.Tab>
                </Tabs.List>
                <Tabs.Panel value="graph">{run.dataset ? <RunGraph dataset={run.dataset} detail={data}/> :
                    <Empty title="No graph"/>}</Tabs.Panel>
                <Tabs.Panel value="timeline">{data.flows.length ? <Timeline detail={data}/> :
                    <Empty title="No flows yet"/>}</Tabs.Panel>
                <Tabs.Panel value="quality">{data.expectations.length ? <Expectations detail={data}/> :
                    <Empty title="No rules checked"/>}</Tabs.Panel>
                <Tabs.Panel value="log"><Log detail={data}/></Tabs.Panel>
            </Tabs>
        </Page>
    );
}

function RunGraph({dataset, detail}: { dataset: string; detail: RunDetail }) {
    const navigate = useNavigate();
    const {data: graph, isLoading} = useGraph(dataset);
    const nodes = useMemo(() => (graph ? withRun(graph, detail.flows, isRunning(detail.run.status)) : undefined), [graph, detail]);
    const [fill, height] = useRemainingHeight();
    if (isLoading) return <Loading/>;
    if (!graph || !nodes) return <Empty title="No graph"/>;
    return (
        <div ref={fill}>
            <Panel flush framed>
                <FlowGraph nodes={nodes} edges={graph.edges} height={height}
                           onSelect={(id) => navigate(tableOfNode(dataset, id))}/>
            </Panel>
        </div>
    );
}

function withRun(graph: PipelineGraph, flows: FlowRun[], running: boolean): GraphNode[] {
    return graph.nodes.map((node) => {
        const flow = flows.find((candidate) => candidate.flow === node.flow);
        if (!flow) return {...node, status: running ? 'QUEUED' : undefined, rows: undefined, durationMs: undefined};
        const rows = node.layer === 'quarantine' ? flow.rowsDropped : node.layer === 'serving' ? undefined : flow.rowsOut;
        return {...node, status: flow.status, rows, durationMs: flow.durationMs};
    });
}

function Timeline({detail}: { detail: RunDetail }) {
    const flows = detail.flows;
    const {data: comparison} = useRunComparison(detail.run.runId, isRunning(detail.run.status) ? undefined : detail.run.dataset ?? undefined);
    const before = new Map((comparison?.previousFlows ?? []).map((flow) => [flow.flow, flow]));
    const times = flows.flatMap((flow) => [parseTime(flow.startedAt)?.getTime(), parseTime(flow.endedAt)?.getTime()])
        .filter((time): time is number => !!time);
    const start = Math.min(...times);
    const span = Math.max(1, Math.max(...times) - start);
    return (
        <Panel flush>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th w={200}>Flow</Table.Th>
                        <Table.Th/>
                        <Table.Th w={110} ta="right">Rows out</Table.Th>
                        <Table.Th w={120} ta="right">
                            {comparison?.previousRunId ? (
                                <Tooltip label={`Run ${shortRunId(comparison.previousRunId)}`}><span>vs previous</span></Tooltip>
                            ) : 'vs previous'}
                        </Table.Th>
                        <Table.Th w={90} ta="right">Duration</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {flows.map((flow) => {
                        const from = (parseTime(flow.startedAt)?.getTime() ?? start) - start;
                        const width = Math.max(SHORTEST_BAR, ((flow.durationMs ?? 0) / span) * 100);
                        const tone = statusTone(flow.status).tone;
                        return (
                            <Table.Tr key={flow.flow}>
                                <Table.Td>
                                    <Group gap="cozy" wrap="nowrap"><StatusIcon status={flow.status} {...ICON_SMALL}/><Text
                                        size="sm">{flow.flow}</Text></Group>
                                </Table.Td>
                                <Table.Td>
                                    <Box pos="relative" h={8}>
                                        <Box pos="absolute" top={0} bottom={0} style={{
                                            left: `${(from / span) * 100}%`, width: `${width}%`, borderRadius: 2,
                                            background: tone === 'success' ? COLOR.primary : toneColor[tone]
                                        }}/>
                                    </Box>
                                </Table.Td>
                                <NumberCell>{formatNumber(flow.rowsOut)}</NumberCell>
                                <Table.Td ta="right"><Change now={flow.rowsOut}
                                                             before={before.get(flow.flow)?.rowsOut}/></Table.Td>
                                <NumberCell>{formatDuration(flow.durationMs)}</NumberCell>
                            </Table.Tr>
                        );
                    })}
                </Table.Tbody>
            </Table>
        </Panel>
    );
}

function Change({now, before}: { now?: number | null; before?: number | null }) {
    if (now === undefined || now === null || before === undefined || before === null) return <Text size="sm"
                                                                                                   c="dimmed">—</Text>;
    const change = rowChange(now, before);
    return (
        <Tooltip label={`${formatNumber(before)} rows before`}>
            <Text size="sm" className="df-num"
                  c={change.alarming ? COLOR.warning : 'dimmed'}>{change.label}</Text>
        </Tooltip>
    );
}

function Expectations({detail}: { detail: RunDetail }) {
    return (
        <Panel flush>
            <Table>
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th>Rule</Table.Th>
                        <Table.Th>On failure</Table.Th>
                        <Table.Th ta="right">Failing rows</Table.Th>
                        <Table.Th ta="right">Checked rows</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {detail.expectations.map((rule) => (
                        <Table.Tr key={rule.name}>
                            <Table.Td>
                                <Text size="sm">{rule.name}</Text>
                                <Text size="xs" c="dimmed" className="df-mono">{rule.check}</Text>
                            </Table.Td>
                            <Table.Td><Text size="sm">{actionLabel(rule.action)}</Text></Table.Td>
                            <NumberCell color={failingColor(rule.action, rule.failing)}>{formatNumber(rule.failing ?? 0)}</NumberCell>
                            <NumberCell>{formatNumber(rule.evaluated ?? 0)}</NumberCell>
                        </Table.Tr>
                    ))}
                </Table.Tbody>
            </Table>
        </Panel>
    );
}

function FailureNotice({detail, error}: { detail: RunDetail; error: string }) {
    const failure = runFailure(detail.flows, error);
    return (
        <Notice mb="lg" title={failure.title}>
            {failure.exception && <Text size="sm" className="df-mono">{failure.exception}</Text>}
            <details className="df-details">
                <summary>Show details</summary>
                <Text size="sm" className="df-mono" fz="xs" mt="snug"
                      style={{whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>{error}</Text>
            </details>
        </Notice>
    );
}

const LOG = {engine: 'engine', tower: 'tower'} as const;

function Log({detail}: { detail: RunDetail }) {
    const [source, setSource] = useState(() => (detail.error && runFailure(detail.flows, detail.error).inTower ? LOG.tower : LOG.engine));
    const running = isRunning(detail.run.status);
    const output = useRunOutput(detail.run.runId, running);
    const toolbar = (
        <SegmentedControl size="xs" value={source} onChange={setSource}
                          data={[{value: LOG.engine, label: 'Engine'}, {value: LOG.tower, label: 'Tower'}]}/>
    );
    if (source === LOG.tower) return <TowerEvents detail={detail} toolbar={toolbar}/>;
    if (output.isLoading) return <Loading/>;
    if (output.error) return <ErrorState error={output.error}/>;
    return (
        <>
            {output.data?.truncated && <Text size="xs" c="dimmed" mb="snug">Showing the first 4 MB</Text>}
            <OutputLog lines={output.data?.lines ?? []} name={`run_${shortRunId(detail.run.runId)}`} follow={running}
                       toolbar={toolbar}/>
        </>
    );
}

function TowerEvents({detail, toolbar}: { detail: RunDetail; toolbar: ReactNode }) {
    return (
        <Panel flush>
            <Group px="sm" h={44} style={{borderBottom: HAIRLINE}}>{toolbar}</Group>
            {!detail.log.length ? <Empty title="No events"/> : (
                <ScrollArea.Autosize mah={540}>
                    <Table>
                        <Table.Tbody>
                            {detail.log.map((line, position) => (
                                <Table.Tr key={position}>
                                    <Table.Td w={110}><Text size="xs" c="dimmed"
                                                            className="df-num">{formatClock(Number(line.timestamp))}</Text></Table.Td>
                                    <Table.Td w={150}><Text size="xs"
                                                            c={line.level === 'ERROR' ? COLOR.danger : 'dimmed'}>{line.type.replace(/([a-z])([A-Z])/g, '$1 $2')}</Text></Table.Td>
                                    <Table.Td><Text size="xs" className="df-mono" style={{
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-word'
                                    }}>{line.message}</Text></Table.Td>
                                </Table.Tr>
                            ))}
                        </Table.Tbody>
                    </Table>
                </ScrollArea.Autosize>
            )}
        </Panel>
    );
}
