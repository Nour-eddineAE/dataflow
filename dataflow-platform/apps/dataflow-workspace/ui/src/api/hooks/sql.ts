import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import type {QueryAlert, QueryAlertComparison, QueryResult, QueryRun, SavedQuery} from '../types';
import {POLL} from '../../lib/timing';

export function useRunSql() {
    return useMutation({mutationFn: (body: { sql: string; limit: number }) => api.post<QueryResult>('/sql', body)});
}

export const useQueryHistory = () => useQuery({
    queryKey: ['query-history'],
    queryFn: () => api.get<QueryRun[]>('/sql/history'),
    refetchInterval: POLL.background,
});

export const useSavedQueries = () => useQuery({
    queryKey: ['saved-queries'],
    queryFn: () => api.get<SavedQuery[]>('/sql/saved')
});

export function useSaveQuery() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (query: {
            id?: string;
            name: string;
            sql: string;
            shared: boolean
        }) => api.post<SavedQuery>('/sql/saved', query),
        onSuccess: () => client.invalidateQueries({queryKey: ['saved-queries']}),
    });
}

const QUERY_ALERTS = ['query-alerts'];

export const useQueryAlerts = () => useQuery({
    queryKey: QUERY_ALERTS,
    queryFn: () => api.get<QueryAlert[]>('/sql/alerts'),
    refetchInterval: POLL.slow,
});

export function useSaveQueryAlert() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: ({queryId, ...settings}: {
            queryId: string;
            everyMinutes: number;
            comparison: QueryAlertComparison;
            threshold: number | null
        }) => api.put<void>(`/sql/saved/${queryId}/alert`, settings),
        onSuccess: () => client.invalidateQueries({queryKey: QUERY_ALERTS}),
    });
}

export function useDeleteQueryAlert() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (queryId: string) => api.delete<void>(`/sql/saved/${queryId}/alert`),
        onSuccess: () => client.invalidateQueries({queryKey: QUERY_ALERTS}),
    });
}

export function useDeleteSavedQuery() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => api.delete<void>(`/sql/saved/${id}`),
        onSuccess: () => client.invalidateQueries({queryKey: ['saved-queries']}),
    });
}
