export type Statement = { text: string; start: number; end: number };

const QUOTES = new Set(["'", '"', '`']);

export function statements(sql: string): Statement[] {
    const found: Statement[] = [];
    let start = 0;
    let code = false;
    let i = 0;
    while (i < sql.length) {
        const char = sql[i];
        if (QUOTES.has(char)) {
            i = closing(sql, i);
            code = true;
        } else if (sql.startsWith('--', i)) {
            i = sql.indexOf('\n', i) < 0 ? sql.length : sql.indexOf('\n', i);
        } else if (sql.startsWith('/*', i)) {
            i = sql.indexOf('*/', i + 2) < 0 ? sql.length : sql.indexOf('*/', i + 2) + 2;
        } else if (char === ';') {
            if (code) found.push(trimmed(sql, start, i));
            start = i + 1;
            code = false;
            i++;
        } else {
            if (!/\s/.test(char)) code = true;
            i++;
        }
    }
    if (code) found.push(trimmed(sql, start, sql.length));
    return found;
}

export function statementAt(all: Statement[], offset: number): Statement | undefined {
    return all.findLast((statement) => statement.start <= offset) ?? all[0];
}

function closing(sql: string, open: number) {
    const quote = sql[open];
    let i = open + 1;
    while (i < sql.length) {
        if (sql[i] === '\\') i += 2;
        else if (sql[i] === quote && sql[i + 1] === quote) i += 2;
        else if (sql[i] === quote) return i + 1;
        else i++;
    }
    return sql.length;
}

function trimmed(sql: string, from: number, to: number): Statement {
    let start = from;
    let end = to;
    while (start < end && /\s/.test(sql[start])) start++;
    while (end > start && /\s/.test(sql[end - 1])) end--;
    return {text: sql.slice(start, end), start, end};
}
