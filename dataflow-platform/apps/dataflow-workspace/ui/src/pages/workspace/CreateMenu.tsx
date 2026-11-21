import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {FileButton, Group, Loader, Menu, ScrollArea, SegmentedControl, Select, Stack, Table, Text, TextInput} from '@mantine/core';
import {IconBrandGit, IconBrandGithub, IconPlus, IconCode, IconFile, IconRoute, IconUpload} from '../../icons';
import {useState} from 'react';
import {GITHUB_LINK, useCreateFolder, useCredentials, useMe, useSaveFile, useStartTask, useTask, useTemplate, useUploadFile} from '../../api/hooks';
import type {FolderSummary, InferResult} from '../../api/types';
import {FormModal} from '../../components/FormModal';
import {datasetFromSource, isSource} from '../../lib/sourceName';
import {className, TRANSFORMATION_NAME, transformationTemplate} from '../../lib/transformationTemplate';
import {ICON} from '../../lib/icons';
import {Action} from '../../components/actions/Action';
import {IconAction} from '../../components/actions/IconAction';
import {NAME} from '../../lib/names';
import {notifyFailure} from '../../components/notify';
import {COLOR} from '../../lib/colors';

export type CreateKind = 'git-folder' | 'pipeline' | 'transformation' | 'file';

const DATASET = new RegExp(`^${NAME}$`);

type Task = { taskId: string; runId: string };

export function CreateMenu({folder, within, shared, request, primary = true, onDone, onCreated}: {
    folder?: FolderSummary;
    primary?: boolean;
    within?: string;
    shared: boolean;
    request?: CreateKind;
    onDone: () => void;
    onCreated: (path: string) => void;
}) {
    const [chosen, setChosen] = useState<CreateKind>();
    const open = request ?? chosen;
    const close = () => {
        setChosen(undefined);
        onDone();
    };
    return (
        <>
            <Menu position="bottom-end" width={180}>
                <Menu.Target>
                    <ToolbarButton label="New" icon={IconPlus} menu primary={primary}/>
                </Menu.Target>
                <Menu.Dropdown>
                    <Menu.Item leftSection={<IconBrandGit {...ICON}/>} disabled={!!folder}
                               onClick={() => setChosen('git-folder')}>Git folder</Menu.Item>
                    <Menu.Item leftSection={<IconRoute {...ICON}/>} disabled={!folder}
                               onClick={() => setChosen('pipeline')}>Pipeline</Menu.Item>
                    <Menu.Item leftSection={<IconCode {...ICON}/>} disabled={!folder}
                               onClick={() => setChosen('transformation')}>Transformation</Menu.Item>
                    <Menu.Item leftSection={<IconFile {...ICON}/>} disabled={!folder}
                               onClick={() => setChosen('file')}>File</Menu.Item>
                </Menu.Dropdown>
            </Menu>
            <ConnectModal opened={open === 'git-folder'} shared={shared} onClose={close} onCreated={onCreated}/>
            {folder &&
                <NewPipelineModal folder={folder} opened={open === 'pipeline'} onClose={close} onCreated={onCreated}/>}
            {folder && <NewFileModal folder={folder} within={within} opened={open === 'file'} onClose={close}
                                     onCreated={onCreated}/>}
            {folder && <NewTransformationModal folder={folder} opened={open === 'transformation'} onClose={close}
                                               onCreated={onCreated}/>}
        </>
    );
}

const nameOf = (folder: FolderSummary) => folder.path.split('/').pop() ?? folder.path;

function ConnectModal({opened, shared, onClose, onCreated}: {
    opened: boolean;
    shared: boolean;
    onClose: () => void;
    onCreated: (path: string) => void
}) {
    const create = useCreateFolder();
    const {data: credentials} = useCredentials();
    const {data: me} = useMe();
    const [remoteUrl, setRemoteUrl] = useState('');
    const [name, setName] = useState('');
    const [branch, setBranch] = useState('main');
    const [credentialId, setCredentialId] = useState<string | null>(null);
    const [team, setTeam] = useState<string | null>(null);
    const teams = me?.groups ?? [];
    const submit = () =>
        create.mutate({
            name: name.trim(),
            remoteUrl: remoteUrl.trim(),
            branch: branch.trim(),
            shared,
            credentialId: credentialId ?? undefined,
            team: team ?? undefined
        }, {
            onSuccess: (folder) => {
                onClose();
                onCreated(folder.path);
            },
            onError: notifyFailure('Not created'),
        });
    return (
        <FormModal opened={opened} onClose={onClose} title={`New Git folder in ${shared ? 'Shared' : 'Home'}`} size="md"
                   submit="Create" submitDisabled={!name.trim() || !remoteUrl.trim()}
                   submitHint={!remoteUrl.trim() ? 'Enter the remote URL' : 'Name the folder'}
                   loading={create.isPending} onSubmit={submit}>
            <TextInput label="Remote" placeholder="https://github.com/acme/analytics.git" value={remoteUrl}
                       data-autofocus
                       onChange={(event) => {
                           const value = event.currentTarget.value;
                           setRemoteUrl(value);
                           setName(value.split('/').pop()?.replace(/\.git$/, '') ?? '');
                       }}/>
            <Group grow>
                <TextInput label="Name" value={name} onChange={(event) => setName(event.currentTarget.value)}/>
                <TextInput label="Branch" value={branch} onChange={(event) => setBranch(event.currentTarget.value)}/>
            </Group>
            <Group gap="xs" align="flex-end" wrap="nowrap">
                <Select style={{flex: 1}} label="Credential" placeholder="None" clearable value={credentialId}
                        onChange={setCredentialId}
                        data={(credentials ?? []).map((credential) => ({
                            value: credential.id,
                            label: credential.name
                        }))}/>
                <IconAction label="Link GitHub" icon={IconBrandGithub} outlined size={32} href={GITHUB_LINK}/>
            </Group>
            {shared && teams.length > 0 && (
                <Select label="Team" description="Manages this folder with you" placeholder="Only me" clearable
                        value={team} onChange={setTeam} data={teams}/>
            )}
        </FormModal>
    );
}

