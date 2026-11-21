import {useQuery} from '@tanstack/react-query';
import {api} from '../client';
import type {Board, BoardData, BoardOverview, Dashboard} from '../types';
import {FRESH, POLL} from '../../lib/timing';

export const useDashboards = () => useQuery({
    queryKey: ['dashboards'],
    queryFn: () => api.get<Dashboard[]>('/dashboards')
});

export const useBoard = (dataset?: string) =>
    useQuery({
        queryKey: ['board', dataset],
        queryFn: () => api.get<Board>(`/boards/${dataset}`),
        enabled: !!dataset,
        staleTime: FRESH.minute
    });

export const useBoardOverview = (dataset?: string) =>
    useQuery({
        queryKey: ['board-overview', dataset],
        queryFn: () => api.get<BoardOverview>(`/boards/${dataset}/overview`),
        enabled: !!dataset,
        refetchInterval: POLL.slow
    });

export function useBoardChart(dataset: string, table: string, chart: string, range: string, filters: Record<string, string>) {
    const query = new URLSearchParams({range});
    Object.entries(filters).forEach(([column, value]) => query.set(`filter.${column}`, value));
    return useQuery({
        queryKey: ['board-chart', dataset, table, chart, query.toString()],
        queryFn: () => api.get<BoardData>(`/boards/${dataset}/${encodeURIComponent(table)}/${encodeURIComponent(chart)}?${query}`),
        staleTime: FRESH.minute,
        placeholderData: (previous) => previous,
    });
}
