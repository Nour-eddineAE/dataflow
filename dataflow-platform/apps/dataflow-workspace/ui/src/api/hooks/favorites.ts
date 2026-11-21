import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from '../client';
import type {Favorite, FavoriteKind} from '../types';

const KEY = ['favorites'];

const path = (kind: FavoriteKind, ref: string) => `/favorites/${kind.toLowerCase()}/${encodeURIComponent(ref)}`;

export const useFavorites = () => useQuery({queryKey: KEY, queryFn: () => api.get<Favorite[]>('/favorites')});

export function useFavorite(kind: FavoriteKind, ref: string) {
    const client = useQueryClient();
    const {data} = useFavorites();
    const on = !!data?.some((favorite) => favorite.kind === kind && favorite.ref === ref);
    const toggle = useMutation({
        mutationFn: async () => {
            if (on) await api.delete<void>(path(kind, ref));
            else await api.put<Favorite>(path(kind, ref), {});
        },
        onSuccess: () => client.invalidateQueries({queryKey: KEY}),
    });
    return {on, toggle};
}
