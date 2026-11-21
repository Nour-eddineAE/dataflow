import {useQueryClient} from '@tanstack/react-query';
import {useEffect, useRef, useState} from 'react';
import {useBlocker} from 'react-router-dom';
import {useDiscardFile, useFolderFile, useRemoveFile, useSaveFile} from '../../api/hooks';
import {notifyDone, notifyFailure, notifyWarning} from '../../components/notify';
import type {Positions} from '../../lib/designerPositions';
import {begin, type History, record, redo, undo} from '../../lib/history';
import {load, save as store} from '../../lib/storage';
import type {DesignDocument} from './designer/PipelineDesigner';

const FIELDS = 'input, textarea, [contenteditable="true"]';

const fieldIds = new WeakMap<Element, number>();
let nextField = 0;

function mergeKey(target: Element | null): string | undefined {
    if (!target) return undefined;
    if (target.closest('.monaco-editor')) return 'code';
    const field = target.closest(FIELDS);
    if (!field) return undefined;
    if (!fieldIds.has(field)) fieldIds.set(field, ++nextField);
    return `field:${fieldIds.get(field)}`;
}

export function useFileDocument(folderId: string, file: string, onClosed: () => void) {
    const loaded = useFolderFile(folderId, file);
    const saving = useSaveFile(folderId);
    const discarding = useDiscardFile(folderId);
    const removing = useRemoveFile(folderId);
    const client = useQueryClient();
    const layoutKey = `df.design.${folderId}:${file}`;
    const [history, setHistory] = useState<History<DesignDocument>>();
    const leaving = useRef(false);

    if (!history && loaded.data) setHistory(begin({text: loaded.data.content ?? '', positions: load<Positions>(layoutKey, {})}));
    useEffect(() => {
        if (history) store(layoutKey, history.present.positions);
    }, [history, layoutKey]);

    const document = history?.present;
    const text = document?.text ?? '';
    const saved = loaded.data?.content;
    const dirty = document !== undefined && saved !== undefined && text !== saved;
    const drafted = loaded.data !== undefined && loaded.data.state !== 'UNCHANGED';
    const busy = saving.isPending || discarding.isPending || removing.isPending;

    const blocker = useBlocker(({currentLocation, nextLocation}) =>
        dirty && !leaving.current && (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search));
    useEffect(() => {
        if (!dirty) return undefined;
        const warn = (event: BeforeUnloadEvent) => event.preventDefault();
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);

    const leave = () => {
        leaving.current = true;
        onClosed();
    };

    const change = (next: DesignDocument) =>
        setHistory((current) => current && record(current, next, {merge: mergeKey(window.document.activeElement)}));
    const setText = (next: string) => change({text: next, positions: document?.positions ?? {}});

    const save = (then?: () => void) => {
        if (!dirty || busy) return;
        saving.mutate({file, content: text}, {
            onSuccess: (result) => {
                client.setQueryData(['folder-file', folderId, file], result);
                if (result.problems.length) notifyWarning({message: `Saved as a draft with ${result.problems.length} problems`});
                else notifyDone({message: 'Saved as a draft'});
                then?.();
            },
            onError: notifyFailure('Not saved'),
        });
    };

    const discard = () => {
        if (busy || !document) return;
        if (dirty && saved !== undefined) {
            change({text: saved, positions: document.positions});
            return;
        }
        discarding.mutate(file, {
            onSuccess: async () => {
                if (loaded.data?.state === 'ADDED') {
                    leave();
                    return;
                }
                const refreshed = await loaded.refetch();
                change({text: refreshed.data?.content ?? '', positions: document.positions});
            },
            onError: notifyFailure('Not discarded'),
        });
    };

    const remove = (then: () => void) => removing.mutate(file, {
        onSuccess: () => {
            then();
            leave();
        },
        onError: notifyFailure('Not deleted'),
    });

    return {
        loaded,
        history,
        document,
        text,
        saved,
        dirty,
        drafted,
        busy,
        saving: saving.isPending,
        discarding: discarding.isPending,
        removing: removing.isPending,
        blocker,
        change,
        setText,
        undo: () => setHistory((current) => current && undo(current)),
        redo: () => setHistory((current) => current && redo(current)),
        save,
        discard,
        remove,
    };
}
