import {Group, type MantineSpacing, Stack, Text} from '@mantine/core';
import {IconAlertCircle, IconAlertTriangle} from '../icons';
import type {ReactNode} from 'react';
import {ICON_LARGE} from '../lib/icons';

const ICONS = {danger: IconAlertCircle, warning: IconAlertTriangle};

export function Notice({tone = 'danger', title, children, mb}: {
    tone?: 'danger' | 'warning';
    title?: ReactNode;
    children: ReactNode;
    mb?: MantineSpacing;
}) {
    const Icon = ICONS[tone];
    return (
        <Group className="df-notice" data-tone={tone} gap="sm" wrap="nowrap" align="flex-start" role="alert" mb={mb}>
            <Icon {...ICON_LARGE} color={`var(--df-${tone})`} style={{flex: 'none', marginTop: 1}}/>
            <Stack gap="tight" style={{minWidth: 0, flex: 1}}>
                {title && <Text size="sm" fw={600}>{title}</Text>}
                {children}
            </Stack>
        </Group>
    );
}
