import {IconStar, IconStarFilled} from '../icons';
import {useFavorite} from '../api/hooks';
import type {FavoriteKind} from '../api/types';
import {IconAction, type IconActionSize} from './actions/IconAction';
import {notifyFailure} from './notify';

export function FavoriteButton({kind, reference, size}: { kind: FavoriteKind; reference: string; size?: IconActionSize }) {
    const {on, toggle} = useFavorite(kind, reference);
    return (
        <IconAction label={on ? 'Remove from favorites' : 'Add to favorites'} icon={on ? IconStarFilled : IconStar}
                    active={on} size={size} loading={toggle.isPending}
                    onClick={() => toggle.mutate(undefined, {
                        onError: notifyFailure(),
                    })}/>
    );
}
