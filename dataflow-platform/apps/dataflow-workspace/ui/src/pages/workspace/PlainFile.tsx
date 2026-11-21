import type {FileContent} from '../../api/types';
import {CodeEditor, type EditorLanguage} from '../../components/CodeEditor';
import type {FileKind} from '../../lib/fileKind';
import {SqlFile} from '../editor/SqlFile';
import {type MarkdownMode, MarkdownEditor} from './MarkdownEditor';

type Props = {
    kind: FileKind;
    view: string;
    problems: FileContent['problems'];
    language: EditorLanguage;
    text: string;
    onChange: (text: string) => void;
    onSave: () => void;
};

export function PlainFile({kind, view, problems, language, text, onChange, onSave}: Props) {
    if (kind === 'markdown') return <MarkdownEditor text={text} mode={view as MarkdownMode} onChange={onChange} onSave={onSave}/>;
    if (language === 'sql') return <SqlFile text={text} problems={problems} onChange={onChange} onSave={onSave}/>;
    return <CodeEditor value={text} onChange={onChange} language={language} problems={problems} onSave={onSave}/>;
}
