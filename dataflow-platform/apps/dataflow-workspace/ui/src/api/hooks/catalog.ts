import {useMutation, useQueries, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import {queries} from './queries';
import type {CatalogTree, ColumnUsage, Commit, DatasetSummary, DeltaDetails, FlowCell, Lineage, Partitions, QualityRule, QueryResult, RescuedKey, RuleCount, RunRuntime, Schema, SqlFunction, TableChanges, TableProfile, TableUsage} from '../types';
import {FRESH, POLL} from '../../lib/timing';

export const useCatalogUsage = () => useQuery({
    queryKey: ['catalog-usage'],
    queryFn: () => api.get<TableUsage[]>('/catalog/usage'),
    staleTime: FRESH.usage
});

export const useColumnUsage = (table?: string | null) => useQuery({
    queryKey: ['column-usage', table],
    queryFn: () => api.get<ColumnUsage[]>(`/catalog/usage/columns?table=${encodeURIComponent(table!)}`),
    enabled: !!table,
    staleTime: FRESH.usage
});

export const useDatasets = () =>
    useQuery({queryKey: ['datasets'], queryFn: () => api.get<DatasetSummary[]>('/datasets'), refetchInterval: POLL.list});

const PREFETCH_FRESH = 10_000;


export function usePrefetch() {
    const client = useQueryClient();
    return {
        pipeline: (dataset: string) => {
            client.prefetchQuery({...queries.dataset(dataset), staleTime: PREFETCH_FRESH});
            client.prefetchQuery({...queries.graph(dataset), staleTime: PREFETCH_FRESH});
        },
        table: (dataset: string, table: string) => client.prefetchQuery({
            ...queries.table(dataset, table),
            staleTime: PREFETCH_FRESH
        }),
        run: (runId: string) => client.prefetchQuery({...queries.run(runId), staleTime: PREFETCH_FRESH}),
    };
}

export const useDataset = (dataset: string) => useQuery({...queries.dataset(dataset), refetchInterval: POLL.detail});

export const useGraph = (dataset: string) => useQuery({...queries.graph(dataset), refetchInterval: POLL.detail});

export const useLineage = (dataset: string) =>
    useQuery({queryKey: ['lineage', dataset], queryFn: () => api.get<Lineage>(`/datasets/${dataset}/lineage`)});

export const usePartitions = (dataset: string, enabled = true) =>
    useQuery({
        queryKey: ['partitions', dataset],
        queryFn: () => api.get<Partitions>(`/datasets/${dataset}/partitions`),
        enabled,
        refetchInterval: POLL.list
    });

export const useRuntime = (dataset: string) =>
    useQuery({
        queryKey: ['runtime', dataset],
        queryFn: async () => (await api.get<RunRuntime | undefined>(`/datasets/${dataset}/runtime`)) ?? null,
        retry: false,
        staleTime: FRESH.minute
    });

const flowCells = (dataset: string, runs: number) => ({
    queryKey: ['flow-cells', dataset, runs],
    queryFn: () => api.get<FlowCell[]>(`/datasets/${dataset}/flows?runs=${runs}`),
    refetchInterval: POLL.list,
});

export const useFlowCells = (dataset: string, runs: number) => useQuery(flowCells(dataset, runs));

export const useFlowCellsOf = (datasets: string[], runs: number) =>
    useQueries({
        queries: datasets.map((dataset) => flowCells(dataset, runs)),
        combine: (results) => ({cells: results.flatMap((result) => result.data ?? []), loading: results.some((result) => result.isLoading)}),
    });

export const useCatalog = () => useQuery({
    queryKey: ['catalog'],
    queryFn: () => api.get<CatalogTree>('/catalog'),
    refetchInterval: POLL.background
});

export const useSchema = () => useQuery({
    queryKey: ['schema'],
    queryFn: () => api.get<Schema>('/catalog/schema'),
    staleTime: FRESH.minute
});

export const useSqlFunctions = () =>
    useQuery({
        queryKey: ['sql-functions'],
        queryFn: () => api.get<SqlFunction[]>('/sql/functions'),
        staleTime: Infinity
    });

export const useTable = (dataset: string, table: string) => useQuery(queries.table(dataset, table));

export const useSample = (dataset: string, table: string, enabled: boolean) =>
    useQuery({
        queryKey: ['sample', dataset, table],
        queryFn: () => api.get<QueryResult>(`/catalog/${dataset}/${table}/sample?limit=200`),
        enabled,
    });

export const useTableHistory = (dataset: string, table: string, enabled: boolean) =>
    useQuery({
        queryKey: ['history', dataset, table],
        queryFn: () => api.get<Commit[]>(`/catalog/${dataset}/${table}/history`),
        enabled
    });

export const useRescued = (dataset: string, enabled: boolean) =>
    useQuery({
        queryKey: ['rescued', dataset],
        queryFn: () => api.get<RescuedKey[]>(`/catalog/${dataset}/bronze/rescued`),
        enabled
    });

export const useTableChanges = (dataset: string, table: string, range: {from: number; to: number}) =>
    useQuery({
        queryKey: ['changes', dataset, table, range.from, range.to],
        queryFn: () => api.get<TableChanges>(`/catalog/${dataset}/${table}/changes?from=${range.from}&to=${range.to}`),
    });

export const useProfile = (dataset: string, table: string) =>
    useQuery({
        queryKey: ['profile', dataset, table],
        queryFn: () => api.get<TableProfile>(`/catalog/${dataset}/${table}/profile`)
    });

export const useDeltaDetails = (dataset: string, table: string) =>
    useQuery({
        queryKey: ['details', dataset, table],
        queryFn: () => api.get<DeltaDetails>(`/catalog/${dataset}/${table}/details`)
    });

export const useQuarantineReasons = (dataset: string, enabled: boolean) =>
    useQuery({
        queryKey: ['reasons', dataset],
        queryFn: () => api.get<RuleCount[]>(`/catalog/${dataset}/quarantine/reasons`),
        enabled
    });

export const useServedProfile = (table: string) =>
    useQuery({
        queryKey: ['profile', 'serving', table],
        queryFn: () => api.get<TableProfile>(`/serving/${table}/profile`)
    });

export const useServedSample = (table: string) =>
    useQuery({queryKey: ['served', table], queryFn: () => api.get<QueryResult>(`/serving/${table}/sample?limit=200`)});

export const useQuality = (dataset?: string) =>
    useQuery({
        queryKey: ['quality', dataset ?? 'all'],
        queryFn: () => api.get<QualityRule[]>(`/quality${dataset ? `?dataset=${dataset}` : ''}`)
    });

export const useServedColumns = (table: string) =>
    useQuery({queryKey: ['served-columns', table], queryFn: () => api.get<QueryResult>(`/serving/${table}/columns`)});

export const useLanding = (dataset: string, enabled: boolean) =>
    useQuery({
        queryKey: ['landing', dataset],
        queryFn: () => api.get<QueryResult>(`/catalog/${dataset}/landing/files`),
        enabled
    });

export function useSyncCatalog() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: () => api.post<{ views: number }>('/catalog/sync'),
        onSuccess: () => {
            client.invalidateQueries({queryKey: ['catalog']});
            client.invalidateQueries({queryKey: ['schema']});
        },
    });
}
