import {Center, Loader, Stack, Text} from '@mantine/core';
import type {ReactNode} from 'react';
import {Notice} from './Notice';
import {COLOR} from '../lib/colors';

export function ErrorState({error, title}: { error: unknown; title?: string }) {
    const message = error instanceof Error ? error.message : String(error);
    return (
        <Notice title={title}>
            <Text size="sm" style={{whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>{message}</Text>
        </Notice>
    );
}

export function Loading() {
    return (
        <Center py="hollow" className="df-delayed">
            <Loader size="sm"/>
        </Center>
    );
}

export function Empty({icon, title, action}: { icon?: ReactNode; title: string; action?: ReactNode }) {
    return (
        <Center py="hollow">
            <Stack align="center" gap="xs">
                {icon && <span style={{color: COLOR.dimmed}}>{icon}</span>}
                <Text c="dimmed" size="sm">{title}</Text>
                {action}
            </Stack>
        </Center>
    );
}
