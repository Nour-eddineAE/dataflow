import {Anchor, Group, Table, Text} from '@mantine/core';
import {type Icon, IconActivity, IconRoute, IconServer, IconTable} from '../icons';
import {Link} from 'react-router-dom';
import {formatAgo} from '../lib/format';
import type {Recent, RecentKind} from '../lib/recents';
import {COLOR} from '../lib/colors';
import {ICON} from '../lib/icons';

const KINDS: Record<RecentKind, { icon: Icon; label: string }> = {
    table: {icon: IconTable, label: 'Table'},
    served: {icon: IconServer, label: 'Served table'},
    pipeline: {icon: IconRoute, label: 'Pipeline'},
    run: {icon: IconActivity, label: 'Run'},
};

export function RecentsTable({items}: { items: Recent[] }) {
    return (
        <Table>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Name</Table.Th>
                    <Table.Th w={160}>Type</Table.Th>
                    <Table.Th w={160}>Opened</Table.Th>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {items.map((item) => {
                    const kind = KINDS[item.kind];
                    return (
                        <Table.Tr key={item.to}>
                            <Table.Td>
                                <Group gap="xs" wrap="nowrap">
                                    <kind.icon {...ICON} color={COLOR.dimmed}/>
                                    <Anchor component={Link} to={item.to} size="sm"
                                            className="df-link">{item.label}</Anchor>
                                </Group>
                            </Table.Td>
                            <Table.Td><Text size="sm">{kind.label}</Text></Table.Td>
                            <Table.Td><Text size="sm" c="dimmed">{formatAgo(item.at)}</Text></Table.Td>
                        </Table.Tr>
                    );
                })}
            </Table.Tbody>
        </Table>
    );
}
