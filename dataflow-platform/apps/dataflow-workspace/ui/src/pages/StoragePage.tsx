import {Anchor, Group, Table} from '@mantine/core';
import {useMe, useStorage, useSyncStorage} from '../api/hooks';
import {Empty, ErrorState} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {TableSkeleton} from '../components/TableSkeleton';
import {Action} from '../components/actions/Action';
import {notifyFailure} from '../components/notify';
import {Notice} from '../components/Notice';

const layer = (prefix: string) => prefix.replace(/\/$/, '').split('/').slice(0, -1).join('/') || prefix;

export function StoragePage() {
    const {data: me} = useMe();
    const storage = useStorage();
    const sync = useSyncStorage();
    const data = storage.data;
    const ready = !!data?.status.enabled && !!data.status.appliedAt && !data.status.problem && !storage.isError;
    return (
        <Page>
            <PageHeader title="Object storage" actions={<Group gap="xs">
                {me?.admin && <Action label="Apply permissions" loading={sync.isPending} disabled={!data?.status.enabled}
                                      onClick={() => sync.mutate(undefined, {onError: notifyFailure()})}/>}
                <Action tone="primary" label="Sign in to object store" href={data?.signInUrl} external
                        disabled={!ready || !data?.signInUrl || (!me?.admin && !data?.datasets.length)}/>
            </Group>}/>
            {data?.status.enabled && !ready && <Notice tone="warning" title="Storage permissions need attention">
                {data.status.problem ?? 'Permissions have not been applied yet.'}
            </Notice>}
            <Panel flush>
                {storage.isLoading ? <TableSkeleton rows={4}/> : storage.error ? <ErrorState error={storage.error}/>
                    : !data?.status.enabled ? <Empty title="Scoped object storage is not configured"/>
                        : !data.datasets.length ? <Empty title="No dataset storage is shared with you"/>
                            : <Table>
                                <Table.Thead><Table.Tr><Table.Th>Dataset</Table.Th><Table.Th>Layers</Table.Th></Table.Tr></Table.Thead>
                                <Table.Tbody>{data.datasets.map((dataset) => (
                                    <Table.Tr key={dataset.dataset}>
                                        <Table.Td>{dataset.dataset}</Table.Td>
                                        <Table.Td>
                                            <Group className="df-chip-list">
                                                {dataset.paths.map((path) => ready
                                                    ? <Anchor key={path.prefix} href={path.url} target="_blank" rel="noreferrer"
                                                              className="df-chip df-link" title={path.prefix}>{layer(path.prefix)}</Anchor>
                                                    : <span key={path.prefix} className="df-chip" title={path.prefix}>{layer(path.prefix)}</span>)}
                                            </Group>
                                        </Table.Td>
                                    </Table.Tr>
                                ))}</Table.Tbody>
                            </Table>}
            </Panel>
        </Page>
    );
}
