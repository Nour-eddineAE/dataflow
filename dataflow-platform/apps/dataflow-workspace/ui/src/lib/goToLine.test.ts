import {describe, expect, it, vi} from 'vitest';
import type {EditorHandle} from '../components/CodeEditor';
import {goToLine} from './goToLine';

function editorOf(lines: string[]) {
    const model = {
        getLineCount: () => lines.length,
        getLineFirstNonWhitespaceColumn: (line: number) => {
            const text = lines[line - 1];
            return text.trim() ? text.length - text.trimStart().length + 1 : 0;
        },
    };
    const editor = {getModel: () => model, setPosition: vi.fn(), revealLineInCenterIfOutsideViewport: vi.fn(), focus: vi.fn()};
    return {editor, handle: editor as unknown as EditorHandle};
}

describe('goToLine', () => {
    it('puts the cursor at the start of the line, in view, with focus', () => {
        const {editor, handle} = editorOf(['dataset: earthquakes', 'silver:', '  keys: [id]']);
        goToLine(handle, 3);
        expect(editor.setPosition).toHaveBeenCalledWith({lineNumber: 3, column: 3});
        expect(editor.revealLineInCenterIfOutsideViewport).toHaveBeenCalledWith(3);
        expect(editor.focus).toHaveBeenCalled();
    });

    it('ignores a line the file no longer has', () => {
        const {editor, handle} = editorOf(['dataset: earthquakes']);
        goToLine(handle, 7);
        goToLine(null, 1);
        expect(editor.setPosition).not.toHaveBeenCalled();
    });
});
