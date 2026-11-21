import {renderToStaticMarkup} from 'react-dom/server';
import {describe, expect, it} from 'vitest';
import {MarkdownPreview} from './MarkdownPreview';

const render = (text: string) => renderToStaticMarkup(<MarkdownPreview text={text}/>);

describe('MarkdownPreview', () => {
    it('renders headings, GFM tables, tasks and fenced code', () => {
        const html = render('# Readme\n\n| Field | Type |\n| --- | --- |\n| id | string |\n\n- [x] Ready\n\n```sql\nSELECT * FROM current\n```');
        expect(html).toContain('<h1>Readme</h1>');
        expect(html).toContain('<table>');
        expect(html).toContain('<td>string</td>');
        expect(html).toContain('type="checkbox"');
        expect(html).toContain('disabled=""');
        expect(html).toContain('checked=""');
        expect(html).toContain('class="language-sql"');
        expect(html).toContain('SELECT * FROM current');
    });

    it('does not render raw HTML or executable link URLs', () => {
        const html = render('<script>alert(1)</script>\n\n<img src="x" onerror="alert(1)">\n\n[Unsafe](javascript:alert%281%29)\n\n[Encoded](jav&#x61;script:alert%281%29)\n\n[Data](data:text/html;base64,PHNjcmlwdD4=)');
        expect(html).not.toContain('<script');
        expect(html).not.toContain('<img');
        expect(html).not.toContain('onerror');
        expect(html).not.toContain('javascript:');
        expect(html).not.toContain('data:text/html');
        expect(html).toContain('<span>Unsafe</span>');
    });

    it('opens safe external links with an isolated browsing context', () => {
        const html = render('[Docs](https://example.com/docs)');
        expect(html).toContain('href="https://example.com/docs"');
        expect(html).toContain('target="_blank"');
        expect(html).toContain('rel="noopener noreferrer"');
    });
});
