import {IconFocusCentered, IconSitemap, IconZoomIn, IconZoomOut} from '../../icons';
import {useReducedMotion} from '@mantine/hooks';
import {Background, BackgroundVariant, getViewportForBounds, type Node, Panel, useReactFlow, useStoreApi} from '@xyflow/react';
import {useCallback, useEffect, useRef} from 'react';
import type {Positions} from '../../lib/graphLayout';
import {MOTION} from '../../lib/motion';
import {ARRANGE_MOTION, tween} from '../../lib/tween';
import {IconAction} from '../actions/IconAction';
import {ToolbarButton} from '../actions/ToolbarButton';
import './Graph.css';
import {COLOR} from '../../lib/colors';

export const GRAPH_MOTION = MOTION.slow;
const ZOOM_MOTION = MOTION.base;
const FALLBACK_SIZE = {width: 200, height: 52};

const FIT_PADDING = 0.15;
const GRID_GAP = 20;
const GRID_DOT = 1.2;
export const SNUG_FIT_PADDING = 0.06;

export function CanvasControls({padding = FIT_PADDING, maxZoom = 1, onArrange, onFit}: {
    padding?: number;
    maxZoom?: number;
    onArrange?: () => void;
    onFit?: () => void;
}) {
    const flow = useReactFlow();
    const fit = onFit ?? (() => void flow.fitView({padding, maxZoom, duration: GRAPH_MOTION}));
    return (
        <Panel position="bottom-right" className="df-graph-controls">
            {onArrange && (
                <>
                    <ToolbarButton label="Arrange" icon={IconSitemap} quiet hint="Lay the graph out again, neatly"
                                   onClick={onArrange}/>
                    <span className="df-graph-controls-divider"/>
                </>
            )}
            <IconAction label="Zoom out" icon={IconZoomOut} onClick={() => void flow.zoomOut({duration: ZOOM_MOTION})}/>
            <IconAction label="Zoom in" icon={IconZoomIn} onClick={() => void flow.zoomIn({duration: ZOOM_MOTION})}/>
            <IconAction label="Fit to view" icon={IconFocusCentered} onClick={fit}/>
        </Panel>
    );
}

export function useArrange(padding = FIT_PADDING, maxZoom = 1) {
    const flow = useReactFlow();
    const store = useStoreApi();
    const reduced = useReducedMotion();
    const cancel = useRef<() => void>(undefined);
    useEffect(() => () => cancel.current?.(), []);
    return useCallback((targets: Positions, apply: (positions: Positions) => void, done?: () => void) => {
        cancel.current?.();
        const duration = reduced ? 0 : ARRANGE_MOTION;
        const nodes = flow.getNodes();
        const from = Object.fromEntries(nodes.map((node) => [node.id, node.position]));
        const boxes = nodes.filter((node) => targets[node.id]).map((node) => ({
            ...targets[node.id],
            width: node.measured?.width ?? FALLBACK_SIZE.width,
            height: node.measured?.height ?? FALLBACK_SIZE.height,
        }));
        if (boxes.length) {
            const left = Math.min(...boxes.map((box) => box.x));
            const top = Math.min(...boxes.map((box) => box.y));
            const right = Math.max(...boxes.map((box) => box.x + box.width));
            const bottom = Math.max(...boxes.map((box) => box.y + box.height));
            const {width, height, minZoom} = store.getState();
            const viewport = getViewportForBounds({x: left, y: top, width: right - left, height: bottom - top}, width, height,
                minZoom, maxZoom, padding);
            void flow.setViewport(viewport, {duration});
        }
        cancel.current = tween(from, targets, duration, apply, () => {
            cancel.current = undefined;
            done?.();
        });
    }, [flow, store, reduced, padding, maxZoom]);
}

export function ArrangeControls({targets, onMove, onDone, onFit, padding = FIT_PADDING, maxZoom = 1}: {
    targets: Positions;
    onMove: (positions: Positions) => void;
    onDone?: () => void;
    onFit?: () => void;
    padding?: number;
    maxZoom?: number;
}) {
    const arrange = useArrange(padding, maxZoom);
    return <CanvasControls padding={padding} maxZoom={maxZoom} onFit={onFit} onArrange={() => arrange(targets, onMove, onDone)}/>;
}

export function movedTo<T extends Node>(moved: Positions) {
    return (nodes: T[]) => nodes.map((node) => (moved[node.id] ? {...node, position: moved[node.id]} : node));
}

export function RefitOnChange({shape, padding = FIT_PADDING, maxZoom = 1}: { shape: string; padding?: number; maxZoom?: number }) {
    const flow = useReactFlow();
    const first = useRef(true);
    useEffect(() => {
        if (first.current) {
            first.current = false;
            return undefined;
        }
        const frame = requestAnimationFrame(() => void flow.fitView({padding, maxZoom, duration: GRAPH_MOTION}));
        return () => cancelAnimationFrame(frame);
    }, [flow, shape, padding, maxZoom]);
    return null;
}

export function GraphBackground() {
    return <Background variant={BackgroundVariant.Dots} gap={GRID_GAP} size={GRID_DOT} color={COLOR.graphDot}/>;
}
