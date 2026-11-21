import {UnstyledButton} from '@mantine/core';
import type {ReactNode} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useMe} from '../api/hooks';
import {AccessPanel} from '../components/AccessPanel';
import {CleanupPanel} from '../components/CleanupPanel';
import {ComputePanel} from '../components/ComputePanel';
import {ConfigurationPanel} from '../components/ConfigurationPanel';
import {GitCredentials} from '../components/GitCredentials';
import {Page} from '../components/Page';
import {PageHeader} from '../components/PageHeader';
import {PreferencesPanel} from '../components/PreferencesPanel';
import {ProfilePanel} from '../components/ProfilePanel';
import '../components/settings/Settings.css';

type Section = {
    value: string;
    label: string;
    admin?: boolean;
    render: () => ReactNode;
};

const GROUPS: { title: string; sections: Section[] }[] = [
    {
        title: 'Account', sections: [
            {value: 'profile', label: 'Profile', render: () => <ProfilePanel/>},
            {value: 'preferences', label: 'Preferences', render: () => <PreferencesPanel/>},
            {value: 'git', label: 'Git', render: () => <GitCredentials/>},
        ]
    },
    {
        title: 'Platform', sections: [
            {value: 'access', label: 'Access', render: () => <AccessPanel/>},
            {value: 'compute', label: 'Compute', render: () => <ComputePanel/>},
            {value: 'cleanup', label: 'Cleanup', admin: true, render: () => <CleanupPanel/>},
        ]
    },
    {
        title: 'Administration', sections: [
            {value: 'configuration', label: 'Configuration', admin: true, render: () => <ConfigurationPanel/>},
        ]
    },
];

const ALIASES: Record<string, string> = {maintenance: 'cleanup', workspace: 'configuration', clickhouse: 'configuration'};

export function AdminPage() {
    const {data: me} = useMe();
    const [params, setParams] = useSearchParams();
    const groups = GROUPS
        .map((group) => ({...group, sections: group.sections.filter((section) => !section.admin || me?.admin)}))
        .filter((group) => group.sections.length > 0);
    const sections = groups.flatMap((group) => group.sections);
    const requested = params.get('section') ?? '';
    const current = sections.find((section) => section.value === (ALIASES[requested] ?? requested))
        ?? sections.find((section) => section.value === 'profile')
        ?? sections[0];

    return (
        <Page>
            <PageHeader title="Settings"/>
            <div className="df-settings">
                <nav className="df-settings-nav" aria-label="Settings">
                    {groups.map((group) => (
                        <div key={group.title} className="df-settings-nav-group">
                            <div className="df-settings-nav-title">{group.title}</div>
                            {group.sections.map((section) => (
                                <UnstyledButton key={section.value} className="df-settings-link"
                                                aria-current={section.value === current.value ? 'page' : undefined}
                                                onClick={() => setParams(section.value === 'profile' ? {} : {section: section.value})}>
                                    {section.label}
                                </UnstyledButton>
                            ))}
                        </div>
                    ))}
                </nav>
                <div className="df-settings-main">
                    <div className="df-settings-head">
                        <h2 className="df-settings-title">{current.label}</h2>
                    </div>
                    {current.render()}
                </div>
            </div>
        </Page>
    );
}
