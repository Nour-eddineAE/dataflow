import {Container} from '@mantine/core';
import type {ReactNode} from 'react';
import {DIALOG_WIDTHS} from './FormModal';

export const PAGE_WIDTH = 1280;
export const WIDE_PAGE_WIDTH = 1440;
export const DIALOG_WIDTH = DIALOG_WIDTHS.lg;

export function Page({children, wide}: { children: ReactNode; wide?: boolean }) {
    return (
        <Container size={wide ? WIDE_PAGE_WIDTH : PAGE_WIDTH} px="xl" pt="lg" pb="section">
            {children}
        </Container>
    );
}
