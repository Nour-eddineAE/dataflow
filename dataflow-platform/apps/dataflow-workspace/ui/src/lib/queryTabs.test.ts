import {describe, expect, it} from 'vitest';
import type {SavedQuery} from '../api/types';
import {closeTab, createTab, nextTitle, openLink, type QueryTab, restoreTabs, statusOf} from './queryTabs';

const tab = (id: string, title = id): QueryTab => ({id, title, sql: '', limit: 1000});

describe('query tabs', () => {
    it('numbers new tabs after the titles in use', () => {
        expect(nextTitle([tab('a', 'Query 1'), tab('b', 'Query 3')])).toBe('Query 2');
        expect(createTab([]).title).toBe('Query 1');
    });

    it('tells drafts, saved and changed queries apart', () => {
        const saved = {id: 's', title: 'Daily', sql: 'SELECT 1', shared: false};
        expect(statusOf(tab('a'))).toBe('draft');
        expect(statusOf({...tab('a'), sql: 'SELECT 1'})).toBe('unsaved');
        expect(statusOf({...tab('a', 'Daily'), sql: 'SELECT 1', saved})).toBe('saved');
        expect(statusOf({...tab('a', 'Daily'), sql: 'SELECT 2', saved})).toBe('unsaved');
        expect(statusOf({...tab('a', 'Renamed'), sql: 'SELECT 1', saved})).toBe('unsaved');
    });

    it('opens a linked query in a new tab with the id the link carries', () => {
        expect(openLink([tab('a', 'Query 1')], {tab: 'n', sql: 'SELECT 1'}))
            .toEqual({tabs: [tab('a', 'Query 1'), {id: 'n', title: 'Query 2', sql: 'SELECT 1', limit: 1000}], active: 'n'});
    });

    it('opens a linked saved query once and activates it after that', () => {
        const query: SavedQuery = {id: 's', owner: 'ana', name: 'Daily', sql: 'SELECT 1', shared: true, updatedAt: '2026-11-06T10:00:00Z'};
        const first = openLink([tab('a')], {tab: 'n', saved: query});
        expect(first.active).toBe('n');
        expect(first.tabs[1]).toMatchObject({title: 'Daily', sql: 'SELECT 1', saved: {id: 's', shared: true}});
        expect(openLink(first.tabs, {tab: 'm', saved: query})).toEqual({tabs: first.tabs, active: 'n'});
    });

    it('activates the tab before the closed one', () => {
        const tabs = [tab('a'), tab('b'), tab('c')];
        expect(closeTab(tabs, 'b', 'b')).toEqual({tabs: [tab('a'), tab('c')], active: 'a'});
        expect(closeTab(tabs, 'a', 'a').active).toBe('b');
        expect(closeTab(tabs, 'c', 'a').active).toBe('a');
    });

    it('opens a fresh tab when the last one closes', () => {
        const closed = closeTab([tab('a')], 'a', 'a');
        expect(closed.tabs).toHaveLength(1);
        expect(closed.active).toBe(closed.tabs[0].id);
    });

    it('restores a tab\'s chart choice', () => {
        const [restored] = restoreTabs([{id: 'a', title: 'Regions', sql: 'SELECT 1', limit: 1000, chart: {x: 'region', y: 'events', kind: 'line'}}]);
        expect(restored.chart).toEqual({x: 'region', y: 'events', kind: 'line'});
    });

    it('restores tabs stored by earlier versions', () => {
        const [restored] = restoreTabs([{id: 'a', title: 'Old', sql: 'SELECT 1', limit: 7, savedId: 's', shared: true}]);
        expect(restored).toEqual({
            id: 'a', title: 'Old', sql: 'SELECT 1', limit: 1000,
            saved: {id: 's', title: 'Old', sql: 'SELECT 1', shared: true},
        });
        expect(restoreTabs('broken')).toHaveLength(1);
    });
});
