import {Group, Select, Tabs, TextInput} from '@mantine/core';
import {IconPlus, IconSearch, IconUsersGroup} from '../icons';
import {useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useDatasets, useMe} from '../api/hooks';
import {Empty, ErrorState} from '../components/ErrorState';
import {TableSkeleton} from '../components/TableSkeleton';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {PipelinesTable} from '../components/PipelinesTable';
import {ReleaseList} from '../components/ReleaseList';
import {ICON} from '../lib/icons';
import {Action} from '../components/actions/Action';
import {PAGE} from '../lib/links';

const TEAM = 'team';
const MINE = 'mine';

export function PipelinesPage() {
    const navigate = useNavigate();
    const {data, error, isLoading} = useDatasets();
    const [filter, setFilter] = useState('');
    const [params, setParams] = useSearchParams();
    const {data: me} = useMe();
    const tab = params.get('tab') === 'releases' ? 'releases' : 'pipelines';
    const team = params.get(TEAM);
    const teams = [...new Set((data ?? []).map((dataset) => dataset.owner).filter((owner): owner is string => !!owner))].sort();
    const owned = (owner?: string | null) => !team || (team === MINE ? !!owner && !!me?.groups.includes(owner) : owner === team);
    const shown = (data ?? []).filter((dataset) => dataset.dataset.includes(filter.trim().toLowerCase()) && owned(dataset.owner));
    const chooseTeam = (value: string | null) => setParams((current) => {
        const next = new URLSearchParams(current);
        if (value) next.set(TEAM, value);
        else next.delete(TEAM);
        return next;
    }, {replace: true});

    return (
        <Page>
            <PageHeader title="Pipelines" actions={<Action tone="primary" icon={IconPlus} label="New pipeline"
                                                           onClick={() => navigate(PAGE.newPipeline)}/>}/>
            <Tabs value={tab} onChange={(value) => setParams(value === 'releases' ? {tab: 'releases'} : {})} mb="md">
                <Tabs.List>
                    <Tabs.Tab value="pipelines">Pipelines</Tabs.Tab>
                    <Tabs.Tab value="releases">Releases</Tabs.Tab>
                </Tabs.List>
            </Tabs>
            {tab === 'releases' ? <ReleaseList/> : (
                <>
                    <Group mb="md">
                        <TextInput placeholder="Filter" leftSection={<IconSearch {...ICON}/>} value={filter}
                                   onChange={(event) => setFilter(event.currentTarget.value)} w={280} aria-label="Filter pipelines"/>
                        <Select placeholder="All teams" leftSection={<IconUsersGroup {...ICON}/>} value={team} onChange={chooseTeam}
                                clearable w={200} aria-label="Team"
                                data={[{value: MINE, label: 'My teams'}, ...teams.map((owner) => ({value: owner, label: owner}))]}/>
                    </Group>
                    {isLoading ? <TableSkeleton/> : error ? <ErrorState error={error}/>
                        : !shown.length ? <Empty title={data?.length ? 'No pipeline matches' : 'No pipelines'}/>
                            : <Panel flush><PipelinesTable datasets={shown}/></Panel>}
                </>
            )}
        </Page>
    );
}
