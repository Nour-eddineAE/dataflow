import {Group, SegmentedControl, Select} from '@mantine/core';
import {useSearchParams} from 'react-router-dom';
import {isRunning, useDatasets, useFlowCellsOf, useRuns, RUN_LIMIT} from '../api/hooks';
import {failurePlaces} from '../lib/failurePlace';
import {Empty, ErrorState} from '../components/ErrorState';
import {TableSkeleton} from '../components/TableSkeleton';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {RunsTable} from '../components/RunsTable';

const FILTERS = [
    {value: 'all', label: 'All'},
    {value: 'running', label: 'Active'},
    {value: 'failed', label: 'Failed'},
    {value: 'succeeded', label: 'Succeeded'},
];

export function RunsPage() {
    const [params, setParams] = useSearchParams();
    const dataset = params.get('pipeline');
    const status = params.get('status') ?? 'all';
    const {data: datasets} = useDatasets();
    const {data, error, isLoading} = useRuns(dataset ?? undefined, RUN_LIMIT.all);

    const update = (key: string, value: string | null) => {
        const next = new URLSearchParams(params);
        if (value && value !== 'all') next.set(key, value);
        else next.delete(key);
        setParams(next, {replace: true});
    };

    const shown = (data ?? []).filter((run) => {
        if (status === 'running') return isRunning(run.status);
        if (status === 'failed') return run.status === 'FAILURE';
        if (status === 'succeeded') return run.status === 'SUCCESS';
        return true;
    });
    const failedOnly = status === 'failed';
    const pipelines = failedOnly ? [...new Set(shown.flatMap((run) => (run.dataset ? [run.dataset] : [])))] : [];
    const {cells, loading} = useFlowCellsOf(pipelines, RUN_LIMIT.all);
    const failedIn = failedOnly && !loading
        ? failurePlaces(cells, shown.filter((run) => run.dataset).map((run) => run.runId))
        : undefined;

    return (
        <Page>
            <PageHeader title="Runs"/>
            <Group mb="md">
                <Select placeholder="All pipelines" data={(datasets ?? []).map((entry) => entry.dataset)}
                        value={dataset} onChange={(value) => update('pipeline', value)} clearable w={220} aria-label="Pipeline"/>
                <SegmentedControl data={FILTERS} value={status} onChange={(value) => update('status', value)}/>
            </Group>
            {isLoading ? <TableSkeleton/> : error ? <ErrorState error={error}/>
                : !shown.length ? <Empty title="No runs"/> : <Panel flush><RunsTable runs={shown} failedIn={failedIn}/></Panel>}
        </Page>
    );
}
