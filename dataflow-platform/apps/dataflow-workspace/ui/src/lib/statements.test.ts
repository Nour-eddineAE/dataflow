import {describe, expect, it} from 'vitest';
import {statementAt, statements} from './statements';

const SCRIPT = `SELECT 'a;b' AS text;

-- counts; per region
SELECT region, count() FROM earthquakes.current GROUP BY region;
/* ; */ SELECT "we;ird" FROM t
`;

describe('statements', () => {
    it('splits on semicolons outside strings and comments', () => {
        expect(statements(SCRIPT).map((statement) => statement.text)).toEqual([
            "SELECT 'a;b' AS text",
            '-- counts; per region\nSELECT region, count() FROM earthquakes.current GROUP BY region',
            '/* ; */ SELECT "we;ird" FROM t',
        ]);
    });

    it('skips pieces that hold only comments or whitespace', () => {
        expect(statements('SELECT 1;\n-- done\n;  ;')).toHaveLength(1);
    });

    it('reads escaped and doubled quotes', () => {
        expect(statements("SELECT 'it''s; \\'fine;'; SELECT 2").map((statement) => statement.text))
            .toEqual(["SELECT 'it''s; \\'fine;'", 'SELECT 2']);
    });
});

describe('statementAt', () => {
    it('picks the statement the cursor is in, or the one before it', () => {
        expect(statementAt(statements(SCRIPT), SCRIPT.indexOf('region,'))?.text).toContain('GROUP BY region');
        expect(statementAt(statements(SCRIPT), SCRIPT.indexOf(';\n\n') + 2)?.text).toBe("SELECT 'a;b' AS text");
        expect(statementAt(statements(SCRIPT), 0)?.text).toBe("SELECT 'a;b' AS text");
        expect(statementAt(statements(''), 0)).toBeUndefined();
    });
});
