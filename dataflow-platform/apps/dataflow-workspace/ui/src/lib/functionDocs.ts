import type {FunctionDoc} from '../api/types';

const TAG = /<\/?[A-Za-z][^>]*>/g;
const LINK = /\[([^\]]+)]\([^)]*\)/g;

export function hoverText(doc: FunctionDoc): string[] {
    const summary = clean(doc.description).split(/\n\s*\n/)[0]?.trim();
    return [
        doc.syntax ? `\`\`\`sql\n${doc.syntax}\n\`\`\`` : '',
        summary ?? '',
        doc.returns ? `**Returns** ${clean(doc.returns)}` : '',
    ].filter(Boolean);
}

function clean(markdown?: string | null) {
    return (markdown ?? '').replace(TAG, '').replace(LINK, '$1').trim();
}
