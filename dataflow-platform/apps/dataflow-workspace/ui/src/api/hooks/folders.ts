import {useMutation, useQueries, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import type {
    FileContent,
    FolderSummary,
    FolderTree,
    GitCredential,
    GitHubSettings,
    ReleaseRecord,
    ReleaseView,
    SpecCommit,
    TransformationInfo,
} from '../types';
import {FRESH, POLL} from '../../lib/timing';

const path = (file: string) => `path=${encodeURIComponent(file)}`;

export const useFolders = () =>
    useQuery({queryKey: ['folders'], queryFn: () => api.get<FolderSummary[]>('/folders'), refetchInterval: POLL.background});

export const useMe = () =>
    useQuery({
        queryKey: ['me'],
        queryFn: () => api.get<{ id: string; name: string; email: string; groups: string[]; admin: boolean }>('/me'),
        staleTime: Infinity
    });

export const useFolderTrees = (ids: string[]) =>
    useQueries({
        queries: ids.map((id) => ({
            queryKey: ['folder', id],
            queryFn: () => api.get<FolderTree>(`/folders/${id}/tree`)
        })),
    });

export const useFolderFile = (id?: string, file?: string) =>
    useQuery({
        queryKey: ['folder-file', id, file],
        queryFn: () => api.get<FileContent>(`/folders/${id}/file?${path(file!)}`),
        enabled: !!id && !!file,
        retry: false,
    });

export const useFolderLog = (id?: string) =>
    useQuery({queryKey: ['folder-log', id], queryFn: () => api.get<SpecCommit[]>(`/folders/${id}/log`), enabled: !!id});

export const useBranches = (id?: string) =>
    useQuery({
        queryKey: ['folder-branches', id],
        queryFn: () => api.get<string[]>(`/folders/${id}/branches`),
        enabled: !!id
    });

function useFolderMutation<Body, Result>(id: string, run: (body: Body) => Promise<Result>) {
    const client = useQueryClient();
    return useMutation({
        mutationFn: run,
        onSuccess: (result) => {
            if (result && typeof result === 'object' && 'files' in (result as object)) client.setQueryData(['folder', id], result);
            client.invalidateQueries({queryKey: ['folder', id]});
            client.invalidateQueries({queryKey: ['folder-file', id]});
            client.invalidateQueries({queryKey: ['folder-log', id]});
            client.invalidateQueries({queryKey: ['folder-branches', id]});
            client.invalidateQueries({queryKey: ['folders']});
        },
    });
}

export const useSaveFile = (id: string) =>
    useFolderMutation(id, ({file, content}: { file: string; content: string }) =>
        api.put<FileContent>(`/folders/${id}/file?${path(file)}`, {content}));

export const useEditFile = (id: string) =>
    useFolderMutation(id, async ({file, change}: { file: string; change: (content: string) => string }) => {
        const current = await api.get<FileContent>(`/folders/${id}/file?${path(file)}`);
        return api.put<FileContent>(`/folders/${id}/file?${path(file)}`, {content: change(current.content ?? '')});
    });

export const useRemoveFile = (id: string) =>
    useFolderMutation(id, (file: string) => api.delete<void>(`/folders/${id}/file?${path(file)}`));

export const useDiscardFile = (id: string) =>
    useFolderMutation(id, (file: string) => api.post<void>(`/folders/${id}/discard?${path(file)}`));

export const useCheckout = (id: string) =>
    useFolderMutation(id, (branch: string) => api.post<FolderTree>(`/folders/${id}/checkout`, {branch}));

export const usePull = (id: string) => useFolderMutation(id, () => api.post<FolderTree>(`/folders/${id}/pull`));

export const usePush = (id: string) => useFolderMutation(id, () => api.post<FolderTree>(`/folders/${id}/push`));

export const useCommitFolder = (id: string) =>
    useFolderMutation(id, (message: string) => api.post<SpecCommit>(`/folders/${id}/commit`, {message}));

export function useCreateFolder() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (body: {
            name: string;
            remoteUrl: string;
            branch: string;
            shared: boolean;
            credentialId?: string;
            team?: string
        }) =>
            api.post<{ id: string; path: string }>('/folders', body),
        onSuccess: () => client.invalidateQueries({queryKey: ['folders']}),
    });
}

export function useDeleteFolder() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => api.delete<void>(`/folders/${id}`),
        onSuccess: () => client.invalidateQueries({queryKey: ['folders']}),
    });
}

export const useCredentials = () => useQuery({
    queryKey: ['credentials'],
    queryFn: () => api.get<GitCredential[]>('/git/credentials')
});

export const useGitHub = () => useQuery({
    queryKey: ['github'],
    queryFn: () => api.get<GitHubSettings>('/git/github'),
    staleTime: Infinity
});

export const GITHUB_LINK = '/api/git/github/link';

export function useCreateCredential() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (body: {
            name: string;
            username: string;
            token: string
        }) => api.post<GitCredential>('/git/credentials', body),
        onSuccess: () => client.invalidateQueries({queryKey: ['credentials']}),
    });
}

export function useDeleteCredential() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => api.delete<void>(`/git/credentials/${id}`),
        onSuccess: () => client.invalidateQueries({queryKey: ['credentials']}),
    });
}

export const useReleases = () => useQuery({
    queryKey: ['releases'],
    queryFn: () => api.get<ReleaseView[]>('/releases'),
    refetchInterval: POLL.background
});

export const useReleaseHistory = (project?: string) =>
    useQuery({
        queryKey: ['release-history', project],
        queryFn: () => api.get<ReleaseRecord[]>(`/releases/${project}`),
        enabled: !!project
    });

export function useRelease() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (body: { folderId: string; target: string }) => api.post<ReleaseView>('/releases', body),
        onSuccess: () => {
            ['releases', 'release-history', 'datasets', 'catalog', 'graph', 'lineage', 'quality', 'compute'].forEach((key) =>
                client.invalidateQueries({queryKey: [key]}));
        },
    });
}

export const useTransformations = (folderId: string | undefined) =>
    useQuery({
        queryKey: ['transformations', folderId],
        queryFn: () => api.get<TransformationInfo[]>(`/folders/${folderId}/transformations`),
        enabled: !!folderId,
        staleTime: FRESH.list,
    });
