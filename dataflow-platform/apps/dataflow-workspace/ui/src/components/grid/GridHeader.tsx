import {Group, Text} from '@mantine/core';
import type {MouseEvent as ReactMouseEvent} from 'react';
import type {QueryResult} from '../../api/types';
import {IconArrowDown, IconArrowUp} from '../../icons';
import {type GridSort, sortLabel} from '../../lib/grid';
import {ICON_SMALL} from '../../lib/icons';
import {shortType} from '../../lib/types';

type Props = {
    columns: QueryResult['columns'];
    widths: number[];
    indexWidth: number;
    actionWidth?: number;
    sort: GridSort;
    onSort: (column: number) => void;
    onResize: (column: number, event: ReactMouseEvent) => void;
    onResetWidth: (column: number, event: ReactMouseEvent) => void;
};

export function GridHeader({columns, widths, indexWidth, actionWidth, sort, onSort, onResize, onResetWidth}: Props) {
    return (
        <div className="df-grid-head">
            <div className="df-grid-hcell" style={{width: indexWidth}}/>
            {columns.map((column, index) => (
                <div
                    key={column.name + index}
                    className="df-grid-hcell"
                    style={{width: widths[index], position: 'relative'}}
                >
                    <button type="button" className="df-grid-sort" onClick={() => onSort(index)}
                            aria-label={sortLabel(column.name, sort?.column === index ? sort : null)}>
                        <Group gap="tight" wrap="nowrap">
                            <Text size="xs" fw={650} truncate>
                                {column.name}
                            </Text>
                            {sort?.column === index && (sort.desc ? <IconArrowDown {...ICON_SMALL}/> :
                                <IconArrowUp {...ICON_SMALL}/>)}
                        </Group>
                        <Text fz="caption" c="dimmed" truncate>
                            {shortType(column.type)}
                        </Text>
                    </button>
                    <span className="df-col-resize" onMouseDown={(event) => onResize(index, event)}
                          onClick={(event) => event.stopPropagation()}
                          onDoubleClick={(event) => onResetWidth(index, event)}/>
                </div>
            ))}
            {actionWidth !== undefined && <div className="df-grid-hcell" style={{width: actionWidth}}/>}
        </div>
    );
}
