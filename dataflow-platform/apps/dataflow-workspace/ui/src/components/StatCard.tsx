import {Card, Group, Text} from '@mantine/core';
import {IconChevronRight} from '../icons';
import type {ReactNode} from 'react';
import {Link} from 'react-router-dom';
import {ICON} from '../lib/icons';
import {COLOR} from '../lib/colors';

export function StatCard({label, value, icon, to}: {
    label: string;
    value: ReactNode;
    icon?: ReactNode;
    to?: string;
}) {
    const body = (
        <Group justify="space-between" wrap="nowrap">
            <div style={{minWidth: 0}}>
                <Text size="sm" c="dimmed" mb="tight">{label}</Text>
                <Group gap="cozy" wrap="nowrap">
                    {icon}
                    <Text fz="xl" fw={600} className="df-num" lh={1.2} component="div">{value}</Text>
                </Group>
            </div>
            {to && <IconChevronRight {...ICON} color={COLOR.dimmed} style={{flex: 'none'}}/>}
        </Group>
    );
    return to
        ? <Card padding="md" component={Link} to={to} className="df-tile">{body}</Card>
        : <Card padding="md">{body}</Card>;
}
