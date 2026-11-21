import {
    type Icon,
    IconAdjustmentsHorizontal,
    IconCloudDownload,
    IconCoffee,
    IconDatabase,
    IconDatabaseExport,
    IconDatabaseImport,
    IconEye,
    IconFilter,
    IconHistory,
    IconSql,
    IconTableImport,
    IconTransform,
} from '../../../icons';
import {TONES} from '../../../lib/layers';
import {type Block, bodyOf, type DesignNode, ingests, type NodeKind, type Spec} from '../../../lib/specModel';

export type BlockStyle = { label: string; icon: Icon; hint: string; tone: string };
export type PaletteItem = { block: Block; label: string; icon: Icon; hint: string };
export type PaletteGroup = { title: string; items: PaletteItem[] };

const NODES: Record<NodeKind, BlockStyle> = {
    pipeline: {label: 'Pipeline', icon: IconAdjustmentsHorizontal, hint: 'Owner, description, schedule', tone: TONES.neutral},
    source: {label: 'Source', icon: IconCloudDownload, hint: 'Where the files come from', tone: TONES.neutral},
    bronze: {label: 'Bronze', icon: IconDatabase, hint: 'Rows as received', tone: TONES.bronze},
    silver: {label: 'Silver', icon: IconFilter, hint: 'Typed columns and quality rules', tone: TONES.silver},
    step: {label: 'Java step', icon: IconCoffee, hint: 'A transformation on silver rows', tone: TONES.code},
    history: {label: 'History', icon: IconHistory, hint: 'Every version of every row; read as current', tone: TONES.history},
    input: {label: 'Input', icon: IconDatabaseImport, hint: 'A table of another pipeline', tone: TONES.input},
    reference: {label: 'Reference', icon: IconTableImport, hint: 'A lookup file, reloaded each run', tone: TONES.reference},
    gold: {label: 'Table', icon: IconTransform, hint: 'A table built from others', tone: TONES.gold},
    serve: {label: 'Serving', icon: IconEye, hint: 'A table in ClickHouse', tone: TONES.serving},
};

const READ: PaletteItem[] = [
    {block: 'input', label: 'Pipeline table', icon: IconDatabaseImport, hint: 'Read a table another pipeline produces'},
    {block: 'reference', label: 'Lookup file', icon: IconTableImport, hint: 'A small file, reloaded each run'},
];
const TRANSFORM: PaletteItem[] = [
    {block: 'gold-steps', label: 'Transform', icon: IconTransform, hint: 'Join, filter, group… step by step'},
    {block: 'gold-sql', label: 'SQL table', icon: IconSql, hint: 'A table from one query'},
    {block: 'gold-transformation', label: 'Java table', icon: IconCoffee, hint: 'A table from a Java transformation'},
];
const INGESTION: PaletteItem[] = [
    {block: 'step', label: 'Java step', icon: IconCoffee, hint: 'Runs a transformation on silver rows'},
];
const PUBLISH: PaletteItem[] = [
    {block: 'serve-view', label: 'Serving view', icon: IconEye, hint: 'Always fresh in ClickHouse; drop it on a table'},
    {block: 'serve-copy', label: 'Serving copy', icon: IconDatabaseExport, hint: 'Fast, refreshed each run; drop it on a table'},
];

export function palette(spec: Spec): PaletteGroup[] {
    return [
        {title: 'Read', items: READ},
        {title: 'Transform', items: ingests(spec) ? [...TRANSFORM, ...INGESTION] : TRANSFORM},
        {title: 'Publish', items: PUBLISH},
    ];
}

export function nodeStyle(node: DesignNode, spec: Spec): BlockStyle {
    if (node.kind === 'gold') {
        const table = spec.gold?.[node.index!];
        const body = table ? bodyOf(table) : 'steps';
        if (body === 'transformation') return {...NODES.gold, icon: IconCoffee};
        if (body === 'sql') return {...NODES.gold, icon: IconSql};
    }
    if (node.kind === 'serve' && spec.serve?.[node.index!]?.mode === 'copy') return {...NODES.serve, icon: IconDatabaseExport};
    return NODES[node.kind];
}
