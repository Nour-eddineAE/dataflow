import {Anchor, Grid, Stack, Table, Tabs, Text} from '@mantine/core';
import {Link, useSearchParams} from 'react-router-dom';
import {useCatalog, useServedColumns, useServedProfile, useServedSample} from '../../api/hooks';
import type {ServedTable} from '../../api/types';
import {DataGrid} from '../../components/DataGrid';
import {Empty, ErrorState, Loading} from '../../components/ErrorState';
import {Fact} from '../../components/Fact';
import {ProfileTable} from '../../components/ProfileTable';
import {Panel} from '../../components/Panel';
import {layer} from '../../lib/layers';
import {links, PAGE} from '../../lib/links';
import {useRemember} from '../../lib/recents';
import {shortType} from '../../lib/types';

import {TableHeader} from './TableHeader';
import {ICON_PAGE} from '../../lib/icons';

export function ServedTableView({table}: { table: string }) {
    const [params, setParams] = useSearchParams();
    const tab = params.get('tab') ?? 'overview';
    const {data: catalog, error, isLoading} = useCatalog();
    const served = catalog?.serving.find((candidate) => candidate.table === table);
    useRemember(served && {to: links.served(table), label: served.sqlName, kind: 'served'});

    const crumbs = [{label: 'Catalog', to: PAGE.catalog}, {label: 'serving'}, {label: table}];
    const favorite = {kind: 'SERVED', reference: table} as const;

    if (isLoading) return <Loading/>;
    if (error || !served) {
        const Unknown = layer('serving').icon;
        return (
            <>
                <TableHeader crumbs={crumbs} icon={<Unknown {...ICON_PAGE}/>} name={`serving.${table}`} exists={false} favorite={favorite}/>
                {error ? <ErrorState error={error}/> : <Empty title="Table not found"/>}
            </>
        );
    }
    const Icon = served.mode === 'view' ? layer('current').icon : layer('serving').icon;

    return (
        <>
            <TableHeader crumbs={crumbs} icon={<Icon {...ICON_PAGE}/>} name={served.sqlName} sqlName={served.sqlName} exists={served.exists}
                         favorite={favorite}/>
            {!served.exists ? <Empty title="Not created yet"/> : (
                <Tabs value={tab} onChange={(next) => setParams(next && next !== 'overview' ? {tab: next} : {}, {replace: true})}
                      keepMounted={false}>
                    <Tabs.List mb="lg">
                        <Tabs.Tab value="overview">Overview</Tabs.Tab>
                        <Tabs.Tab value="sample">Sample data</Tabs.Tab>
                        <Tabs.Tab value="profile">Profile</Tabs.Tab>
                    </Tabs.List>
                    <Tabs.Panel value="overview"><Overview served={served}/></Tabs.Panel>
                    <Tabs.Panel value="sample"><ServedSample table={table}/></Tabs.Panel>
                    <Tabs.Panel value="profile"><ServedProfile table={table}/></Tabs.Panel>
                </Tabs>
            )}
        </>
    );
}

function Overview({served}: { served: ServedTable }) {
    const {data, error, isLoading} = useServedColumns(served.table);
    const source = served.from === 'current' ? 'current' : served.from;
    return (
        <Grid gap="lg">
            <Grid.Col span={{base: 12, lg: 8}}>
                <Panel title="Columns" flush>
                    {isLoading ? <Loading/> : error ? <ErrorState error={error}/> : (
                        <Table>
                            <Table.Thead>
                                <Table.Tr>
                                    <Table.Th>Name</Table.Th>
                                    <Table.Th>Type</Table.Th>
                                </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                                {(data?.rows ?? []).map((row) => (
                                    <Table.Tr key={String(row[0])}>
                                        <Table.Td><Text size="sm">{String(row[0])}</Text></Table.Td>
                                        <Table.Td><Text size="sm"
                                                        c="dimmed">{shortType(String(row[1]))}</Text></Table.Td>
                                    </Table.Tr>
                                ))}
                            </Table.Tbody>
                        </Table>
                    )}
                </Panel>
            </Grid.Col>
            <Grid.Col span={{base: 12, lg: 4}}>
                <Panel title="About">
                    <Stack gap="sm">
                        <Fact label="Pipeline"><Anchor component={Link} to={links.pipeline(served.dataset)} size="sm"
                                                       className="df-link">{served.dataset}</Anchor></Fact>
                        <Fact label="Source"><Anchor component={Link} to={links.table(served.dataset, source)} size="sm"
                                                     className="df-link">{served.dataset}.{source}</Anchor></Fact>
                        <Fact
                            label="Type">{served.mode === 'copy' ? 'Table, refreshed each run' : 'View over Delta'}</Fact>
                        {served.orderBy && <Fact label="Order by"><Text size="sm" className="df-mono"
                                                                        fz="xs">{served.orderBy}</Text></Fact>}
                    </Stack>
                </Panel>
            </Grid.Col>
        </Grid>
    );
}

function ServedSample({table}: { table: string }) {
    const {data, error, isLoading} = useServedSample(table);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    return data ? <DataGrid result={data} height="calc(100vh - 290px)"/> : null;
}

function ServedProfile({table}: { table: string }) {
    const {data, error, isLoading} = useServedProfile(table);
    if (isLoading) return <Loading/>;
    if (error) return <ErrorState error={error}/>;
    return data ? <ProfileTable profile={data} sqlName={`serving.${table}`}/> : null;
}
