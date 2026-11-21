import {notifications} from '@mantine/notifications';
import {useEffect, useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {isRunning, useRuns, RUN_LIMIT} from '../api/hooks';
import type {RunSummary} from '../api/types';
import {formatDuration, shortRunId} from '../lib/format';
import {links} from '../lib/links';
import {statusTone} from '../lib/status';
import {StatusIcon} from './StatusBadge';
import {NOTICE} from '../lib/timing';
import {ICON_LARGE} from '../lib/icons';


export function RunNotifier() {
    const navigate = useNavigate();
    const {data: runs} = useRuns(undefined, RUN_LIMIT.watched);
    const known = useRef<Map<string, string> | null>(null);

    useEffect(() => {
        if (!runs) return;
        const before = known.current;
        known.current = new Map(runs.map((run) => [run.runId, run.status]));
        if (!before) return;
        runs.filter((run) => isRunning(before.get(run.runId)) && !isRunning(run.status))
            .forEach((run) => announce(run, () => navigate(links.run(run.runId))));
    }, [runs, navigate]);

    return null;
}

function announce(run: RunSummary, open: () => void) {
    const {tone, label} = statusTone(run.status);
    notifications.show({
        id: `run-${run.runId}`,
        title: `${run.dataset ?? run.job} · ${label}`,
        message: `Run ${shortRunId(run.runId)}${run.partition ? ` · ${run.partition}` : ''} · ${formatDuration(run.durationMs)}`,
        icon: <StatusIcon status={run.status} {...ICON_LARGE}/>,
        color: 'transparent',
        autoClose: tone === 'danger' ? false : NOTICE.brief,
        style: {cursor: 'pointer'},
        onClick: open,
    });
}
