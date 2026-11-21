import {Anchor, Box, SegmentedControl, Stack, Tabs, Text} from '@mantine/core';
import {IconChartBar, IconRoute} from '../icons';
import {lazy, Suspense, useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {RUN_LIMIT, useDataset, useFlowCells, useGraph, useLineage, useQuality, useRuns, useRuntime, useSpec} from '../api/hooks';
import {Empty, ErrorState, Loading} from '../components/ErrorState';
import {FlowGraph} from '../components/FlowGraph';
import {LineageGraph} from '../components/LineageGraph';
import {Notice} from '../components/Notice';
import {Page} from '../components/Page';
import {Meta, PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {QualityRules} from '../components/QualityRules';
import {RunButton} from '../components/RunButton';
import {RunDurations} from '../components/RunDurations';
import {MATRIX_METRICS, type MatrixMetric, RunMatrix} from '../components/RunMatrix';
import {RunsTable} from '../components/RunsTable';
import {PipelineMenu} from '../components/PipelineMenu';
import {ScheduleMeta} from '../components/Schedule';
import {StatusBadge} from '../components/StatusBadge';
import {TableSkeleton} from '../components/TableSkeleton';
import {links, tableOfNode, PAGE} from '../lib/links';
import {useRemember} from '../lib/recents';
import {PartitionsTab} from './pipeline/PartitionsTab';
import {WhyTab} from './pipeline/WhyTab';
import {EditSpecButton} from '../components/EditSpecButton';
import {FavoriteButton} from '../components/FavoriteButton';
import {RuntimeMeta} from '../components/RuntimeMeta';
import {IconAction} from '../components/actions/IconAction';
import {useRemainingHeight} from '../components/layout/useRemainingHeight';
import {ICON_HERO} from '../lib/icons';

const CodeEditor = lazy(() => import('../components/CodeEditor').then((module) => ({default: module.CodeEditor})));

export function PipelinePage() {
    const {dataset = '', tab = 'graph'} = useParams();
    const navigate = useNavigate();
    const {data: summary, error, isLoading} = useDataset(dataset);
    const {data: runtime} = useRuntime(dataset);
    useRemember(summary && {to: links.pipeline(dataset), label: dataset, kind: 'pipeline'});

    const heading = {crumbs: [{label: 'Pipelines', to: PAGE.pipelines}, {label: dataset}], icon: <IconRoute {...ICON_HERO}/>, title: dataset};

    if (isLoading) return <Loading/>;
    if (error || !summary) return <Page wide><PageHeader {...heading}/><ErrorState error={error ?? 'Pipeline not found'}/></Page>;

    return (
        <Page wide>
            <PageHeader
                {...heading}
                actions={
                    <>
                        <FavoriteButton kind="PIPELINE" reference={dataset} size={32}/>
                        <IconAction label="Dashboard" icon={IconChartBar} outlined size={32} to={links.board(dataset)}/>
                        <EditSpecButton dataset={dataset}/>
                        <RunButton dataset={summary}/>
                        {(summary.schedule || summary.source) && <PipelineMenu summary={summary}/>}
                    </>
                }
                meta={
                    <>
                        <Meta label="Last run">
                            {summary.lastRun ? (
                                <Anchor component={Link} to={links.run(summary.lastRun.runId)} underline="never" c="inherit">
                                    <StatusBadge status={summary.lastRun.status}/>
                                </Anchor>
                            ) : <StatusBadge/>}
                        </Meta>
                        <Meta label="Schedule"><ScheduleMeta dataset={dataset} cron={summary.schedule}
                                                            onInputs={(summary.counts.inputs ?? 0) > 0}/></Meta>
                        {summary.partitions && <Meta label="Partitions">{summary.partitions}</Meta>}
                        <Meta label="Owner">{summary.owner ?? '—'}</Meta>
                        <RuntimeMeta runtime={runtime}/>
                    </>
                }
            />
            {!summary.valid && (
                <Notice mb="lg" title="Invalid spec">
                    {summary.problems.map((problem) => (
                        <Text key={problem.message}
                              size="sm">{problem.line ? `Line ${problem.line}: ` : ''}{problem.message}</Text>
                    ))}
                </Notice>
            )}
            <Tabs value={tab}
                  onChange={(value) => navigate(links.pipeline(dataset, value === 'graph' ? undefined : value ?? undefined), {replace: true})}>
                <Tabs.List mb="lg">
                    <Tabs.Tab value="graph">Graph</Tabs.Tab>
                    <Tabs.Tab value="runs">Runs</Tabs.Tab>
                    {summary.partitions && <Tabs.Tab value="partitions">Partitions</Tabs.Tab>}
                    <Tabs.Tab value="quality">Quality</Tabs.Tab>
                    <Tabs.Tab value="lineage">Lineage</Tabs.Tab>
                    <Tabs.Tab value="spec">Spec</Tabs.Tab>
                    <Tabs.Tab value="trace">Trace</Tabs.Tab>
                </Tabs.List>
                <Tabs.Panel value="graph">{summary.valid && <GraphTab dataset={dataset}/>}</Tabs.Panel>
                <Tabs.Panel value="runs"><RunsTab dataset={dataset}/></Tabs.Panel>
                {summary.partitions && <Tabs.Panel value="partitions"><PartitionsTab dataset={dataset}/></Tabs.Panel>}
                <Tabs.Panel value="quality"><QualityTab dataset={dataset}/></Tabs.Panel>
                <Tabs.Panel value="lineage">{summary.valid && <LineageTab dataset={dataset}/>}</Tabs.Panel>
                <Tabs.Panel value="spec"><SpecTab dataset={dataset}/></Tabs.Panel>
                <Tabs.Panel value="trace">{summary.valid &&
                    <WhyTab dataset={dataset} keys={summary.keys}/>}</Tabs.Panel>
            </Tabs>
        </Page>
    );
}

function GraphTab({dataset}: { dataset: string }) {
    const navigate = useNavigate();
    const [fill, height] = useRemainingHeight();
    const {data, error, isLoading} = useGraph(dataset);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data) return null;
    return (
        <div ref={fill}>
            <Panel flush framed>
                <FlowGraph nodes={data.nodes} edges={data.edges} height={height}
                           onSelect={(id) => navigate(tableOfNode(dataset, id))}/>
            </Panel>
        </div>
    );
}

const MATRIX_RUNS = RUN_LIMIT.pipeline;

function RunsTab({dataset}: { dataset: string }) {
    const {data, error, isLoading} = useRuns(dataset);
    const {data: cells} = useFlowCells(dataset, MATRIX_RUNS);
    const [metric, setMetric] = useState<MatrixMetric>('duration');
    if (isLoading) return <TableSkeleton/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.length) return <Empty title="No runs"/>;
    return (
        <Stack gap="md">
            <Panel title="Durations"><RunDurations runs={data}/></Panel>
            {!!cells?.length && (
                <Panel title="Flows" actions={
                    <SegmentedControl size="xs" value={metric} data={MATRIX_METRICS}
                                      onChange={(value) => setMetric(value as MatrixMetric)}/>
                }>
                    <RunMatrix cells={cells} metric={metric}/>
                </Panel>
            )}
            <Panel flush><RunsTable runs={data} showDataset={false}/></Panel>
        </Stack>
    );
}

