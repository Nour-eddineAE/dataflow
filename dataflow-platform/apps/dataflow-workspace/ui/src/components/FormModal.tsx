import {Box, Group, Modal, Stack, Text} from '@mantine/core';
import {type FormEvent, type ReactNode, useId} from 'react';
import {Action} from './actions/Action';

export const DIALOG_WIDTHS = {sm: 420, md: 520, lg: 720, full: 'calc(100vw - 96px)'} as const;

type FormWidth = Exclude<keyof typeof DIALOG_WIDTHS, 'full'>;

export function FormModal({
                              opened,
                              onClose,
                              title,
                              size = 'sm',
                              submit,
                              submitDisabled,
                              submitHint,
                              alternative,
                              loading,
                              danger,
                              cancel = 'Cancel',
                              onSubmit,
                              onClosed,
                              children
                          }: {
    opened: boolean;
    onClose: () => void;
    title: ReactNode;
    size?: FormWidth;
    submit: string;
    submitDisabled?: boolean;
    submitHint?: string;
    alternative?: ReactNode;
    loading?: boolean;
    danger?: boolean;
    cancel?: string;
    onSubmit: () => void;
    onClosed?: () => void;
    children?: ReactNode;
}) {
    const handle = (event: FormEvent) => {
        event.preventDefault();
        if (!submitDisabled && !loading) onSubmit();
    };
    return (
        <Modal opened={opened} onClose={onClose} onExitTransitionEnd={onClosed} title={title} size={DIALOG_WIDTHS[size]} centered>
            <form onSubmit={handle}>
                <Stack gap="md">
                    {children}
                    <FormActions onCancel={onClose} submit={submit} disabled={submitDisabled} hint={submitDisabled ? submitHint : undefined} loading={loading}
                                 danger={danger} cancel={cancel} alternative={alternative}/>
                </Stack>
            </form>
        </Modal>
    );
}

export function FormActions({onCancel, submit, disabled, hint, loading, danger, alternative, cancel = 'Cancel'}: {
    onCancel: () => void;
    submit: string;
    alternative?: ReactNode;
    disabled?: boolean;
    hint?: string;
    loading?: boolean;
    danger?: boolean;
    cancel?: string;
}) {
    const hintId = useId();
    return (
        <Group justify="flex-end" gap="xs" mt="xs" wrap="nowrap">
            {alternative && <Box mr="auto">{alternative}</Box>}
            {hint && <Text id={hintId} size="xs" c="dimmed" mr="auto">{hint}</Text>}
            <Action wide label={cancel} onClick={onCancel}/>
            <Action wide type="submit" tone="primary" danger={danger} label={submit} disabled={disabled} loading={loading}
                    aria-describedby={hint ? hintId : undefined}/>
        </Group>
    );
}

export function ViewModal({opened, onClose, title, size = 'lg', children}: {
    opened: boolean;
    onClose: () => void;
    title: ReactNode;
    size?: keyof typeof DIALOG_WIDTHS;
    children?: ReactNode;
}) {
    return <Modal opened={opened} onClose={onClose} title={title} size={DIALOG_WIDTHS[size]} centered>{children}</Modal>;
}
