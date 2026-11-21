import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import type {AlertsView, CleanupItem, CleanupKind, Compute, ComputePolicy, EffectiveSettings, PlatformService, ProjectCompute, ServingHealth, Status, StorageView} from '../types';
import {FRESH, POLL} from '../../lib/timing';


export const useStatus = () => useQuery({
    queryKey: ['status'],
    queryFn: () => api.get<Status>('/status'),
    refetchInterval: POLL.background
});

export const useServingHealth = () => useQuery({
    queryKey: ['services', 'serving-health'],
    queryFn: () => api.get<ServingHealth>('/services/serving-health'),
    refetchInterval: POLL.background,
});

export const useStorage = () => useQuery({
    queryKey: ['storage'],
    queryFn: () => api.get<StorageView>('/storage'),
    refetchInterval: POLL.background,
});

export function useSyncStorage() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: () => api.post('/storage/sync'),
        onSettled: () => client.invalidateQueries({queryKey: ['storage']}),
    });
}

export const useEffectiveSettings = () =>
    useQuery({
        queryKey: ['settings-effective'],
        queryFn: () => api.get<EffectiveSettings>('/settings/effective'),
        staleTime: FRESH.minute
    });

export const useCompute = () => useQuery({
    queryKey: ['compute'],
    queryFn: () => api.get<ProjectCompute[]>('/compute'),
    staleTime: FRESH.minute
});

export const useComputePolicy = () => useQuery({
    queryKey: ['compute', 'policy'],
    queryFn: () => api.get<ComputePolicy>('/compute/policy'),
    staleTime: Infinity
});

export function useUpdateCompute() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: ({folderId, target, compute}: { folderId: string; target: string; compute: Compute }) =>
            api.put<ProjectCompute>(`/compute/${folderId}/${encodeURIComponent(target)}`, compute),
        onSuccess: () => ['compute', 'folder', 'folder-file', 'folder-log'].forEach((key) => client.invalidateQueries({queryKey: [key]})),
    });
}

export const useHousekeeping = () => useQuery({
    queryKey: ['housekeeping'],
    queryFn: () => api.get<CleanupItem[]>('/housekeeping')
});

export function useCleanUp() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (kinds: CleanupKind[]) =>
            api.post<{
                lakeTask?: { taskId: string; runId: string } | null;
                lakeLocations: number;
                removed: number
            }>('/housekeeping', {kinds}),
        onSuccess: () => client.invalidateQueries({queryKey: ['housekeeping']}),
    });
}

export const useServices = () => useQuery({
    queryKey: ['services'],
    queryFn: () => api.get<PlatformService[]>('/services'),
    refetchInterval: POLL.slow
});

export function useReloadTower() {
    return useMutation({mutationFn: () => api.post<{ reloaded: boolean }>('/tower/reload')});
}

export const useAlerts = () => useQuery({
    queryKey: ['alerts'],
    queryFn: () => api.get<AlertsView>('/alerts'),
    refetchInterval: POLL.slow
});
