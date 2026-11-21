import {describe, expect, it, vi} from 'vitest';
import type {EditorHandle} from '../components/CodeEditor';
import {insertAtCursor} from './editorInsert';

const RANGE = {startLineNumber: 2, startColumn: 3, endLineNumber: 2, endColumn: 3};

function editorWith(selection: typeof RANGE | null) {
    const editor = {getSelection: () => selection, executeEdits: vi.fn(), focus: vi.fn()};
    return {editor, handle: editor as unknown as EditorHandle};
}

describe('insertAtCursor', () => {
    it('replaces the selection and gives focus back to the editor', () => {
        const {editor, handle} = editorWith(RANGE);
        insertAtCursor(handle, 'zones');
        expect(editor.executeEdits).toHaveBeenCalledWith('schema', [{range: RANGE, text: 'zones', forceMoveMarkers: true}]);
        expect(editor.focus).toHaveBeenCalled();
    });

    it('does nothing before the editor has mounted or without a cursor', () => {
        const {editor, handle} = editorWith(null);
        expect(() => insertAtCursor(null, 'zones')).not.toThrow();
        insertAtCursor(handle, 'zones');
        expect(editor.executeEdits).not.toHaveBeenCalled();
    });
});