function NewPipelineModal({folder, opened, onClose, onCreated}: {
    folder: FolderSummary;
    opened: boolean;
    onClose: () => void;
    onCreated: (path: string) => void
}) {
    const save = useSaveFile(folder.id);
    const template = useTemplate();
    const [mode, setMode] = useState<'blank' | 'source'>('blank');
    const [name, setName] = useState('');
    const [task, setTask] = useState<Task>();
    const {data: state} = useTask(task);
    const inference = state?.result?.status === 'done' ? (state.result.result as InferResult) : undefined;
    const [busy, setBusy] = useState(false);
    const valid = DATASET.test(name);
    const create = async (content: () => Promise<string>) => {
        setBusy(true);
        try {
            const file = `pipelines/${name}/pipeline.yml`;
            await save.mutateAsync({file, content: await content()});
            onClose();
            onCreated(`${folder.path}/${file}`);
        } catch (error) {
            notifyFailure('Not created')(error as Error);
        } finally {
            setBusy(false);
        }
    };
    const submit = () =>
        mode === 'blank'
            ? create(() => template(name))
            : inference && create(async () => inference.spec.replace(`dataset: ${inference.dataset}`, `dataset: ${name}`));
    const reset = () => {
        setName('');
        setMode('blank');
        setTask(undefined);
    };
    return (
        <FormModal opened={opened} onClose={onClose} title={`New pipeline in ${nameOf(folder)}`} size={mode === 'source' ? 'lg' : 'md'}
                   submit="Create" submitDisabled={!valid || (mode === 'source' && !inference)}
                   submitHint={!name ? 'Name the dataset' : valid ? 'Sample the source first' : undefined}
                   loading={busy} onSubmit={submit}
                   onClosed={reset}>
            <SegmentedControl fullWidth value={mode} onChange={(value) => {
                setMode(value as 'blank' | 'source');
                setTask(undefined);
            }}
                              data={[{value: 'blank', label: 'Blank'}, {value: 'source', label: 'From source'}]}/>
            {mode === 'blank' ? (
                <TextInput label="Dataset" placeholder="sensor_readings" value={name} data-autofocus
                           onChange={(event) => setName(event.currentTarget.value.trim())}
                           error={name && !valid ? 'a-z, 0-9 and _, starting with a letter' : undefined}/>
            ) : (
                <FromSource name={name} setName={setName} valid={valid} task={task} onTask={setTask}
                            inference={inference}/>
            )}
        </FormModal>
    );
}

