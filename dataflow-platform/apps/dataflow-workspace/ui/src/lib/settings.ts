import type {EffectiveSetting, SettingsSection, SettingValue} from '../api/types';

const GENERAL = 'General';

const TITLES: Record<string, string> = {
    'dataflow.workspace': GENERAL,
    'dataflow.workspace.auth': 'Sign-in',
    'dataflow.engine': GENERAL,
    tower: GENERAL,
    'clickhouse.server': 'Server',
    'clickhouse.queries': 'Query defaults',
};

const WORDS: Record<string, string> = {
    api: 'API',
    github: 'GitHub',
    http: 'HTTP',
    id: 'ID',
    oidc: 'OIDC',
    uid: 'UID',
    url: 'URL',
};

export function humanize(key: string): string {
    const text = key.split(/[-_.]+/).filter(Boolean).map((word) => WORDS[word] ?? word).join(' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
}

export function sectionTitle(section: string): string {
    return TITLES[section] ?? humanize(section.slice(section.lastIndexOf('.') + 1));
}

export function settingLabel(setting: string, section: string): string {
    return humanize(setting.startsWith(`${section}.`) ? setting.slice(section.length + 1) : setting);
}

const SENTENCE_END = /[.!?]$/;

export function settingHint({description, origin}: EffectiveSetting): string {
    if (!origin) return description;
    const sentence = description && !SENTENCE_END.test(description) ? `${description}.` : description;
    return [sentence, `Set in: ${origin}.`].filter(Boolean).join(' ');
}

export function isSet(value: SettingValue): boolean {
    return Array.isArray(value) ? value.length > 0 : value != null && value !== '';
}

export function valueText(value: SettingValue): string {
    if (value == null) return '';
    if (!Array.isArray(value)) return value;
    return value.map((item) => typeof item === 'string' ? item : Object.values(item).join(' ')).join(' ');
}

export function matchingSettings(section: SettingsSection, query: string): EffectiveSetting[] {
    const terms = query.toLowerCase().split(/\s+/).filter(Boolean);
    if (!terms.length) return section.settings;
    return section.settings.filter((setting) => {
        const text = [setting.name, settingLabel(setting.name, section.name), setting.description, setting.origin ?? '',
            setting.kind === 'SECRET' ? '' : valueText(setting.value)].join(' ').toLowerCase();
        return terms.every((term) => text.includes(term));
    });
}
