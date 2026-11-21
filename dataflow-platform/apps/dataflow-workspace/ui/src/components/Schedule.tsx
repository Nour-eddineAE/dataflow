import {Group, Tooltip} from '@mantine/core';
import {useSchedule} from '../api/hooks';
import {describeCron, describeTrigger, formatTime} from '../lib/format';
import {StatusBadge} from './StatusBadge';

export function ScheduleMeta({dataset, cron, onInputs}: { dataset: string; cron?: string; onInputs?: boolean }) {
    const {data: state} = useSchedule(dataset, !!cron);
    if (!cron) return <>{describeTrigger(cron, !!onInputs)}</>;
    return (
        <Group gap="snug" wrap="nowrap">
            <Tooltip
                label={state?.running && state.nextRun ? `Next run ${formatTime(state.nextRun)}` : `${cron} (${state?.timezone ?? 'UTC'})`}>
                <span>{describeCron(cron)}</span>
            </Tooltip>
            {state && !state.running && <StatusBadge status="PAUSED"/>}
        </Group>
    );
}
