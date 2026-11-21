import {describe, expect, it} from 'vitest';
import type {EffectiveSetting, SettingsSection} from '../api/types';
import {humanize, isSet, matchingSettings, sectionTitle, settingHint, settingLabel, valueText} from './settings';

const AUTH: SettingsSection = {
    name: 'dataflow.workspace.auth',
    description: 'How people sign in',
    settings: [
        {name: 'dataflow.workspace.auth.client-id', description: 'OIDC client', kind: 'TEXT', value: 'workspace', origin: 'Environment WORKSPACE_OIDC_CLIENT'},
        {name: 'dataflow.workspace.auth.client-secret', description: 'Secret', kind: 'SECRET', value: '••••••'},
        {name: 'dataflow.workspace.auth.admins', description: 'Administrator groups', kind: 'LIST', value: ['platform']},
    ],
};

describe('settings', () => {
    it('turns keys into labels', () => {
        expect(humanize('client-id')).toBe('Client ID');
        expect(humanize('max_thread_pool_size')).toBe('Max thread pool size');
        expect(settingLabel('dataflow.workspace.git.github.public-url', 'dataflow.workspace.git.github')).toBe('Public URL');
        expect(settingLabel('max_rows_to_read', 'clickhouse.queries')).toBe('Max rows to read');
    });

    it('names sections', () => {
        expect(sectionTitle('dataflow.workspace.auth')).toBe('Sign-in');
        expect(sectionTitle('dataflow.workspace.git.github')).toBe('GitHub');
        expect(sectionTitle('dataflow.workspace.storage-access')).toBe('Storage access');
        expect(sectionTitle('dataflow.workspace')).toBe('General');
        expect(sectionTitle('dataflow.engine')).toBe('General');
        expect(sectionTitle('dataflow.engine.maintenance')).toBe('Maintenance');
        expect(sectionTitle('tower')).toBe('General');
        expect(sectionTitle('tower.compute_policy')).toBe('Compute policy');
    });

    it('tells set values from unset ones', () => {
        expect(isSet(null)).toBe(false);
        expect(isSet('')).toBe(false);
        expect(isSet([])).toBe(false);
        expect(isSet('0')).toBe(true);
        expect(isSet([{uid: 'runs'}])).toBe(true);
    });

    it('flattens values and entries', () => {
        expect(valueText(['a', 'b'])).toBe('a b');
        expect(valueText([{uid: 'runs', title: 'Runs'}])).toBe('runs Runs');
    });

    it('ends the description as a sentence before saying where a setting is set', () => {
        const setting = (description: string, origin: string | null = null): EffectiveSetting =>
            ({name: 'dataflow.workspace.access.refresh', description, kind: 'TEXT', value: '30s', origin});
        expect(settingHint(setting('How often grants reload', 'application.yml'))).toBe('How often grants reload. Set in: application.yml.');
        expect(settingHint(setting('How often grants reload.', 'application.yml'))).toBe('How often grants reload. Set in: application.yml.');
        expect(settingHint(setting('', 'application.yml'))).toBe('Set in: application.yml.');
        expect(settingHint(setting('How often grants reload'))).toBe('How often grants reload');
    });

    it('matches every term across label, key, description and value but never a secret', () => {
        expect(matchingSettings(AUTH, '')).toHaveLength(3);
        expect(matchingSettings(AUTH, 'client id').map((setting) => setting.kind)).toEqual(['TEXT']);
        expect(matchingSettings(AUTH, 'platform').map((setting) => setting.kind)).toEqual(['LIST']);
        expect(matchingSettings(AUTH, '••')).toHaveLength(0);
        expect(matchingSettings(AUTH, 'WORKSPACE_OIDC').map((setting) => setting.kind)).toEqual(['TEXT']);
    });
});
