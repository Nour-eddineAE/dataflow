import {ToolbarButton} from '../../components/actions/ToolbarButton';
import {Group, Input, Menu, Stack, Text, Textarea, TextInput} from '@mantine/core';
import {useDisclosure} from '@mantine/hooks';
import {ICON} from '../../lib/icons';
import {
    IconArrowDown,
    IconArrowUp,
    IconCheck,
    IconGitBranch,
    IconGitCommit,
    IconRocket
} from '../../icons';
import {useState} from 'react';
import {useBranches, useCheckout, useCommitFolder, usePull, usePush, useRelease} from '../../api/hooks';
import type {FolderTree} from '../../api/types';
import {FormModal} from '../../components/FormModal';
import {StateMark} from './icons';
import {shortCommit, plural} from '../../lib/format';
import {notifyDone, notifyFailure} from '../../components/notify';
import {Notice} from '../../components/Notice';

export function GitActions({tree}: { tree: FolderTree }) {
    const pull = usePull(tree.id);
    const push = usePush(tree.id);
    const [commitOpen, commitModal] = useDisclosure(false);
    const drafts = tree.files.filter((file) => file.state !== 'UNCHANGED').length;
    const ahead = tree.ahead ?? 0;
    const behind = tree.behind ?? 0;
    return (
        <Group gap="tight" wrap="nowrap">
            <BranchMenu tree={tree}/>
            <ToolbarButton label="Pull" icon={IconArrowDown} count={behind} hint="Get the remote's commits" loading={pull.isPending}
                           onClick={() => pull.mutate(undefined, {onError: notifyFailure('Pull failed')})}/>
            <ToolbarButton label="Commit" icon={IconGitCommit} count={drafts} hint={drafts ? 'Commit the saved drafts' : 'No drafts to commit'} disabled={!drafts}
                           onClick={commitModal.open}/>
            <ToolbarButton label="Push" icon={IconArrowUp} count={ahead} hint={ahead ? 'Send the commits to the remote' : 'Nothing to push'} disabled={!ahead}
                           loading={push.isPending} onClick={() => push.mutate(undefined, {
                               onSuccess: () => notifyDone({message: `Pushed ${tree.branch}`}),
                               onError: notifyFailure('Push failed'),
                           })}/>
            <ReleaseMenu tree={tree}/>
            <CommitModal tree={tree} opened={commitOpen} onClose={commitModal.close}/>
        </Group>
    );
}

function ReleaseMenu({tree}: { tree: FolderTree }) {
    const release = useRelease();
    const [target, setTarget] = useState<string>();
    return (
        <>
            <Menu position="bottom-end" width={180}>
                <Menu.Target>
                    <ToolbarButton label="Release" icon={IconRocket} menu disabled={!tree.targets.length} loading={release.isPending}/>
                </Menu.Target>
                <Menu.Dropdown>
                    {tree.targets.map((name) => <Menu.Item key={name}
                                                           onClick={() => setTarget(name)}>{name}</Menu.Item>)}
                </Menu.Dropdown>
            </Menu>
            <FormModal opened={!!target} onClose={() => setTarget(undefined)}
                       title={`Release ${tree.project} to ${target}`} submit="Release"
                       loading={release.isPending}
                       onSubmit={() => release.mutate({folderId: tree.id, target: target!}, {
                           onSuccess: (view) => {
                               setTarget(undefined);
                               notifyDone({
                                   title: `${view.project} → ${view.target}`,
                                   message: shortCommit(view.commit)
                               });
                           },
                           onError: notifyFailure('Not released'),
                       })}>
                <Stack gap="snug">
                    <Text size="sm" className="df-mono">{tree.branch}{tree.head ? ` @ ${shortCommit(tree.head)}` : ''}</Text>
                    {!!tree.ahead && (
                        <Notice tone="warning">
                            {`${plural(tree.ahead, 'local commit')} not pushed; production targets release the pushed branch.`}
                        </Notice>
                    )}
                </Stack>
            </FormModal>
        </>
    );
}

function BranchMenu({tree}: { tree: FolderTree }) {
    const branches = useBranches(tree.id);
    const checkout = useCheckout(tree.id);
    const [creating, setCreating] = useState(false);
    const [name, setName] = useState('');
    const switchTo = (branch: string) =>
        checkout.mutate(branch, {
            onSuccess: () => {
                setCreating(false);
                setName('');
            },
            onError: notifyFailure('Not switched'),
        });
    return (
        <>
            <Menu position="bottom-end" width={220}>
                <Menu.Target>
                    <ToolbarButton label={tree.branch} icon={IconGitBranch} menu quiet loading={checkout.isPending}/>
                </Menu.Target>
                <Menu.Dropdown>
                    {(branches.data ?? [tree.branch]).map((branch) => (
                        <Menu.Item key={branch} aria-current={branch === tree.branch || undefined}
                                   leftSection={branch === tree.branch ? <IconCheck {...ICON}/> : <span className="df-menu-spacer"/>}
                                   onClick={() => branch !== tree.branch && switchTo(branch)}>{branch}</Menu.Item>
                    ))}
                    <Menu.Divider/>
                    <Menu.Item onClick={() => setCreating(true)}>New branch</Menu.Item>
                </Menu.Dropdown>
            </Menu>
            <FormModal opened={creating} onClose={() => setCreating(false)} title="New branch" submit="Create"
                       submitDisabled={!name} loading={checkout.isPending} onSubmit={() => switchTo(name)}>
                <TextInput label="Name" placeholder="zone-names" value={name} data-autofocus
                           onChange={(event) => setName(event.currentTarget.value.trim())}/>
            </FormModal>
        </>
    );
}

function CommitModal({tree, opened, onClose}: { tree: FolderTree; opened: boolean; onClose: () => void }) {
    const commit = useCommitFolder(tree.id);
    const changed = tree.files.filter((file) => file.state !== 'UNCHANGED');
    const [typed, setTyped] = useState<string>();
    const message = typed ?? defaultMessage(changed.map((file) => file.path));
    const submit = () =>
        commit.mutate(message, {
            onSuccess: (result) => {
                notifyDone({message: `Committed ${shortCommit(result.id)}`});
                onClose();
            },
            onError: notifyFailure('Not committed'),
        });
    return (
        <FormModal opened={opened} onClose={onClose} title={`Commit to ${tree.branch}`} size="lg" submit="Commit"
                   submitDisabled={!message.trim()} loading={commit.isPending} onSubmit={submit}
                   onClosed={() => setTyped(undefined)}>
            <Input.Wrapper label={`${plural(changed.length, 'file')}`}>
                <Stack gap="tight" mt="tight">
                    {changed.map((file) => (
                        <Group key={file.path} gap="cozy" wrap="nowrap">
                            <StateMark state={file.state}/>
                            <Text size="sm" className="df-mono" truncate>{file.path}</Text>
                        </Group>
                    ))}
                </Stack>
            </Input.Wrapper>
            <Textarea label="Message" autosize minRows={2} value={message} data-autofocus
                      onChange={(event) => setTyped(event.currentTarget.value)}/>
        </FormModal>
    );
}

function defaultMessage(paths: string[]) {
    const specs = paths.map((path) => /^pipelines\/([^/]+)\/pipeline\.yml$/.exec(path)?.[1]).filter(Boolean);
    if (specs.length === paths.length && specs.length > 0) return `feat(pipelines): update ${specs.join(', ')}`;
    return paths.length === 1 ? `chore: update ${paths[0]}` : `chore: update ${paths.length} files`;
}
