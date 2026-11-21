import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import type {AccessAudit, AccessGrant} from '../types';

export const useGrants = () => useQuery({
    queryKey: ['grants'],
    queryFn: () => api.get<AccessGrant[]>('/access/grants')
});

export const useAccessAudit = (enabled: boolean) =>
    useQuery({queryKey: ['access-audit'], queryFn: () => api.get<AccessAudit[]>('/access/audit?limit=20'), enabled});

export function useGrant() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (grant: {
            securable: string;
            principal: string;
            privilege: string
        }) => api.post<AccessGrant>('/access/grants', grant),
        onSettled: () => {
            client.invalidateQueries({queryKey: ['grants']});
            client.invalidateQueries({queryKey: ['access-audit']});
            client.invalidateQueries({queryKey: ['storage']});
        },
    });
}

export function useRevoke() {
    const client = useQueryClient();
    return useMutation({
        mutationFn: (grant: { securable: string; principal: string; privilege: string }) =>
            api.delete<void>(`/access/grants?${new URLSearchParams(grant)}`),
        onSettled: () => {
            client.invalidateQueries({queryKey: ['grants']});
            client.invalidateQueries({queryKey: ['access-audit']});
            client.invalidateQueries({queryKey: ['storage']});
        },
    });
}
