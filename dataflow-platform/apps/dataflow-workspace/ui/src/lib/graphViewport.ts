export type Bounds = { x: number; y: number; width: number; height: number };
export type Viewport = { x: number; y: number; zoom: number };
export type Fit = { minZoom: number; maxZoom: number; padding: number; edge: number };

export function readableViewport(bounds: Bounds, width: number, height: number, fit: Fit): Viewport {
    const room = 1 + 2 * fit.padding;
    const wanted = Math.min(width / (bounds.width * room), height / (bounds.height * room));
    const zoom = Math.min(fit.maxZoom, Math.max(fit.minZoom, wanted));
    const fitsAcross = bounds.width * zoom <= width;
    const fitsDown = bounds.height * zoom <= height;
    return {
        x: fitsAcross ? (width - bounds.width * zoom) / 2 - bounds.x * zoom : fit.edge - bounds.x * zoom,
        y: fitsDown ? (height - bounds.height * zoom) / 2 - bounds.y * zoom : fit.edge - bounds.y * zoom,
        zoom,
    };
}
