import {Group, Text, Tooltip} from '@mantine/core';
import {useNavigate} from 'react-router-dom';
import type {FlowCell} from '../api/types';
import {formatDuration, formatNumber, formatTime, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {buildMatrix} from '../lib/runMatrix';
import {statusTone, toneColor} from '../lib/status';
import {ICON_SMALL} from '../lib/icons';
import {moveBarFocus} from './barKeys';
import {StatusIcon} from './StatusBadge';
import './RunMatrix.css';

const MIN_SHARE = 0.18;

export type MatrixMetric = 'duration' | 'rows';

export const MATRIX_METRICS: { value: MatrixMetric; label: string }[] = [
    {value: 'duration', label: 'Duration'},
    {value: 'rows', label: 'Rows'},
];

export function RunMatrix({cells, metric}: { cells: FlowCell[]; metric: MatrixMetric }) {
    const navigate = useNavigate();
    const matrix = buildMatrix(cells);
    if (!matrix.runs.length) return null;
    const first = matrix.runs[0];
    const last = matrix.runs[matrix.runs.length - 1];
    const present = matrix.flows.map((flow) => matrix.runs.map((run) => !!matrix.cell(run.runId, flow)));
    const entry = present.flatMap((row, rowIndex) => row.map((has, col) => (has ? `${rowIndex}:${col}` : ''))).find(Boolean);
    return (
        <div>
            <div className="df-matrix" style={{gridTemplateColumns: `max-content repeat(${matrix.runs.length}, minmax(6px, 1fr))`}}
                 role="table" aria-label="Flows by run" onKeyDown={(event) => moveBarFocus(event, present)}>
                {matrix.flows.map((flow, row) => (
                    <div key={flow} className="df-matrix-row" role="row">
                        <div className="df-matrix-flow df-mono" role="rowheader">{flow}</div>
                        {matrix.runs.map((run, col) => {
                            const cell = matrix.cell(run.runId, flow);
                            if (!cell) return <div key={run.runId} className="df-matrix-cell" role="cell"><span className="df-matrix-none"/></div>;
                            const {tone, label} = statusTone(cell.status);
                            const byRows = metric === 'rows';
                            const peak = byRows ? matrix.mostRows(flow) : matrix.longest(flow);
                            const value = byRows ? cell.rowsOut : cell.durationMs;
                            const share = peak ? Math.max(MIN_SHARE, (value ?? 0) / peak) : MIN_SHARE;
                            const alarm = byRows ? matrix.rowsAlarm(run.runId, flow) : undefined;
                            return (
                                <div key={run.runId} className="df-matrix-cell" role="cell">
                                    <Tooltip withArrow label={
                                        <div>
                                            <div>{flow} · {label}</div>
                                            <div>{formatDuration(cell.durationMs)}{cell.rowsOut != null && ` · ${formatNumber(cell.rowsOut)} rows`}</div>
                                            {alarm && <div>{alarm} against the previous run</div>}
                                            <div>{formatTime(cell.startedAt)}</div>
                                        </div>
                                    }>
                                        <button type="button" className="df-matrix-bar" aria-label={`${flow} ${label} in run ${shortRunId(run.runId)}`}
                                                data-row={row} data-col={col} tabIndex={entry === `${row}:${col}` ? 0 : -1}
                                                style={{height: `${share * 100}%`, background: toneColor[alarm ? 'warning' : tone]}}
                                                onClick={() => navigate(links.run(run.runId))}/>
                                    </Tooltip>
                                </div>
                            );
                        })}
                    </div>
                ))}
            </div>
            <Group justify="space-between" className="df-matrix-axis">
                <Text size="xs" c="dimmed">{formatTime(first.startedAt)}</Text>
                {metric === 'rows' && (
                    <Group gap="tight" wrap="nowrap">
                        <StatusIcon status="WARNING" size={ICON_SMALL.size}/>
                        <Text size="xs" c="dimmed">Rows down a fifth or more</Text>
                    </Group>
                )}
                <Text size="xs" c="dimmed">{formatTime(last.startedAt)}</Text>
            </Group>
        </div>
    );
}
