import type {EditorHandle} from '../components/CodeEditor';

export function goToLine(editor: EditorHandle | null, line: number) {
    const model = editor?.getModel();
    if (!editor || !model || line < 1 || line > model.getLineCount()) return;
    editor.setPosition({lineNumber: line, column: model.getLineFirstNonWhitespaceColumn(line) || 1});
    editor.revealLineInCenterIfOutsideViewport(line);
    editor.focus();
}
