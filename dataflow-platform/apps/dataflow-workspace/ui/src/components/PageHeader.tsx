import {Anchor, Breadcrumbs, Group, Stack, Text, Title} from '@mantine/core';
import type {ReactNode} from 'react';
import {Link} from 'react-router-dom';
import {COLOR} from '../lib/colors';

export type Crumb = { label: string; to?: string };

export function PageHeader({title, crumbs, actions, icon, meta}: {
    title: ReactNode;
    crumbs?: Crumb[];
    actions?: ReactNode;
    icon?: ReactNode;
    meta?: ReactNode;
}) {
    return (
        <Stack gap="tight" mb="lg">
            {crumbs && (
                <Breadcrumbs separatorMargin="snug" separator="/" fz="xs">
                    {crumbs.map((crumb) =>
                        crumb.to ? (
                            <Anchor key={crumb.label} component={Link} to={crumb.to} size="xs"
                                    c="dimmed">{crumb.label}</Anchor>
                        ) : (
                            <Text key={crumb.label} size="xs" c="dimmed">{crumb.label}</Text>
                        ),
                    )}
                </Breadcrumbs>
            )}
            <Group justify="space-between" align="center" wrap="nowrap" mih={36}>
                <Group gap="xs" wrap="nowrap" style={{minWidth: 0}}>
                    {icon}
                    <Title order={1} fz="xl" lh={1.25} style={{minWidth: 0}}>{title}</Title>
                </Group>
                {actions && <Group gap="cozy" wrap="nowrap">{actions}</Group>}
            </Group>
            {meta && <Group gap="lg" c="dimmed" fz="sm">{meta}</Group>}
        </Stack>
    );
}

export function Meta({label, children}: { label?: string; children: ReactNode }) {
    return (
        <Group gap="snug" wrap="nowrap">
            {label && <Text size="sm" c="dimmed">{label}</Text>}
            <Text size="sm" c={label ? COLOR.text : 'dimmed'} component="div">{children}</Text>
        </Group>
    );
}
