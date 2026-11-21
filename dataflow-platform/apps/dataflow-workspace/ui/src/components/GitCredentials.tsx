import {PasswordInput, TextInput} from '@mantine/core';
import {IconBrandGithub, IconExternalLink, IconKey, IconTrash} from '../icons';
import {useState} from 'react';
import {GITHUB_LINK, useCreateCredential, useCredentials, useDeleteCredential, useGitHub} from '../api/hooks';
import {formatAgo} from '../lib/format';
import {ErrorState} from './ErrorState';
import {FormModal} from './FormModal';
import {SettingRow, SettingsEmpty, SettingsLoading, SettingValue} from './settings/SettingRow';
import {Panel} from './Panel';
import {ICON} from '../lib/icons';
import {Action} from './actions/Action';
import {IconAction} from './actions/IconAction';
import {COLOR} from '../lib/colors';
import {notifyFailure} from './notify';

export function GitCredentials() {
    const {data, error, isLoading} = useCredentials();
    const remove = useDeleteCredential();
    const [adding, setAdding] = useState(false);
    const github = useGitHub();
    return (
        <Panel flush title="Credentials" actions={<>
            <Action size="sm" icon={IconBrandGithub} label="Link GitHub" href={GITHUB_LINK}/>
            <Action size="sm" icon={IconKey} label="Add token" onClick={() => setAdding(true)}/>
        </>}>
            {isLoading ? <SettingsLoading/> : error ? <ErrorState error={error}/> : !data?.length ?
                <SettingsEmpty>None</SettingsEmpty> :
                data.map((credential) => (
                    <SettingRow key={credential.id} label={<>
                        {credential.provider === 'github' ? <IconBrandGithub {...ICON}/> :
                            <IconKey {...ICON} color={COLOR.dimmed}/>}
                        {credential.name}
                    </>}>
                        <SettingValue muted>{credential.username} · {formatAgo(credential.createdAt)}</SettingValue>
                        {credential.provider === 'github' && github.data?.installUrl && (
                            <IconAction label="Choose repositories" icon={IconExternalLink} href={github.data.installUrl} external/>
                        )}
                        <IconAction label="Remove" icon={IconTrash} danger
                                    loading={remove.isPending && remove.variables === credential.id}
                                    onClick={() => remove.mutate(credential.id, {
                                        onError: notifyFailure('Not removed'),
                                    })}/>
                    </SettingRow>
                ))}
            <CredentialModal opened={adding} onClose={() => setAdding(false)}/>
        </Panel>
    );
}

function CredentialModal({opened, onClose}: { opened: boolean; onClose: () => void }) {
    const create = useCreateCredential();
    const [name, setName] = useState('');
    const [username, setUsername] = useState('');
    const [token, setToken] = useState('');
    const submit = () =>
        create.mutate(
            {name: name.trim(), username: username.trim(), token},
            {
                onSuccess: () => {
                    setToken('');
                    onClose();
                },
                onError: notifyFailure('Not added'),
            },
        );
    return (
        <FormModal opened={opened} onClose={onClose} title="Git credential" size="md" submit="Add"
                   submitDisabled={!name.trim() || !username.trim() || !token} loading={create.isPending}
                   onSubmit={submit}>
            <TextInput label="Name" placeholder="GitHub" value={name}
                       onChange={(event) => setName(event.currentTarget.value)} data-autofocus/>
            <TextInput label="Username" value={username} onChange={(event) => setUsername(event.currentTarget.value)}/>
            <PasswordInput label="Token" value={token} onChange={(event) => setToken(event.currentTarget.value)}/>
        </FormModal>
    );
}
