import {Tooltip} from '@mantine/core';
import type {ReactElement} from 'react';

const HINT_WIDTH = 360;

export function Hint({label, children}: { label?: string | null; children: ReactElement }) {
    return (
        <Tooltip label={label} disabled={!label} multiline maw={HINT_WIDTH} position="bottom-start">
            {children}
        </Tooltip>
    );
}
