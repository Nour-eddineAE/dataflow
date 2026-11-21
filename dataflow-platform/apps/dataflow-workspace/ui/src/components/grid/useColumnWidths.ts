import {type MouseEvent as ReactMouseEvent, useMemo, useState} from 'react';
import type {QueryResult} from '../../api/types';
import {columnWidths} from '../../lib/grid';

const MIN_WIDTH = 60;

export function useColumnWidths(result: QueryResult) {
    const [resized, setResized] = useState<Record<number, number>>({});
    const measured = useMemo(() => columnWidths(result), [result]);
    const widths = useMemo(() => measured.map((width, index) => resized[index] ?? width), [measured, resized]);

    const startResize = (index: number, event: ReactMouseEvent) => {
        event.preventDefault();
        event.stopPropagation();
        const startX = event.clientX;
        const startWidth = widths[index];
        const move = (moved: MouseEvent) => setResized((current) => ({
            ...current,
            [index]: Math.max(MIN_WIDTH, startWidth + moved.clientX - startX)
        }));
        const stop = () => {
            window.removeEventListener('mousemove', move);
            window.removeEventListener('mouseup', stop);
            document.body.style.cursor = '';
        };
        document.body.style.cursor = 'col-resize';
        window.addEventListener('mousemove', move);
        window.addEventListener('mouseup', stop);
    };

    const resetWidth = (index: number, event: ReactMouseEvent) => {
        event.stopPropagation();
        setResized(({[index]: _, ...rest}) => rest);
    };

    return {widths, startResize, resetWidth};
}
