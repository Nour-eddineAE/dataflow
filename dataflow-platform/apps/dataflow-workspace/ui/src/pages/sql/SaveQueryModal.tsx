import {Checkbox, TextInput} from '@mantine/core';
import {useState} from 'react';
import {FormModal} from '../../components/FormModal';

export type SaveDraft = { name: string; shared: boolean };

const BLANK: SaveDraft = {name: '', shared: false};

export function SaveQueryModal({draft, loading, onChange, onClose, onSubmit}: {
    draft: SaveDraft | null;
    loading: boolean;
    onChange: (draft: SaveDraft) => void;
    onClose: () => void;
    onSubmit: (draft: SaveDraft) => void;
}) {
    const [last, setLast] = useState(BLANK);
    if (draft && draft !== last) setLast(draft);
    const shown = draft ?? last;
    return (
        <FormModal opened={!!draft} onClose={onClose} title="Save query" submit="Save" loading={loading}
                   submitDisabled={!shown.name.trim()} onSubmit={() => onSubmit(shown)}>
            <TextInput label="Name" value={shown.name} data-autofocus
                       onChange={(event) => onChange({...shown, name: event.currentTarget.value})}/>
            <Checkbox label="Shared with everyone" checked={shown.shared}
                      onChange={(event) => onChange({...shown, shared: event.currentTarget.checked})}/>
        </FormModal>
    );
}
