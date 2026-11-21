import type * as Monaco from 'monaco-editor';

type Key = { key: string; detail: string };

const KEYS: Key[] = [
    {key: 'dataset', detail: 'Folder name: lower case, digits, _'},
    {key: 'version', detail: 'Spec version, 3 or more'},
    {key: 'owner', detail: 'Team that owns the data'},
    {key: 'description', detail: 'One line about the data'},
    {key: 'source', detail: 'Where the data comes from: url or path, format, options'},
    {key: 'url', detail: 'HTTP source; may contain {partition}'},
    {key: 'path', detail: 'File or glob; may contain {partition}'},
    {key: 'format', detail: 'Any Spark format'},
    {key: 'options', detail: 'Spark reader options'},
    {key: 'partitions', detail: 'monthly or daily, with a start'},
    {key: 'schedule', detail: 'Cron, run by the tower'},
    {key: 'bronze', detail: 'schema-evolution, max-files-per-trigger'},
    {key: 'compute', detail: 'Resources and Spark settings'},
    {key: 'executors', detail: 'min, max'},
    {key: 'driver-memory', detail: 'Driver heap, e.g. 2g'},
    {key: 'executor-memory', detail: 'Executor heap, e.g. 4g'},
    {key: 'driver-cores', detail: 'Driver CPU cores'},
    {key: 'executor-cores', detail: 'Executor CPU cores'},
    {key: 'spark-conf', detail: 'Allowed Spark tuning settings'},
    {key: 'schema-evolution', detail: 'rescue or add-new-columns'},
    {key: 'max-files-per-trigger', detail: 'Files per bronze micro-batch'},
    {key: 'silver', detail: 'columns, derive, expectations'},
    {key: 'columns', detail: 'Typed columns read from bronze'},
    {key: 'derive', detail: 'Columns computed from earlier silver columns'},
    {key: 'expectations', detail: 'Rules: warn, drop or fail'},
    {key: 'history', detail: 'keys, sequence-by, stored-as, track-history-except'},
    {key: 'keys', detail: 'Columns that identify a fact'},
    {key: 'sequence-by', detail: 'Timestamp, date or bigint ordering versions'},
    {key: 'stored-as', detail: 'scd2'},
    {key: 'track-history-except', detail: 'Columns whose change makes no new version'},
    {key: 'references', detail: 'Lookup tables reloaded every run'},
    {key: 'inputs', detail: 'Tables of other pipelines, named here: alias: <dataset>.<table>'},
    {key: 'gold', detail: 'Tables built with sql, a transformation, or from + steps'},
    {key: 'materialize', detail: 'false: computed inside the tables that read it, never written'},
    {key: 'join', detail: 'with, type (inner, left, right, full, semi, anti), on or condition, prefix'},
    {key: 'with', detail: 'The table to join'},
    {key: 'on', detail: 'Matching columns: { left_column: right_column }'},
    {key: 'condition', detail: 'Join condition over left.<column> and right.<column>'},
    {key: 'prefix', detail: 'Prefix for the joined table\'s columns'},
    {key: 'union', detail: 'Append the rows of these tables, columns matched by name'},
    {key: 'filter', detail: 'Keep rows where this SQL condition holds'},
    {key: 'select', detail: 'Keep these columns or expressions (expr AS name)'},
    {key: 'rename', detail: '{ old: new }'},
    {key: 'drop', detail: 'Leave these columns out'},
    {key: 'aggregate', detail: 'by: [columns], measures: { name: expression }'},
    {key: 'by', detail: 'Group by these columns or expressions'},
    {key: 'measures', detail: 'name: aggregate expression, such as count(*)'},
    {key: 'dedupe', detail: 'keys, latest-by: one row per key'},
    {key: 'latest-by', detail: 'Keep the row with the greatest value of this column'},
    {key: 'serve', detail: 'Tables in ClickHouse: view or copy'},
];

