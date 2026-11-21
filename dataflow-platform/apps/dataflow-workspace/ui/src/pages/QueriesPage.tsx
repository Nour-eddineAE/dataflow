import {Anchor, Table, Tabs, Text, TextInput} from '@mantine/core';
import {IconPlus, IconSearch, IconTrash} from '../icons';
import {useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {useMe, useQueryAlerts, useSavedQueries} from '../api/hooks';
import type {SavedQuery} from '../api/types';
import {QueryAlertButton} from '../components/QueryAlerts';
import {Empty, ErrorState} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {TableSkeleton} from '../components/TableSkeleton';
import {formatAgo} from '../lib/format';
import {ICON_SMALL} from '../lib/icons';
import {Action} from '../components/actions/Action';
import {IconAction} from '../components/actions/IconAction';
import {QueryHistory} from './queries/QueryHistory';
import {FavoriteButton} from '../components/FavoriteButton';
import {PAGE, links} from '../lib/links';
import {DeleteQueryModal} from '../components/DeleteQueryModal';

const HISTORY = 'history';

export function QueriesPage() {
    const {data, error, isLoading} = useSavedQueries();
    const {data: me} = useMe();
    const {data: alerts} = useQueryAlerts();
    const [doomed, setDoomed] = useState<SavedQuery>();
    const [filter, setFilter] = useState('');
    const [params, setParams] = useSearchParams();
    const tab = params.get('tab') === HISTORY ? HISTORY : 'saved';
    const queries = (data ?? []).filter((query) => query.name.toLowerCase().includes(filter.trim().toLowerCase()));
    return (
        <Page>
            <PageHeader title="Queries" actions={
                <Action tone="primary" icon={IconPlus} label="New query" to={PAGE.newQuery}/>
            }/>
            <Tabs value={tab} onChange={(value) => setParams(value === HISTORY ? {tab: HISTORY} : {}, {replace: true})} mb="md">
                <Tabs.List>
                    <Tabs.Tab value="saved">Saved</Tabs.Tab>
                    <Tabs.Tab value={HISTORY}>History</Tabs.Tab>
                </Tabs.List>
            </Tabs>
            <TextInput placeholder="Filter" leftSection={<IconSearch {...ICON_SMALL}/>} value={filter} mb="md" maw={320}
                       aria-label="Filter queries"
                       onChange={(event) => setFilter(event.currentTarget.value)}/>
            {tab === HISTORY ? <QueryHistory filter={filter}/> : <Panel flush>
                {isLoading ? <TableSkeleton rows={3}/> : error ? <ErrorState error={error}/> : !queries.length ?
                    <Empty title={data?.length ? 'No query matches' : 'No saved queries'}/> : (
                        <Table>
                            <Table.Thead>
                                <Table.Tr>
                                    <Table.Th>Name</Table.Th>
                                    <Table.Th>Owner</Table.Th>
                                    <Table.Th>Shared</Table.Th>
                                    <Table.Th>Updated</Table.Th>
                                    <Table.Th w={48}/>
                                    <Table.Th w={48}/>
                                    <Table.Th w={48}/>
                                </Table.Tr>
                            </Table.Thead>
                            <Table.Tbody>
                                {queries.map((query) => (
                                    <Table.Tr key={query.id}>
                                        <Table.Td><Anchor component={Link} to={links.savedQuery(query.id)} size="sm"
                                                          className="df-link">{query.name}</Anchor></Table.Td>
                                        <Table.Td><Text size="sm" c="dimmed">{query.owner}</Text></Table.Td>
                                        <Table.Td><Text size="sm"
                                                        c="dimmed">{query.shared ? 'Everyone' : 'Only owner'}</Text></Table.Td>
                                        <Table.Td><Text size="sm"
                                                        c="dimmed">{formatAgo(query.updatedAt)}</Text></Table.Td>
                                        <Table.Td>
                                            <FavoriteButton kind="QUERY" reference={query.id} size={28}/>
                                        </Table.Td>
                                        <Table.Td>
                                            {me?.id === query.owner && (
                                                <QueryAlertButton queryId={query.id} name={query.name}
                                                                  alert={alerts?.find((alert) => alert.queryId === query.id)}/>
                                            )}
                                        </Table.Td>
                                        <Table.Td>
                                            {(me?.id === query.owner || me?.admin) && (
                                                <IconAction label="Delete query" icon={IconTrash} danger
                                                            onClick={() => setDoomed(query)}/>
                                            )}
                                        </Table.Td>
                                    </Table.Tr>
                                ))}
                            </Table.Tbody>
                        </Table>
                    )}
            </Panel>}
            <DeleteQueryModal query={doomed} onClose={() => setDoomed(undefined)}/>
        </Page>
    );
}
