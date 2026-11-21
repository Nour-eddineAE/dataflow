import {Anchor, NumberInput, Select, Stack, Table, Text} from '@mantine/core';
import {useDisclosure} from '@mantine/hooks';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useDeleteQueryAlert, useMe, useQueryAlerts, useSavedQueries, useSaveQueryAlert} from '../api/hooks';
import type {QueryAlert, QueryAlertComparison} from '../api/types';
import {IconBell, IconPlus} from '../icons';
import {alertCandidates, CHECK_INTERVALS, COMPARISONS, DEFAULT_COMPARISON, describeAlert, intervalLabel, needsThreshold} from '../lib/queryAlerts';
import {formatAgo} from '../lib/format';
import {links} from '../lib/links';
import {FormModal} from './FormModal';
import {Hint} from './Hint';
import {notifyFailure} from './notify';
import {StatusBadge} from './StatusBadge';
import {Action} from './actions/Action';
import {IconAction} from './actions/IconAction';

export function QueryAlertButton({queryId, name, alert}: { queryId: string; name: string; alert?: QueryAlert }) {
    const [opened, {open, close}] = useDisclosure(false);
    return (
        <>
            <IconAction label={alert ? 'Change alert' : 'Add alert'} icon={IconBell} size={28} active={!!alert}
                        onClick={open}/>
            {opened && <QueryAlertModal query={{id: queryId, name}} alert={alert} onClose={close}/>}
        </>
    );
}

export function AddQueryAlert() {
    const [opened, {open, close}] = useDisclosure(false);
    return (
        <>
            <Action tone="primary" icon={IconPlus} label="Add alert" onClick={open}/>
            {opened && <QueryAlertModal onClose={close}/>}
        </>
    );
}

function QueryAlertModal({query, alert, onClose}: {
    query?: { id: string; name: string };
    alert?: QueryAlert;
    onClose: () => void
}) {
    const {data: saved} = useSavedQueries();
    const {data: alerts} = useQueryAlerts();
    const {data: me} = useMe();
    const [queryId, setQueryId] = useState<string | null>(query?.id ?? null);
    const save = useSaveQueryAlert();
    const remove = useDeleteQueryAlert();
    const [every, setEvery] = useState(String(alert?.everyMinutes ?? CHECK_INTERVALS[1].minutes));
    const [comparison, setComparison] = useState<QueryAlertComparison>(alert?.comparison ?? DEFAULT_COMPARISON);
    const [threshold, setThreshold] = useState<number | string>(alert?.threshold ?? '');
    const compares = needsThreshold(comparison);
    const submit = () => queryId && save.mutate({
        queryId,
        everyMinutes: Number(every),
        comparison,
        threshold: compares ? Number(threshold) : null,
    }, {onSuccess: onClose, onError: notifyFailure('Alert not saved')});
    return (
        <FormModal opened onClose={onClose} title={query ? `Alert on ${query.name}` : 'Add alert'} submit="Save"
                   loading={save.isPending} submitDisabled={!queryId || (compares && threshold === '')} onSubmit={submit}>
            <Stack>
                {!query && (
                    <Select label="Query" searchable data-autofocus value={queryId} onChange={setQueryId}
                            data={alertCandidates(saved ?? [], alerts ?? [], me?.id).map(({id, name}) => ({value: id, label: name}))}
                            nothingFoundMessage={saved?.some((entry) => entry.owner === me?.id) ? 'All your saved queries have an alert' : 'Save a query first'} comboboxProps={{withinPortal: true}}/>
                )}
                <Select label="Check every" allowDeselect={false} value={every}
                        data={CHECK_INTERVALS.map(({minutes, label}) => ({value: String(minutes), label}))}
                        onChange={(value) => value && setEvery(value)} comboboxProps={{withinPortal: true}}/>
                <Select label="Alert when" allowDeselect={false} value={comparison} data={COMPARISONS}
                        onChange={(value) => value && setComparison(value as QueryAlertComparison)}
                        comboboxProps={{withinPortal: true}}/>
                {compares && <NumberInput label="Value" value={threshold} onChange={setThreshold}/>}
                {alert && (
                    <Action tone="subtle" danger label="Remove alert" loading={remove.isPending}
                            onClick={() => queryId && remove.mutate(queryId, {
                                onSuccess: onClose,
                                onError: notifyFailure('Alert not removed'),
                            })}/>
                )}
            </Stack>
        </FormModal>
    );
}

export function QueryAlertsTable({alerts}: { alerts: QueryAlert[] }) {
    return (
        <Table>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>State</Table.Th>
                    <Table.Th>Query</Table.Th>
                    <Table.Th>When</Table.Th>
                    <Table.Th>Value</Table.Th>
                    <Table.Th>Checked</Table.Th>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {alerts.map((alert) => (
                    <Table.Tr key={alert.queryId}>
                        <Table.Td>
                            <Hint label={alert.error}>
                                <span><StatusBadge status={alert.state}/></span>
                            </Hint>
                        </Table.Td>
                        <Table.Td>
                            <Anchor component={Link} to={links.savedQuery(alert.queryId)} size="sm"
                                    className="df-link">{alert.name}</Anchor>
                        </Table.Td>
                        <Table.Td>
                            <Text size="sm">{describeAlert(alert)}</Text>
                            <Text size="xs" c="dimmed">{intervalLabel(alert.everyMinutes)}</Text>
                        </Table.Td>
                        <Table.Td><Text size="sm" className="df-num">{alert.value ?? '—'}</Text></Table.Td>
                        <Table.Td>
                            <Text size="sm" c="dimmed">{alert.checkedAt ? formatAgo(alert.checkedAt) : '—'}</Text>
                        </Table.Td>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}
