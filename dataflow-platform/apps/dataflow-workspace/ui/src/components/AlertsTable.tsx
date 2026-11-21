import {Anchor, Group, Table, Text} from '@mantine/core';
import {IconAlertTriangle, IconClock} from '../icons';
import {Link} from 'react-router-dom';
import type {Alert} from '../api/types';
import {formatAgo} from '../lib/format';
import {ICON} from '../lib/icons';
import {links} from '../lib/links';
import {COLOR} from '../lib/colors';

export function AlertsTable({alerts}: { alerts: Alert[] }) {
    return (
        <Table>
            <Table.Tbody>
                {alerts.map((alert, index) => (
                    <Table.Tr key={index}>
                        <Table.Td w={120}>
                            <Group gap="snug" wrap="nowrap"
                                   style={{color: alert.state === 'pending' ? COLOR.warning : COLOR.danger}}>
                                {alert.state === 'pending' ? <IconClock {...ICON}/> :
                                    <IconAlertTriangle {...ICON}/>}
                                <Text size="sm" c="inherit">{alert.state === 'pending' ? 'Pending' : 'Firing'}</Text>
                            </Group>
                        </Table.Td>
                        <Table.Td w={160}>
                            {alert.dataset ? <Anchor component={Link} to={links.board(alert.dataset)} size="sm"
                                                     className="df-link">{alert.dataset}</Anchor> : null}
                        </Table.Td>
                        <Table.Td><Text size="sm">{alert.summary ?? alert.name}</Text></Table.Td>
                        <Table.Td w={120}><Text size="sm" c="dimmed">{alert.since ? formatAgo(alert.since) : ''}</Text></Table.Td>
                    </Table.Tr>
                ))}
            </Table.Tbody>
        </Table>
    );
}
