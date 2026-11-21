import {
    type Icon,
    IconBook2,
    IconDatabase,
    IconDatabaseImport,
    IconEye,
    IconFiles,
    IconHistory,
    IconListDetails,
    IconServer,
    IconShieldX,
    IconTable,
} from '../icons';

export type LayerStyle = { icon: Icon; title: string; tone: string };

export const TONES = {
    neutral: '#8b929b',
    bronze: '#b8804f',
    silver: '#8895a4',
    quarantine: '#a959ac',
    history: '#5b8fd1',
    current: '#4798dd',
    reference: '#9184d1',
    input: '#3f9aa6',
    gold: '#a5953a',
    serving: '#1c8cb4',
    code: '#9b7fd4',
    brand: '#7a88ea',
    dashboards: '#e08a4a',
} as const;

const LAYERS: Record<string, LayerStyle> = {
    landing: {icon: IconFiles, title: 'Landing', tone: TONES.neutral},
    bronze: {icon: IconTable, title: 'Bronze', tone: TONES.bronze},
    silver: {icon: IconTable, title: 'Silver', tone: TONES.silver},
    quarantine: {icon: IconShieldX, title: 'Quarantine', tone: TONES.quarantine},
    history: {icon: IconHistory, title: 'History', tone: TONES.history},
    current: {icon: IconEye, title: 'Current', tone: TONES.current},
    reference: {icon: IconBook2, title: 'Reference', tone: TONES.reference},
    input: {icon: IconDatabaseImport, title: 'Input', tone: TONES.input},
    gold: {icon: IconTable, title: 'Gold', tone: TONES.gold},
    serving: {icon: IconServer, title: 'Serving', tone: TONES.serving},
    system: {icon: IconListDetails, title: 'System', tone: TONES.neutral},
};

export const layer = (name?: string): LayerStyle => LAYERS[name ?? ''] ?? {icon: IconDatabase, title: name ?? '', tone: TONES.neutral};

export const tableStyle = (id: string, layerName: string): LayerStyle =>
    (id === 'current' ? {...layer(layerName), icon: IconEye} : layer(layerName));
