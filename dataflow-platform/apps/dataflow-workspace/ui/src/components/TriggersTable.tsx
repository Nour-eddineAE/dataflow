import {Anchor, Group, Switch, Table, Text} from '@mantine/core';
import {Link} from 'react-router-dom';
import {useToggleTrigger} from '../api/hooks';
import type {Trigger} from '../api/types';
import {triggerWhen} from '../lib/automation';
import {formatAgo, formatTime} from '../lib/format';
import {links} from '../lib/links';
import {Hint} from './Hint';
import {StatusBadge} from './StatusBadge';
import {notifyFailure} from './notify';

export function TriggersTable({triggers}: { triggers: Trigger[] }) {
    return (
        <Table>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Name</Table.Th>
                    <Table.Th>When</Table.Th>
                    <Table.Th>State</Table.Th>
                    <Table.Th>Last check</Table.Th>
                    <Table.Th>Next</Table.Th>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {triggers.map((trigger) => <TriggerRow key={trigger.name} trigger={trigger}/>)}
            </Table.Tbody>
        </Table>
    );
}

function TriggerRow({trigger}: { trigger: Trigger }) {
    const toggle = useToggleTrigger();
    const action = trigger.running ? 'pause' : 'resume';
    const change = () => toggle.mutate({name: trigger.name, action}, {
        onError: notifyFailure(`${trigger.name} not ${action}d`),
    });
    return (
        <Table.Tr>
            <Table.Td>
                {trigger.dataset
                    ? <Anchor component={Link} to={links.pipeline(trigger.dataset)} size="sm"
                              className="df-link">{trigger.dataset}</Anchor>
                    : <Text size="sm">{trigger.name}</Text>}
            </Table.Td>
            <Table.Td>
                <Hint label={trigger.cron && `${trigger.cron} (${trigger.timezone})`}>
                    <Text size="sm" span>{triggerWhen(trigger)}</Text>
                </Hint>
            </Table.Td>
            <Table.Td>
                <Switch size="sm" checked={trigger.running} disabled={toggle.isPending} onChange={change}
                        label={trigger.running ? 'Active' : <StatusBadge status="PAUSED"/>}/>
            </Table.Td>
            <Table.Td>
                {trigger.lastStatus ? (
                    <Hint label={trigger.lastMessage}>
                        <Group gap="xs" wrap="nowrap" display="inline-flex">
                            <StatusBadge status={trigger.lastStatus}/>
                            <Text size="sm" c="dimmed">{formatAgo(trigger.lastAt)}</Text>
                        </Group>
                    </Hint>
                ) : <Text size="sm" c="dimmed">—</Text>}
            </Table.Td>
            <Table.Td>
                <Text size="sm" c={trigger.running && trigger.nextAt ? undefined : 'dimmed'}>
                    {trigger.running && trigger.nextAt ? formatTime(trigger.nextAt) : '—'}
                </Text>
            </Table.Td>
        </Table.Tr>
    );
}