function QualityTab({dataset}: { dataset: string }) {
    const {data, error, isLoading} = useQuality(dataset);
    if (isLoading) return <TableSkeleton rows={4}/>;
    if (error) return <ErrorState error={error}/>;
    if (!data?.length) return <Empty title="No rules"/>;
    return <Panel flush><QualityRules rules={data}/></Panel>;
}

function LineageTab({dataset}: { dataset: string }) {
    const [fill, height] = useRemainingHeight();
    const {data, error, isLoading} = useLineage(dataset);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    if (!data) return null;
    return (
        <Stack>
            {data.warnings.length > 0 && (
                <Notice tone="warning">
                    {data.warnings.map((warning) => <Text key={warning} size="sm">{warning}</Text>)}
                </Notice>
            )}
            <div ref={fill}><Panel flush framed><LineageGraph lineage={data} height={height}/></Panel></div>
        </Stack>
    );
}

function SpecTab({dataset}: { dataset: string }) {
    const [fill, height] = useRemainingHeight();
    const {data, error, isLoading} = useSpec(dataset);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    return (
        <Panel
            title={`pipelines/${dataset}/pipeline.yml`}
            actions={<EditSpecButton dataset={dataset} size="sm"/>}
            flush
            framed
        >
            <Box h={height} ref={fill}>
                <Suspense fallback={<Loading/>}>
                    <CodeEditor value={data?.content ?? ''} language="yaml" readOnly problems={data?.problems}/>
                </Suspense>
            </Box>
        </Panel>
    );
}
