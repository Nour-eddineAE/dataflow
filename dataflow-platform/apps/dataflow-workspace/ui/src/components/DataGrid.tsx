import {useVirtualizer} from '@tanstack/react-virtual';
import {type KeyboardEvent, type ReactNode, useMemo, useRef, useState} from 'react';
import type {QueryResult} from '../api/types';
import {cellText} from '../lib/format';
import {type GridSort, nextSort, sortRows} from '../lib/grid';
import {isNumericType} from '../lib/types';
import {GridCell} from './grid/GridCell';
import {GridHeader} from './grid/GridHeader';
import {useColumnWidths} from './grid/useColumnWidths';

const HEADER_HEIGHT = 44;
const ROW_HEIGHT = 31;
const INDEX_WIDTH = 52;
const ACTION_WIDTH = 64;

type Props = {
    result: QueryResult;
    height?: number | string;
    onRowClick?: (row: unknown[], index: number) => void;
    rowAction?: (row: unknown[]) => ReactNode;
};

export const fittedHeight = (rows: number, most: number) => Math.min(most, HEADER_HEIGHT + rows * ROW_HEIGHT);

export function DataGrid({result, height = 420, onRowClick, rowAction}: Props) {
    const [sort, setSort] = useState<GridSort>(null);
    const [selection, setSelection] = useState<{ rows: unknown[][]; row: number; column: number } | null>(null);
    const parent = useRef<HTMLDivElement>(null);
    const {widths, startResize, resetWidth} = useColumnWidths(result);
    const rows = useMemo(() => sortRows(result.rows, sort), [result.rows, sort]);

    const virtualizer = useVirtualizer({
        count: rows.length,
        getScrollElement: () => parent.current,
        estimateSize: () => ROW_HEIGHT,
        overscan: 12,
    });

    const selected = selection?.rows === rows ? selection : null;

    const copySelection = (event: KeyboardEvent) => {
        if (event.key === 'Escape') setSelection(null);
        if (!selected || !(event.metaKey || event.ctrlKey) || event.key !== 'c' || window.getSelection()?.toString()) return;
        event.preventDefault();
        void navigator.clipboard?.writeText(cellText(rows[selected.row]?.[selected.column]));
    };

    const totalWidth = INDEX_WIDTH + widths.reduce((sum, width) => sum + width, 0) + (rowAction ? ACTION_WIDTH : 0);

    return (
        <div className="df-grid" tabIndex={0} onKeyDown={copySelection}>
            <div ref={parent} className="df-grid-scroll" style={{height}}>
                <div style={{width: totalWidth, minWidth: '100%'}}>
                    <GridHeader columns={result.columns} widths={widths} indexWidth={INDEX_WIDTH}
                                actionWidth={rowAction ? ACTION_WIDTH : undefined} sort={sort}
                                onSort={(column) => setSort((current) => nextSort(current, column))}
                                onResize={startResize} onResetWidth={resetWidth}/>
                    <div style={{height: virtualizer.getTotalSize(), position: 'relative'}}>
                        {virtualizer.getVirtualItems().map((item) => {
                            const row = rows[item.index];
                            return (
                                <div
                                    key={item.key}
                                    className="df-grid-row"
                                    style={{
                                        position: 'absolute',
                                        top: 0,
                                        transform: `translateY(${item.start}px)`,
                                        height: ROW_HEIGHT,
                                        width: '100%',
                                        cursor: onRowClick ? 'pointer' : undefined
                                    }}
                                    onClick={onRowClick ? () => onRowClick(row, item.index) : undefined}
                                >
                                    <div className="df-grid-cell idx" style={{width: INDEX_WIDTH}}>
                                        {item.index + 1}
                                    </div>
                                    {result.columns.map((column, index) => (
                                        <GridCell key={index} value={row[index]} type={column.type} width={widths[index]}
                                                  numeric={isNumericType(column.type)} expandOnDoubleClick={!!onRowClick}
                                                  selected={selected?.row === item.index && selected.column === index}
                                                  onSelect={onRowClick ? undefined : () => setSelection({
                                                      rows,
                                                      row: item.index,
                                                      column: index
                                                  })}/>
                                    ))}
                                    {rowAction && (
                                        <div className="df-grid-cell" style={{width: ACTION_WIDTH, padding: '3px 6px'}}
                                             onClick={(event) => event.stopPropagation()}>
                                            {rowAction(row)}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
}
