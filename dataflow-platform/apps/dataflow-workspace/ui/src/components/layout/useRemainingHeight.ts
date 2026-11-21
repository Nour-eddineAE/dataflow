import {useCallback, useState} from 'react';

const FLOOR = 380;
const PAGE_FOOT = 56;

export const remainingHeight = (viewport: number, top: number) => Math.max(FLOOR, Math.round(viewport - top - PAGE_FOOT));

export function useRemainingHeight(): [(element: HTMLElement | null) => (() => void) | undefined, number] {
    const [height, setHeight] = useState(FLOOR);
    const measure = useCallback((element: HTMLElement | null) => {
        if (!element) return undefined;
        const fit = () => setHeight(remainingHeight(window.innerHeight, element.getBoundingClientRect().top));
        fit();
        window.addEventListener('resize', fit);
        return () => window.removeEventListener('resize', fit);
    }, []);
    return [measure, height];
}
