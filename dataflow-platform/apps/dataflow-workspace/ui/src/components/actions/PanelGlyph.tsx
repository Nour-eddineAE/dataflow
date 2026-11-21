import './PanelGlyph.css';

export type PanelSide = 'left' | 'right' | 'bottom';

const SHAPES: Record<PanelSide, { panel: [number, number, number, number]; edge: string; chevron: string }> = {
    left: {panel: [3.8, 4.8, 5.2, 14.4], edge: 'M9 4v16', chevron: 'M16 9l-3 3l3 3'},
    right: {panel: [15, 4.8, 5.2, 14.4], edge: 'M15 4v16', chevron: 'M8 9l3 3l-3 3'},
    bottom: {panel: [3.8, 15, 16.4, 4.2], edge: 'M3 15h18', chevron: 'M9 8l3 3l3 -3'},
};

export function PanelGlyph({side, collapsed, size = 16}: { side: PanelSide; collapsed: boolean; size?: number }) {
    const {panel: [x, y, width, height], edge, chevron} = SHAPES[side];
    return (
        <svg className="df-panel-glyph" data-side={side} data-collapsed={collapsed || undefined} width={size} height={size}
             viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round"
             aria-hidden>
            <rect x={3} y={4} width={18} height={16} rx={2}/>
            <rect className="df-panel-glyph-panel" x={x} y={y} width={width} height={height} rx={1.2} fill="currentColor" stroke="none"/>
            <path className="df-panel-glyph-edge" d={edge}/>
            <path className="df-panel-glyph-chevron" d={chevron}/>
        </svg>
    );
}
