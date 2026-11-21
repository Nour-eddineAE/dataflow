import {COLOR} from './colors';

export type Tone = 'success' | 'danger' | 'running' | 'queued' | 'neutral' | 'warning';

export type StatusTone = { tone: Tone; label: string };

const TONES: Record<string, StatusTone> = {
    SUCCESS: {tone: 'success', label: 'Succeeded'},
    COMPLETED: {tone: 'success', label: 'Succeeded'},
    MATERIALIZED: {tone: 'success', label: 'Done'},
    FAILURE: {tone: 'danger', label: 'Failed'},
    FAILED: {tone: 'danger', label: 'Failed'},
    STARTED: {tone: 'running', label: 'Running'},
    RUNNING: {tone: 'running', label: 'Running'},
    MATERIALIZING: {tone: 'running', label: 'Running'},
    STARTING: {tone: 'running', label: 'Starting'},
    QUEUED: {tone: 'queued', label: 'Queued'},
    NOT_STARTED: {tone: 'queued', label: 'Queued'},
    CANCELING: {tone: 'warning', label: 'Cancelling'},
    CANCELED: {tone: 'neutral', label: 'Cancelled'},
    SKIPPED: {tone: 'neutral', label: 'Skipped'},
    MISSING: {tone: 'neutral', label: 'Not run'},
    HEALTHY: {tone: 'success', label: 'Healthy'},
    UNREACHABLE: {tone: 'danger', label: 'Unreachable'},
    UNKNOWN: {tone: 'neutral', label: 'Not checked'},
    PASSED: {tone: 'success', label: 'Passed'},
    WARNING: {tone: 'warning', label: 'Warning'},
    PAUSED: {tone: 'warning', label: 'Paused'},
    OK: {tone: 'success', label: 'OK'},
    FIRING: {tone: 'danger', label: 'Firing'},
    ERROR: {tone: 'danger', label: 'Error'},
};

export const statusTone = (status?: string): StatusTone =>
    TONES[status ?? ''] ?? {
        tone: 'neutral',
        label: status ? status.charAt(0) + status.slice(1).toLowerCase() : 'Never run'
    };

const ATTENTION: Tone[] = ['danger', 'warning', 'running', 'queued', 'neutral', 'success'];

export type StatusCount = { status: string; label: string; count: number };

export function countStatuses(statuses: string[]): StatusCount[] {
    const counts = new Map<string, StatusCount>();
    for (const status of statuses) {
        const {label} = statusTone(status);
        const seen = counts.get(label);
        counts.set(label, {status: seen?.status ?? status, label, count: (seen?.count ?? 0) + 1});
    }
    const rank = (count: StatusCount) => ATTENTION.indexOf(statusTone(count.status).tone);
    return [...counts.values()].sort((left, right) => rank(left) - rank(right));
}

export const toneColor: Record<Tone, string> = {
    success: COLOR.success,
    danger: COLOR.danger,
    running: COLOR.link,
    queued: COLOR.dimmed,
    neutral: COLOR.dimmed,
    warning: COLOR.warning,
};
