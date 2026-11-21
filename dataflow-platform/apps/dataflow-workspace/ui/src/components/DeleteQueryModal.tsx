import {Text} from '@mantine/core';
import {useDeleteSavedQuery} from '../api/hooks';
import type {SavedQuery} from '../api/types';
import {FormModal} from './FormModal';
import {notifyFailure} from './notify';

export function DeleteQueryModal({query, onClose}: { query?: SavedQuery; onClose: () => void }) {
    const remove = useDeleteSavedQuery();
    return (
        <FormModal opened={!!query} onClose={onClose} title="Delete saved query?" submit="Delete" danger
                   loading={remove.isPending}
                   onSubmit={() => query && remove.mutate(query.id, {
                       onSuccess: onClose,
                       onError: notifyFailure('Not deleted'),
                   })}>
            <Text size="sm">{query?.name}{query?.shared ? ' is shared; it disappears for everyone.' : ''}</Text>
        </FormModal>
    );
}
