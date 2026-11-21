import {Anchor, Group, Table, Tabs, Text} from '@mantine/core';
import {useState} from 'react';
import {IconExternalLink, IconRefresh} from '../icons';
import {useMe, useReloadTower, useServices, useServingHealth, useSyncCatalog} from '../api/hooks';
import type {PlatformService} from '../api/types';
import {Empty, ErrorState} from '../components/ErrorState';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {Panel} from '../components/Panel';
import {StatusBadge} from '../components/StatusBadge';
import {TableSkeleton} from '../components/TableSkeleton';
import {NumberCell} from '../components/NumberCell';
import {formatBytes, formatDuration, formatNumber} from '../lib/format';
import {COLOR} from '../lib/colors';
import {ICON_TINY} from '../lib/icons';
import {PAGE} from '../lib/links';

import {Hint} from '../components/Hint';
import {Action} from '../components/actions/Action';
import {notifyDone, notifyFailure} from '../components/notify';

const LAKE = 'lake';

export function ServicesPage() {
    const services = useServices();
    const {data: me} = useMe();
    const [checking, setChecking] = useState(false);
    const check = () => {
        setChecking(true);
        services.refetch().finally(() => setChecking(false));
    };
    return (
        <Page>
            <PageHeader title="Services" actions={<Action icon={IconRefresh} label="Check" loading={checking} onClick={check}/>}/>
            {me?.admin ? (
                <Tabs defaultValue="health" keepMounted={false}>
                    <Tabs.List mb="lg">
                        <Tabs.Tab value="health">Health</Tabs.Tab>
                        <Tabs.Tab value="serving">Serving activity</Tabs.Tab>
                    </Tabs.List>
                    <Tabs.Panel value="health"><HealthTable services={services}/></Tabs.Panel>
                    <Tabs.Panel value="serving"><ServingHealthPanel/></Tabs.Panel>
                </Tabs>
            ) : <HealthTable services={services}/>}
        </Page>
    );
}

function HealthTable({services}: { services: ReturnType<typeof useServices> }) {
    return (
        <Panel flush>
            {services.isLoading ? <TableSkeleton rows={4}/> : services.error ? <ErrorState error={services.error}/>
                : !services.data?.length ? <Empty title="No service is shared with you"/> : (
                    <Table>
                        <Table.Thead>
                            <Table.Tr>
                                <Table.Th>Service</Table.Th>
                                <Table.Th>Runs on</Table.Th>
                                <Table.Th>Status</Table.Th>
                                <Table.Th>Version</Table.Th>
                                <Table.Th ta="right">Response</Table.Th>
                                <Table.Th/>
                            </Table.Tr>
                        </Table.Thead>
                        <Table.Tbody>
                            {services.data.map((service) => <ServiceRow key={service.id} service={service}/>)}
                        </Table.Tbody>
                    </Table>
                )}
        </Panel>
    );
}

function ServingHealthPanel() {
    const health = useServingHealth();
    const activity = health.data?.activity;
    return (
        <Panel flush title="ClickHouse, last hour">
            {health.isLoading ? <TableSkeleton rows={1}/> : health.error ? <ErrorState error={health.error}/> : (
                <Table>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th ta="right">Queries</Table.Th>
                            <Table.Th ta="right">Failed</Table.Th>
                            <Table.Th ta="right">Average</Table.Th>
                            <Table.Th ta="right">Slowest</Table.Th>
                            <Table.Th ta="right">Memory now</Table.Th>
                            <Table.Th>Failed refreshes</Table.Th>
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                        <Table.Tr>
                            <NumberCell>{formatNumber(activity?.queries)}</NumberCell>
                            <NumberCell color={activity?.failed ? COLOR.danger : undefined}>{formatNumber(activity?.failed)}</NumberCell>
                            <NumberCell>{activity?.averageMs == null ? '—' : formatDuration(Math.round(activity.averageMs))}</NumberCell>
                            <NumberCell>{formatDuration(activity?.slowestMs)}</NumberCell>
                            <NumberCell>{formatBytes(health.data?.memoryBytes)}</NumberCell>
                            <Table.Td>
                                {health.data?.failedRefreshes.length ? (
                                    <Group className="df-chip-list">
                                        {health.data.failedRefreshes.map((refresh) => (
                                            <Hint key={`${refresh.database}.${refresh.view}`} label={refresh.error}>
                                                <span className="df-chip">{refresh.database}.{refresh.view}</span>
                                            </Hint>
                                        ))}
                                    </Group>
                                ) : <Text size="sm" c="dimmed">None</Text>}
                            </Table.Td>
                        </Table.Tr>
                    </Table.Tbody>
                </Table>
            )}
        </Panel>
    );
}

function ServiceRow({service}: { service: PlatformService }) {
    const reload = useReloadTower();
    const sync = useSyncCatalog();
    const failed = notifyFailure();
    return (
        <Table.Tr>
            <Table.Td>
                {service.link
                    ? (
                        <Anchor href={service.link} target="_blank" rel="noreferrer" size="sm" className="df-link">
                            <Group component="span" gap="tight" wrap="nowrap" display="inline-flex">
                                {service.name}<IconExternalLink {...ICON_TINY} aria-label="Opens in a new tab"/>
                            </Group>
                        </Anchor>
                    )
                    : <Text size="sm">{service.name}</Text>}
            </Table.Td>
            <Table.Td><Text size="sm" c="dimmed">{service.runsOn}</Text></Table.Td>
            <Table.Td>
                <Hint label={service.detail}>
                    <span><StatusBadge status={service.health}/></span>
                </Hint>
            </Table.Td>
            <Table.Td><Text size="sm" className="df-mono" c={service.version ? undefined : 'dimmed'}>{service.version ?? '—'}</Text></Table.Td>
            <NumberCell>{formatDuration(service.responseMs)}</NumberCell>
            <Table.Td>
                <Group gap="xs" justify="flex-end" wrap="nowrap">
                    {service.id === LAKE && <Action size="sm" label="Browse files" to={PAGE.storage}/>}
                    {service.actions.includes('reload') && (
                        <Action size="sm" label="Reload specs" loading={reload.isPending}
                                onClick={() => reload.mutate(undefined, {
                                    onSuccess: () => notifyDone({message: 'Pipeline specs reloaded'}),
                                    onError: failed
                                })}/>
                    )}
                    {service.actions.includes('sync') && (
                        <Action size="sm" label="Recreate views" loading={sync.isPending}
                                onClick={() => sync.mutate(undefined, {
                                    onSuccess: ({views}) => notifyDone({message: `${views} views recreated`}),
                                    onError: failed
                                })}/>
                    )}
                </Group>
            </Table.Td>
        </Table.Tr>
    );
}
