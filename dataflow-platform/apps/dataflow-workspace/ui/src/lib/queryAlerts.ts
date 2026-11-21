import type {QueryAlert, QueryAlertComparison, SavedQuery} from '../api/types';
import {HOURS_PER_DAY, MINUTES_PER_HOUR} from './timing';

const FIRST_ROW_CHECK: QueryAlertComparison = 'ANY_ROWS';

export const DEFAULT_COMPARISON = FIRST_ROW_CHECK;

const LABELS: Record<QueryAlertComparison, string> = {
    ANY_ROWS: 'It returns any rows',
    NO_ROWS: 'It returns no rows',
    ABOVE: 'The first value is above',
    BELOW: 'The first value is below',
    EQUAL: 'The first value equals',
};

export const COMPARISONS = (Object.entries(LABELS)).map(([value, label]) => ({value, label}));

export const CHECK_INTERVALS = [
    {minutes: 5, label: '5 minutes'},
    {minutes: 15, label: '15 minutes'},
    {minutes: 60, label: 'Hour'},
    {minutes: HOURS_PER_DAY * MINUTES_PER_HOUR, label: 'Day'},
];

export const needsThreshold = (comparison: QueryAlertComparison) =>
    comparison !== FIRST_ROW_CHECK && comparison !== 'NO_ROWS';

export const describeAlert = (alert: Pick<QueryAlert, 'comparison' | 'threshold'>) =>
    needsThreshold(alert.comparison) ? `${LABELS[alert.comparison]} ${alert.threshold}` : LABELS[alert.comparison];

export function intervalLabel(minutes: number) {
    const known = CHECK_INTERVALS.find((interval) => interval.minutes === minutes);
    return `Every ${(known?.label ?? `${minutes} minutes`).toLowerCase()}`;
}

export const alertCandidates = (queries: SavedQuery[], alerts: Pick<QueryAlert, 'queryId'>[], me?: string) =>
    queries.filter((query) => query.owner === me && !alerts.some((alert) => alert.queryId === query.id));
