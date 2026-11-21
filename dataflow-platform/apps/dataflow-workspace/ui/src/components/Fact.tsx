import {Box, Group, Text} from '@mantine/core';
import type {ReactNode} from 'react';

export function Fact({label, children}: { label: string; children: ReactNode }) {
    return (
        <Group gap="md" wrap="nowrap" align="flex-start">
            <Text size="sm" c="dimmed" w={104} style={{flex: 'none'}}>{label}</Text>
            <Box style={{minWidth: 0}}><Text size="sm" component="div">{children}</Text></Box>
        </Group>
    );
}
