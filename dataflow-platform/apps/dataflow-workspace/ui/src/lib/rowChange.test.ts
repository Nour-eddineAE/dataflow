import {describe, expect, it} from 'vitest';
import {rowChange} from './rowChange';

describe('rowChange', () => {
    it('says how far rows moved since the previous run', () => {
        expect(rowChange(9963, 9963)).toEqual({label: 'same', alarming: false});
        expect(rowChange(10155, 9963)).toEqual({label: '+1.9%', alarming: false});
        expect(rowChange(9964, 9963)).toEqual({label: '≈ same', alarming: false});
        expect(rowChange(120, 0)).toEqual({label: '+120', alarming: false});
    });

    it('flags a drop of a fifth or more', () => {
        expect(rowChange(7000, 10000)).toEqual({label: '−30%', alarming: true});
        expect(rowChange(9000, 10000)).toEqual({label: '−10%', alarming: false});
    });
});
