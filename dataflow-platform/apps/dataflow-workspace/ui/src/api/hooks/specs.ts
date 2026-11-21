import {keepPreviousData, useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import type {Spec} from '../../lib/specModel';
import {api} from '../client';
import type {DraftCheck, Finding, StoredSpec, TaskKind, TaskState} from '../types';
import {useFolders, useReleases} from './folders';
import {POLL} from '../../lib/timing';
import {links} from '../../lib/links';

export const useSpec = (dataset?: string) =>
    useQuery({
        queryKey: ['spec', dataset],
        queryFn: () => api.get<StoredSpec>(`/specs/${dataset}`),
        enabled: !!dataset
    });

export function useTemplate() {
    const client = useQueryClient();
    return (dataset: string) => client.fetchQuery({
        queryKey: ['template', dataset],
        queryFn: () => api.get<{ content: string }>(`/specs/template?dataset=${encodeURIComponent(dataset)}`),
    }).then((template) => template.content);
}

export const useDraftCheck = (dataset: string | undefined, content: string, enabled = true) =>
    useQuery({
        queryKey: ['check', dataset, content],
        queryFn: () => api.post<DraftCheck>('/specs/check', {dataset, content}),
        enabled: enabled && content.trim().length > 0,
        placeholderData: (previous) => previous,
        staleTime: Infinity,
    });

const TASK_DONE = ['SUCCESS', 'FAILURE', 'CANCELED'];

export const taskFinished = (status?: string) => !!status && TASK_DONE.includes(status);

export function useStartTask() {
    return useMutation({
        mutationFn: (body: {
            kind: TaskKind;
            dataset: string;
            content?: string;
            folderId?: string;
            source?: string
        }) => api.post<{ taskId: string; runId: string }>('/tasks', body),
    });
}

export const useUploadFile = () => useMutation({
    mutationFn: (file: File) => api.upload<{ key: string }>('/uploads', file),
});

export const useTask = (task?: { taskId: string; runId: string }) =>
    useQuery({
        queryKey: ['task', task?.taskId],
        queryFn: () => api.get<TaskState>(`/tasks/${task!.taskId}?run=${task!.runId}`),
        enabled: !!task,
        refetchInterval: (query) => (taskFinished(query.state.data?.status) ? false : POLL.running),
    });

export function useEditLink(dataset?: string) {
    const {data: releases} = useReleases();
    const {data: folders} = useFolders();
    const release = releases?.find((candidate) => dataset && candidate.datasets.includes(dataset));
    const folder = folders?.find((candidate) => release && candidate.project === release.project);
    const file = dataset ? `pipelines/${dataset}/pipeline.yml` : undefined;
    return {
        project: release?.project,
        folderId: folder?.id,
        file,
        to: folder && file ? links.workspace(`${folder.path}/${file}`) : undefined,
    };
}

export const useSpecTree = (content: string, enabled: boolean) =>
    useQuery({
        queryKey: ['spec-tree', content],
        queryFn: () => api.post<Spec>('/specs/tree', {content}),
        enabled,
        staleTime: Infinity,
        retry: false,
    });

export const useTypedSpecTree = (content: string) =>
    useQuery({
        queryKey: ['spec-tree', content],
        queryFn: () => api.post<Spec>('/specs/tree', {content}),
        placeholderData: keepPreviousData,
        staleTime: Infinity,
        retry: false,
    });

export const usePreflight = () =>
    useMutation({
        mutationFn: (request: { dataset: string; content: string; folderId: string }) =>
            api.post<Finding[]>('/specs/preflight', request),
    });
