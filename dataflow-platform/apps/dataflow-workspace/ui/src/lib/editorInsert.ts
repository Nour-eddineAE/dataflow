import type {EditorHandle} from '../components/CodeEditor';

export function insertAtCursor(editor: EditorHandle | null, text: string) {
    const selection = editor?.getSelection();
    if (!editor || !selection) return;
    editor.executeEdits('schema', [{range: selection, text, forceMoveMarkers: true}]);
    editor.focus();
}
