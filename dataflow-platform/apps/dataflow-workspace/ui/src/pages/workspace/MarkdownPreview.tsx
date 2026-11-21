import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

export function MarkdownPreview({text}: {text: string}) {
    return (
        <article className="df-markdown-preview" aria-label="Markdown preview">
            <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml components={{
                a: ({href, children, title}) => href
                    ? <a href={href} title={title} target={href.startsWith('#') ? undefined : '_blank'} rel="noopener noreferrer">{children}</a>
                    : <span>{children}</span>,
            }}>{text}</ReactMarkdown>
        </article>
    );
}
