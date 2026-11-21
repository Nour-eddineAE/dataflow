import {Anchor, Group, Table, Text} from '@mantine/core';
import {type Icon, IconCode, IconRoute, IconServer, IconTable} from '../icons';
import {Link} from 'react-router-dom';
import type {FavoriteKind} from '../api/types';
import type {FavoriteEntry} from '../lib/favorites';
import {formatAgo} from '../lib/format';
import {ICON} from '../lib/icons';
import {FavoriteButton} from './FavoriteButton';
import {COLOR} from '../lib/colors';

const KINDS: Record<FavoriteKind, { icon: Icon; label: string }> = {
    PIPELINE: {icon: IconRoute, label: 'Pipeline'},
    TABLE: {icon: IconTable, label: 'Table'},
    SERVED: {icon: IconServer, label: 'Served table'},
    QUERY: {icon: IconCode, label: 'Query'},
};

export function FavoritesTable({items}: { items: FavoriteEntry[] }) {
    return (
        <Table>
            <Table.Thead>
                <Table.Tr>
                    <Table.Th>Name</Table.Th>
                    <Table.Th w={160}>Type</Table.Th>
                    <Table.Th w={160}>Added</Table.Th>
                    <Table.Th w={48}/>
                </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
                {items.map((item) => {
                    const kind = KINDS[item.kind];
                    return (
                        <Table.Tr key={`${item.kind}:${item.ref}`}>
                            <Table.Td>
                                <Group gap="xs" wrap="nowrap">
                                    <kind.icon {...ICON} color={COLOR.dimmed}/>
                                    <Anchor component={Link} to={item.to} size="sm" className="df-link">{item.label}</Anchor>
                                </Group>
                            </Table.Td>
                            <Table.Td><Text size="sm">{kind.label}</Text></Table.Td>
                            <Table.Td><Text size="sm" c="dimmed">{formatAgo(item.createdAt)}</Text></Table.Td>
                            <Table.Td><FavoriteButton kind={item.kind} reference={item.ref} size={28}/></Table.Td>
                        </Table.Tr>
                    );
                })}
            </Table.Tbody>
        </Table>
    );
}
