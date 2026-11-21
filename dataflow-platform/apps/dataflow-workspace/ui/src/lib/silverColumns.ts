export type ColumnAddition = { text: string } | { problem: string };

const INDENT = /^ */;
const COLUMN_NAME = /name:\s*([\w]+)/;

function indentOf(line: string) {
    return INDENT.exec(line)![0].length;
}

function blockEnd(lines: string[], start: number, indent: number) {
    let end = start;
    for (let index = start + 1; index < lines.length; index++) {
        if (!lines[index].trim()) continue;
        if (indentOf(lines[index]) <= indent) break;
        end = index;
    }
    return end;
}

function listEnd(lines: string[], heading: number, indent: number) {
    let end = heading;
    for (let index = heading + 1; index < lines.length; index++) {
        const line = lines[index];
        if (!line.trim()) continue;
        const nested = indentOf(line) > indent;
        if (indentOf(line) < indent || (!nested && !line.trimStart().startsWith('-'))) break;
        end = index;
    }
    return end;
}

export function addSilverColumn(spec: string, column: string): ColumnAddition {
    const lines = spec.split('\n');
    const silver = lines.findIndex((line) => /^silver:\s*(#.*)?$/.test(line));
    if (silver < 0) return {problem: 'The spec has no silver block'};
    const silverEnd = blockEnd(lines, silver, 0);
    const heading = lines.findIndex((line, index) => index > silver && index <= silverEnd && /^\s+columns:/.test(line));
    if (heading < 0 || !/^\s+columns:\s*(#.*)?$/.test(lines[heading])) {
        return {problem: 'silver.columns is not a block list'};
    }
    const first = lines.slice(heading + 1).find((line) => line.trim());
    const indent = first === undefined ? -1 : indentOf(first);
    if (first === undefined || !first.trimStart().startsWith('-') || indent < indentOf(lines[heading])) {
        return {problem: 'silver.columns is not a block list'};
    }
    const end = listEnd(lines, heading, indent);
    const name = COLUMN_NAME.exec(column)?.[1];
    const declared = lines.slice(heading + 1, end + 1).some((line) => COLUMN_NAME.exec(line)?.[1] === name);
    if (declared) return {problem: `${name} is already in silver.columns`};
    lines.splice(end + 1, 0, ' '.repeat(indent) + column);
    return {text: lines.join('\n')};
}