const SNIPPETS: { label: string; detail: string; body: string }[] = [
    {
        label: 'column',
        detail: 'Silver column',
        body: '- { name: ${1:name}, type: ${2|string,int,bigint,double,boolean,date,timestamp|}, from: ${3:source_column} }'
    },
    {
        label: 'required column',
        detail: 'Silver column that must be set',
        body: '- { name: ${1:name}, type: ${2|string,int,bigint,double,boolean,date,timestamp|}, required: true, from: ${3:source_column} }'
    },
    {
        label: 'derive',
        detail: 'Derived column',
        body: '- { name: ${1:name}, type: ${2|string,int,bigint,double,boolean,date,timestamp|}, from: "${3:expression}" }'
    },
    {
        label: 'expectation',
        detail: 'Quality rule',
        body: '- { name: ${1:rule}, check: "${2:column IS NOT NULL}", on-failure: ${3|warn,drop,fail|} }'
    },
    {
        label: 'reference',
        detail: 'Lookup table',
        body: '- { name: ${1:name}, url: ${2:https://}, format: ${3|csv,json,parquet|}, options: { header: "true" } }'
    },
    {label: 'gold table', detail: 'Gold SQL', body: '- name: ${1:name}\n  sql: |\n    SELECT ${2:*}\n    FROM current'},
    {
        label: 'transform',
        detail: 'Gold table built step by step',
        body: '- name: ${1:name}\n  from: ${2:current}\n  steps:\n    - filter: "${3:condition}"\n    - aggregate: { by: [${4:column}], measures: { rows: "count(*)" } }',
    },
    {label: 'join step', detail: 'Join another table', body: '- join: { with: ${1:table}, type: ${2|left,inner,full,right,semi,anti|}, on: { ${3:column}: ${4:column} } }'},
    {label: 'input', detail: 'A table of another pipeline', body: '${1:name}: ${2:dataset}.${3|current,gold.,reference.|}'},
    {label: 'serve view', detail: 'ClickHouse view', body: '- { from: ${1:current}, table: ${2:name}, mode: view }'},
    {
        label: 'serve copy',
        detail: 'ClickHouse copy, refreshed each run',
        body: '- { from: gold.${1:name}, table: ${2:name}, mode: copy, order-by: ${3:column} }'
    },
];

const VALUE_DOCS: Record<string, string> = {
    warn: 'Keep the row and count it',
    drop: 'Send the row to quarantine',
    fail: 'Fail the run before anything is written',
    rescue: 'Unknown columns and values that do not cast go to _rescued_data',
    'add-new-columns': 'New source columns become bronze columns',
    view: 'ClickHouse view over the Delta table, always current',
    copy: 'ClickHouse table refreshed after each run; needs order-by',
    scd2: 'Every version kept with __START_AT and __END_AT',
};

const TOKEN = /[\w-]+/g;

export function specDoc(line: string, column: number): { text: string; start: number; end: number } | undefined {
    for (const match of line.matchAll(TOKEN)) {
        const start = match.index + 1;
        const end = start + match[0].length;
        if (column < start || column > end) continue;
        const isKey = /^\s*:/.test(line.slice(end - 1));
        const key = isKey ? KEYS.find((candidate) => candidate.key === match[0]) : undefined;
        const text = key?.detail ?? (isKey ? undefined : VALUE_DOCS[match[0]]);
        return text ? {text, start, end} : undefined;
    }
    return undefined;
}

const VALUES: [RegExp, string[]][] = [
    [/\btype:\s*[\w(,]*$/, ['string', 'int', 'bigint', 'double', 'float', 'boolean', 'date', 'timestamp', 'decimal(18,2)']],
    [/\bon-failure:\s*\w*$/, ['warn', 'drop', 'fail']],
    [/\bmode:\s*\w*$/, ['view', 'copy']],
    [/\bformat:\s*\w*$/, ['csv', 'json', 'parquet', 'orc', 'avro', 'text']],
    [/\bschema-evolution:\s*[\w-]*$/, ['rescue', 'add-new-columns']],
    [/\bstored-as:\s*\w*$/, ['scd2']],
    [/\brequired:\s*\w*$/, ['true', 'false']],
];

export function specCompletions(monaco: typeof Monaco) {
    return (model: Monaco.editor.ITextModel, position: Monaco.Position): Monaco.languages.CompletionList => {
        const word = model.getWordUntilPosition(position);
        const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
        const before = model.getValueInRange(new monaco.Range(position.lineNumber, 1, position.lineNumber, position.column));
        const {Kind, Rule} = {
            Kind: monaco.languages.CompletionItemKind,
            Rule: monaco.languages.CompletionItemInsertTextRule
        };

        const values = VALUES.find(([pattern]) => pattern.test(before));
        if (values) {
            return {
                suggestions: values[1].map((value) => ({
                    label: value,
                    kind: Kind.Value,
                    insertText: value,
                    range
                }))
            };
        }
        if (!/^\s*-?\s*[\w-]*$/.test(before)) {
            return {suggestions: []};
        }
        return {
            suggestions: [
                ...KEYS.map((entry) => ({
                    label: entry.key,
                    kind: Kind.Property,
                    detail: entry.detail,
                    insertText: `${entry.key}: `,
                    range,
                    sortText: `1${entry.key}`,
                })),
                ...SNIPPETS.map((snippet) => ({
                    label: snippet.label, kind: Kind.Snippet, detail: snippet.detail, insertText: snippet.body,
                    insertTextRules: Rule.InsertAsSnippet, range, sortText: `0${snippet.label}`,
                })),
            ],
        };
    };
}
