import {AppShell, Box, Group, Menu, ScrollArea, Tooltip, UnstyledButton} from '@mantine/core';
import {spotlight} from '@mantine/spotlight';
import {IconCode, IconGitBranch, IconPlus, IconRoute} from '../icons';
import {useDisclosure, useDocumentTitle, useHotkeys, useMediaQuery} from '@mantine/hooks';
import {Suspense, useEffect, useRef, useState} from 'react';
import {NavLink, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {useStatus} from '../api/hooks';
import {load, save} from '../lib/storage';
import {titleOf} from '../lib/title';
import {type Destination, NAVIGATION, PLATFORM} from '../lib/navigation';
import {preloadPages} from '../routes';
import {ErrorBoundary} from './ErrorBoundary';
import {Loading} from './ErrorState';
import {Logo} from './Logo';
import {RunNotifier} from './RunNotifier';
import {SearchButton} from './SearchButton';
import {SearchSpotlight} from './SearchSpotlight';
import {AccountMenu, ActiveRuns, AppsMenu} from './shell/HeaderActions';
import {SidebarToggle} from './shell/SidebarToggle';
import {ShortcutsModal} from './ShortcutsModal';
import {ICON} from '../lib/icons';
import './shell/Shell.css';
import {TOOLTIP_DELAY_FAST} from '../lib/timing';
import {PAGE} from '../lib/links';
import {EASE, MOTION} from '../lib/motion';

const NAV_ICON = ICON;

const TOOL_PAGES = [PAGE.workspace, PAGE.sql];

const COLLAPSED_KEY = 'df.sidebar.collapsed';
const TOOL_COLLAPSED_KEY = 'df.sidebar.collapsed.tools';
const NAV_WIDTH = 220;
const RAIL_WIDTH = 56;
const PRELOAD_DELAY = 1200;

export function Shell() {
    const navigate = useNavigate();
    const location = useLocation();
    const [preferCollapsed, setPreferCollapsed] = useState(() => load(COLLAPSED_KEY, false));
    const [preferToolCollapsed, setPreferToolCollapsed] = useState(() => load(TOOL_COLLAPSED_KEY, true));
    const narrow = useMediaQuery('(max-width: 1100px)') ?? false;
    const [openOnNarrow, setOpenOnNarrow] = useState(false);
    const [shortcuts, toggleShortcuts] = useDisclosure(false);
    const tool = TOOL_PAGES.some((page) => location.pathname.startsWith(page));
    const collapsed = tool ? preferToolCollapsed : preferCollapsed;
    const rail = narrow ? !openOnNarrow : collapsed;
    const toggleNav = () => {
        if (narrow) setOpenOnNarrow(!openOnNarrow);
        else if (tool) setPreferToolCollapsed(!preferToolCollapsed);
        else setPreferCollapsed(!preferCollapsed);
    };
    useHotkeys([['shift+?', toggleShortcuts.open], ['mod+b', toggleNav]]);

    useEffect(() => save(COLLAPSED_KEY, preferCollapsed), [preferCollapsed]);
    useEffect(() => save(TOOL_COLLAPSED_KEY, preferToolCollapsed), [preferToolCollapsed]);
    useEffect(() => {
        const timer = window.setTimeout(preloadPages, PRELOAD_DELAY);
        return () => window.clearTimeout(timer);
    }, []);
    useDocumentTitle(titleOf(location.pathname));
    const surface = useRef<HTMLDivElement>(null);
    const shownPath = useRef(location.pathname);
    useEffect(() => {
        if (shownPath.current === location.pathname) return;
        shownPath.current = location.pathname;
        if (document.activeElement === document.body) surface.current?.focus({preventScroll: true});
    }, [location.pathname]);

    return (
        <AppShell header={{height: 52}} navbar={{width: rail ? RAIL_WIDTH : NAV_WIDTH, breakpoint: 0}}
                  padding={0} transitionDuration={MOTION.base} transitionTimingFunction={EASE}>
            <a className="df-skip" href="#content" onClick={(event) => {
                event.preventDefault();
                surface.current?.focus();
            }}>Skip to content</a>
            <AppShell.Header className="df-header">
                <Group h="100%" px="sm" wrap="nowrap" justify="space-between" gap="md" className="df-shell-header">
                    <Group gap="snug" wrap="nowrap" className="df-shell-header-brand">
                        <SidebarToggle collapsed={rail} onToggle={toggleNav}/>
                        <UnstyledButton onClick={() => navigate(PAGE.home)} aria-label="Home">
                            <Logo/>
                        </UnstyledButton>
                    </Group>
                    <Box className="df-shell-header-search">
                        <SearchButton onClick={() => spotlight.open()}/>
                    </Box>
                    <Group gap="tight" wrap="nowrap" justify="flex-end" className="df-shell-header-actions">
                        <ActiveRuns/>
                        <AppsMenu/>
                        <AccountMenu onShortcuts={toggleShortcuts.open}/>
                    </Group>
                </Group>
            </AppShell.Header>

            <AppShell.Navbar className="df-sidebar" data-rail={rail || undefined} p="cozy">
                <AppShell.Section mb="tight">
                    <Menu position={rail ? 'right-start' : 'bottom-start'} width={200}>
                        <Menu.Target>
                            <Tooltip label="New" position="right" disabled={!rail}>
                                <UnstyledButton className="df-nav-new" aria-label="New">
                                    <IconPlus {...ICON}/>
                                    <span className="df-nav-label">New</span>
                                </UnstyledButton>
                            </Tooltip>
                        </Menu.Target>
                        <Menu.Dropdown>
                            <Menu.Item leftSection={<IconGitBranch {...ICON}/>}
                                       onClick={() => navigate(PAGE.newGitFolder)}>Git folder</Menu.Item>
                            <Menu.Item leftSection={<IconRoute {...ICON}/>}
                                       onClick={() => navigate(PAGE.newPipeline)}>Pipeline</Menu.Item>
                            <Menu.Item leftSection={<IconCode {...ICON}/>}
                                       onClick={() => navigate(PAGE.newQuery)}>Query</Menu.Item>
                        </Menu.Dropdown>
                    </Menu>
                </AppShell.Section>
                <AppShell.Section grow component={ScrollArea} scrollbarSize={6} type="never">
                    {NAVIGATION.map((group, index) => (
                        <Box key={group.title ?? index} mt={index === 0 ? 'tight' : 0}>
                            {group.title && <div className="df-nav-section"><span className="df-nav-label">{group.title}</span></div>}
                            {group.items.map((item) => <NavItem key={item.to} item={item} rail={rail}/>)}
                        </Box>
                    ))}
                </AppShell.Section>
                <AppShell.Section className="df-nav-foot">
                    {PLATFORM.map((item) => <NavItem key={item.to} item={item} rail={rail}/>)}
                </AppShell.Section>
            </AppShell.Navbar>

            <AppShell.Main>
                <div className="df-surface" id="content" ref={surface} tabIndex={-1}>
                    <ErrorBoundary resetKey={location.pathname}>
                        <Suspense fallback={<Loading/>}>
                            <Outlet/>
                        </Suspense>
                    </ErrorBoundary>
                </div>
            </AppShell.Main>
            <SearchSpotlight onShortcuts={toggleShortcuts.open}/>
            <ShortcutsModal opened={shortcuts} onClose={toggleShortcuts.close}/>
            <RunNotifier/>
        </AppShell>
    );
}

function NavItem({item, rail}: { item: Destination; rail: boolean }) {
    return (
        <Tooltip label={item.label} position="right" disabled={!rail} openDelay={TOOLTIP_DELAY_FAST}>
            <NavLink to={item.to} end={item.to === PAGE.home} className="df-nav-link">
                {({isActive}) => (
                    <>
                        <item.icon {...NAV_ICON} weight={isActive ? 'fill' : 'regular'}/>
                        <span className="df-nav-label">{item.label}</span>
                        {item.to === PAGE.services && <ServiceHealth/>}
                    </>
                )}
            </NavLink>
        </Tooltip>
    );
}

function ServiceHealth() {
    const {data: status, isError} = useStatus();
    const down = status ? [!status.tower && 'Tower', !status.serving && 'Serving'].filter(Boolean) : [];
    if (!isError && !down.length) return null;
    const label = isError ? 'Status unavailable' : `${down.join(' and ')} not reachable`;
    return <span className="df-nav-health" data-tone="down" role="img" aria-label={label} title={label}/>;
}
