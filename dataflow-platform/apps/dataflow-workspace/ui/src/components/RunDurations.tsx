import {Box, Group, Text, Tooltip} from '@mantine/core';
import {useNavigate} from 'react-router-dom';
import type {RunSummary} from '../api/types';
import {formatDuration, formatTime, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {statusTone, toneColor} from '../lib/status';
import {barScale} from '../lib/chartKeys';
import {moveBarFocus} from './barKeys';

const SHORTEST_BAR = 4;
const FULL = 100;

export function RunDurations({runs}: { runs: RunSummary[] }) {
    const navigate = useNavigate();
    const ordered = [...runs].reverse();
    const scale = barScale(ordered.map((run) => run.durationMs ?? 0));
    const present = [ordered.map(() => true)];
    return (
        <Box>
            <Group justify="flex-end" className="df-durations-scale">
                <Text size="xs" c="dimmed" className="df-num">{formatDuration(scale)}</Text>
            </Group>
            <div className="df-durations" role="group" aria-label="Run durations" onKeyDown={(event) => moveBarFocus(event, present)}>
                {ordered.map((run, index) => {
                    const {tone, label} = statusTone(run.status);
                    return (
                        <Tooltip key={run.runId}
                                 label={`${formatTime(run.startedAt ?? run.createdAt)} · ${label} · ${formatDuration(run.durationMs)}`}>
                            <button type="button" className="df-duration" aria-label={`Run ${shortRunId(run.runId)}`}
                                    data-row={0} data-col={index} tabIndex={index === ordered.length - 1 ? 0 : -1}
                                    data-capped={(run.durationMs ?? 0) > scale || undefined}
                                    onClick={() => navigate(links.run(run.runId))}
                                    style={{
                                        height: `${Math.min(FULL, Math.max(SHORTEST_BAR, ((run.durationMs ?? 0) / scale) * FULL))}%`,
                                        background: toneColor[tone]
                                    }}/>
                        </Tooltip>
                    );
                })}
            </div>
        </Box>
    );
}
