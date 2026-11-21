import type {ReactNode} from 'react';
import './Panel.css';

export function Panel({title, actions, children, flush, framed}: {
    title?: ReactNode;
    actions?: ReactNode;
    children: ReactNode;
    flush?: boolean;
    framed?: boolean;
}) {
    return (
        <section className="df-section" data-framed={framed || undefined} data-flush={flush || undefined}>
            {(title || actions) && (
                <header className="df-section-head">
                    {title && <h2 className="df-section-title">{title}</h2>}
                    {actions && <div className="df-section-actions">{actions}</div>}
                </header>
            )}
            <div className="df-section-body">{children}</div>
        </section>
    );
}
