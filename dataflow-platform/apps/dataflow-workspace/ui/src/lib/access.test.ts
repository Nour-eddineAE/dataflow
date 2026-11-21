import {describe, expect, it} from 'vitest';
import {knownPrincipals, privilegeLabel} from './access';

const GRANTED = ['group:analysts', 'user:maria', 'group:engineers', 'group:analysts'];

describe('knownPrincipals', () => {
    it('offers the groups already granted and the person\'s own, once each, in order', () => {
        expect(knownPrincipals('group', GRANTED, ['engineers', 'admins'])).toEqual(['admins', 'analysts', 'engineers']);
    });

    it('offers people already granted, never groups', () => {
        expect(knownPrincipals('user', GRANTED, ['admins'])).toEqual(['maria']);
    });
});

describe('privilegeLabel', () => {
    it('writes a privilege code in sentence case', () => {
        expect(privilegeLabel('SELECT')).toBe('Select');
        expect(privilegeLabel('MANAGE')).toBe('Manage');
    });
});
