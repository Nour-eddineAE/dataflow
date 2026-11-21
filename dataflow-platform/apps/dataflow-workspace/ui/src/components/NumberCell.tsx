import {Table, Text} from '@mantine/core';
import type {ReactNode} from 'react';

export function NumberCell({children, color}: { children: ReactNode; color?: string }) {
    return (
        <Table.Td ta="right">
            <Text size="sm" className="df-num" c={color}>{children}</Text>
        </Table.Td>
    );
}