function FromSource({name, setName, valid, task, onTask, inference}: {
    name: string;
    setName: (name: string) => void;
    valid: boolean;
    task?: Task;
    onTask: (task?: Task) => void;
    inference?: InferResult;
}) {
    const start = useStartTask();
    const upload = useUploadFile();
    const [url, setUrl] = useState('');
    const [named, setNamed] = useState(false);
    const {data: state} = useTask(task);
    const finished = state?.result;
    const running = !!task && !finished && !['FAILURE', 'CANCELED'].includes(state?.status ?? '');
    const uploadFile = (file: File | null) => {
        if (!file) return;
        upload.mutate(file, {
            onSuccess: ({key}) => {
                setUrl(key);
                onTask(undefined);
                if (!named) setName(datasetFromSource(file.name));
            },
            onError: notifyFailure('Not uploaded'),
        });
    };
    const sample = () =>
        start.mutate({kind: 'infer', dataset: name, source: url.trim()}, {
            onSuccess: onTask,
            onError: notifyFailure('Not sampled'),
        });
    return (
        <>
            <Group gap="xs" align="flex-end" wrap="nowrap">
                <TextInput style={{flex: 1}} label="Source" placeholder="https://…/file.csv or lake path" value={url}
                           data-autofocus
                           onChange={(event) => {
                               const value = event.currentTarget.value;
                               setUrl(value);
                               onTask(undefined);
                               if (!named) setName(datasetFromSource(value));
                           }}/>
                <FileButton onChange={uploadFile}>
                    {(button) => (
                        <Action icon={IconUpload} label="Upload" loading={upload.isPending}
                                onClick={button.onClick}/>
                    )}
                </FileButton>
            </Group>
            <Group gap="xs" align="flex-end" wrap="nowrap">
                <TextInput style={{flex: 1}} label="Dataset" placeholder="sensor_readings" value={name}
                           onChange={(event) => {
                               setNamed(true);
                               setName(event.currentTarget.value.trim());
                           }}
                           error={name && !valid ? 'a-z, 0-9 and _, starting with a letter' : undefined}/>
                <Action wide label="Sample" disabled={!valid || !isSource(url)} loading={start.isPending || running} onClick={sample}/>
            </Group>
            {running && (
                <Group gap="xs">
                    <Loader size="xs"/>
                    <Text size="sm" c="dimmed">{state?.status === 'STARTED' ? 'Sampling' : 'Starting'}</Text>
                </Group>
            )}
            {finished?.status === 'failed' && <Text size="sm" c={COLOR.danger}>{finished.error}</Text>}
            {task && state && !finished && ['FAILURE', 'CANCELED'].includes(state.status) &&
                <Text size="sm" c={COLOR.danger}>{state.status}</Text>}
            {inference && <InferenceSummary inference={inference}/>}
        </>
    );
}

function InferenceSummary({inference}: { inference: InferResult }) {
    return (
        <Stack gap="xs">
            <Text size="sm">
                {inference.sampledRows.toLocaleString()} rows · {inference.columns.length} columns ·
                key {inference.keys.join(', ')}
            </Text>
            <ScrollArea.Autosize mah={260} type="auto">
                <Table withRowBorders={false} verticalSpacing={2} fz="sm">
                    <Table.Tbody>
                        {inference.reasons.map((reason) => (
                            <Table.Tr key={reason.choice}>
                                <Table.Td className="df-mono" style={{whiteSpace: 'nowrap'}}>{reason.choice}</Table.Td>
                                <Table.Td c="dimmed">{reason.why}</Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </ScrollArea.Autosize>
            {inference.problems.map((problem) => <Text key={problem} size="sm" c={COLOR.danger}>{problem}</Text>)}
        </Stack>
    );
}

function NewFileModal({folder, within, opened, onClose, onCreated}: {
    folder: FolderSummary; within?: string; opened: boolean; onClose: () => void; onCreated: (path: string) => void;
}) {
    const save = useSaveFile(folder.id);
    const [name, setName] = useState('');
    const file = within ? `${within}/${name}` : name;
    const submit = () =>
        save.mutate({file, content: ''}, {
            onSuccess: () => {
                onClose();
                onCreated(`${folder.path}/${file}`);
                setName('');
            },
            onError: notifyFailure('Not created'),
        });
    return (
        <FormModal opened={opened} onClose={onClose} title={`New file in ${within?.split('/').pop() ?? nameOf(folder)}`}
                   submit="Create" submitDisabled={!name} submitHint="Name the file" loading={save.isPending} onSubmit={submit}>
            <TextInput label="Name" placeholder="name or path/name" value={name} data-autofocus
                       onChange={(event) => setName(event.currentTarget.value.trim())}/>
        </FormModal>
    );
}

function NewTransformationModal({folder, opened, onClose, onCreated}: {
    folder: FolderSummary; opened: boolean; onClose: () => void; onCreated: (path: string) => void;
}) {
    const save = useSaveFile(folder.id);
    const [name, setName] = useState('');
    const valid = TRANSFORMATION_NAME.test(name);
    const file = `transformations/${className(name)}.java`;
    const submit = () =>
        save.mutate({file, content: transformationTemplate(name)}, {
            onSuccess: () => {
                onClose();
                onCreated(`${folder.path}/${file}`);
                setName('');
            },
            onError: notifyFailure('Not created'),
        });
    return (
        <FormModal opened={opened} onClose={onClose} title={`New transformation in ${nameOf(folder)}`} submit="Create"
                   submitDisabled={!valid} submitHint={name ? undefined : 'Name the transformation'} loading={save.isPending}
                   onSubmit={submit}>
            <TextInput label="Name" placeholder="zone_enrichment" value={name} data-autofocus
                       onChange={(event) => setName(event.currentTarget.value.trim())}
                       error={name && !valid ? 'a-z, 0-9 and _, starting with a letter' : undefined}/>
        </FormModal>
    );
}
