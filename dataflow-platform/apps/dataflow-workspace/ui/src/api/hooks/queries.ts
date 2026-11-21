import {queryOptions} from '@tanstack/react-query';
import {api} from '../client';
import type {DatasetSummary, PipelineGraph, RunDetail, TableDetail} from '../types';

export const queries = {
    dataset: (dataset: string) => queryOptions({
        queryKey: ['datasets', dataset],
        queryFn: () => api.get<DatasetSummary>(`/datasets/${dataset}`)
    }),
    graph: (dataset: string) => queryOptions({
        queryKey: ['graph', dataset],
        queryFn: () => api.get<PipelineGraph>(`/datasets/${dataset}/graph`)
    }),
    table: (dataset: string, table: string) => queryOptions({
        queryKey: ['table', dataset, table],
        queryFn: () => api.get<TableDetail>(`/catalog/${dataset}/${table}`)
    }),
    run: (runId: string) => queryOptions({
        queryKey: ['run', runId],
        queryFn: () => api.get<RunDetail>(`/runs/${runId}`)
    }),
};
