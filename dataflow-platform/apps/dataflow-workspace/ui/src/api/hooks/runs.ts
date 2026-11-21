import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import {queries} from './queries';
import type {RunComparison, RunOutput, RunSummary, ScheduleState, Trigger, Why} from '../types';
import {FRESH, POLL} from '../../lib/timing';

const RUNNING = ['QUEUED', 'NOT_STARTED', 'STARTING', 'STARTED', 'CANCELING'];

export const isRunning = (status?: string) => !!status && RUNNING.includes(status);

export const RUN_LIMIT = {watched: 20, pipeline: 50, all: 100} as const;

export const useRuns = (dataset?: string, limit: number = RUN_LIMIT.pipeline) =>
    useQuery({
        queryKey: ['runs', dataset ?? 'all', limit],
        queryFn: () => api.get<RunSummary[]>(`/runs?limit=${limit}${dataset ? `&dataset=${dataset}` : ''}`),
        refetchInterval: (query) => (query.state.data?.some((run) => isRunning(run.status)) ? POLL.active : POLL.list),
    });

export const useRun = (runId: string) =>
    useQuery({
        ...queries.run(runId),
        refetchInterval: (query) => (isRunning(query.state.data?.run.status) ? POLL.running : false)
    });

export const useRunOutput = (runId: string, running: boolean) =>
    useQuery({
        queryKey: ['run-output', runId],
        queryFn: () => api.get<RunOutput>(`/runs/${runId}/output`),
        refetchInterval: running ? POLL.active : false,
    });

export const useRunComparison = (runId: string, dataset: string | undefined) =>
    useQuery({
        queryKey: ['run-previous', runId],
        queryFn: () => api.get<RunComparison>(`/runs/${runId}/previous?dataset=${dataset}`),
        enabled: !!dataset,
        staleTime: Infinity,
    });

export const useWhy = (dataset: string, id?: string, column?: string) =>
    useQuery({
        queryKey: ['why', dataset, id, column],
        queryFn: () => api.get<Why>(`/datasets/${dataset}/why?id=${encodeURIComponent(id ?? '')}${column ? `&column=${encodeURIComponent(column)}` : ''}`),
        enabled: !!id,
        retry: false,
    });

export function useLaunch() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (body: { dataset: string; partition?: string; fullRefresh?: boolean }) =>
            api.post<{ runId: string }>('/runs', body),
        onSuccess: () => client.invalidateQueries({queryKey: ['runs']}),
    });
}

export const useSchedule = (dataset: string, enabled: boolean) =>
    useQuery({
        queryKey: ['schedule', dataset],
        queryFn: () => api.get<ScheduleState>(`/datasets/${dataset}/schedule`),
        enabled
    });

export const useSchedules = () =>
    useQuery({
        queryKey: ['schedules'],
        queryFn: () => api.get<Record<string, boolean>>('/schedules'),
        staleTime: FRESH.list
    });

export function useToggleSchedule(dataset: string) {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (action: 'pause' | 'resume') => api.post<ScheduleState>(`/datasets/${dataset}/schedule/${action}`),
        onSuccess: (state) => {
            client.setQueryData(['schedule', dataset], state);
            client.invalidateQueries({queryKey: ['schedules']});
            client.invalidateQueries({queryKey: ['automation']});
        },
    });
}

export const useAutomation = () =>
    useQuery({
        queryKey: ['automation'],
        queryFn: () => api.get<Trigger[]>('/automation'),
        staleTime: FRESH.list,
        refetchInterval: POLL.slow,
    });

export function useToggleTrigger() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: ({name, action}: { name: string; action: 'pause' | 'resume' }) =>
            api.post<Trigger>(`/automation/${name}/${action}`),
        onSuccess: (trigger) => {
            client.invalidateQueries({queryKey: ['automation']});
            client.invalidateQueries({queryKey: ['schedules']});
            if (trigger.dataset) client.invalidateQueries({queryKey: ['schedule', trigger.dataset]});
        },
    });
}

export function useBackfill() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (body: { dataset: string; partitions: string[] }) => api.post<{
            backfillId: string
        }>('/backfills', body),
        onSuccess: () => client.invalidateQueries({queryKey: ['runs']}),
    });
}

export function useTerminate() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (runId: string) => api.post<{ runId: string }>(`/runs/${runId}/terminate`),
        onSuccess: (_, runId) => {
            client.invalidateQueries({queryKey: ['run', runId]});
            client.invalidateQueries({queryKey: ['runs']});
        },
    });
}
