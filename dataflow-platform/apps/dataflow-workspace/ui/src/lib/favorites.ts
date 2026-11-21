import type {Favorite, SavedQuery} from '../api/types';
import {links} from './links';

export type FavoriteEntry = Favorite & { to: string; label: string };

export function favoriteEntry(favorite: Favorite, queries: SavedQuery[]): FavoriteEntry | undefined {
    switch (favorite.kind) {
        case 'PIPELINE':
            return {...favorite, to: links.pipeline(favorite.ref), label: favorite.ref};
        case 'TABLE': {
            const dot = favorite.ref.indexOf('.');
            return dot < 0 ? undefined
                : {...favorite, to: links.table(favorite.ref.slice(0, dot), favorite.ref.slice(dot + 1)), label: favorite.ref};
        }
        case 'SERVED':
            return {...favorite, to: links.served(favorite.ref), label: `serving.${favorite.ref}`};
        case 'QUERY': {
            const query = queries.find((candidate) => candidate.id === favorite.ref);
            return query ? {...favorite, to: links.savedQuery(query.id), label: query.name} : undefined;
        }
    }
}
