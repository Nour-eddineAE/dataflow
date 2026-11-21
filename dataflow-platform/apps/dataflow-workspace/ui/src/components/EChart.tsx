import {BarChart, LineChart} from 'echarts/charts';
import {GridComponent, LegendComponent, TooltipComponent} from 'echarts/components';
import * as echarts from 'echarts/core';
import {CanvasRenderer} from 'echarts/renderers';
import {useEffect, useEffectEvent, useRef} from 'react';

echarts.use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

export function EChart({option, height = 240, onSelect}: {
    option: Record<string, unknown>;
    height?: number | string;
    onSelect?: (name: string) => void;
}) {
    const element = useRef<HTMLDivElement>(null);
    const chart = useRef<echarts.ECharts>(undefined);
    const select = useEffectEvent((name: string) => onSelect?.(name));

    useEffect(() => {
        const instance = echarts.init(element.current!, undefined, {renderer: 'canvas'});
        chart.current = instance;
        instance.on('click', (event) => select(String(event.name)));
        const observer = new ResizeObserver(() => instance.resize());
        observer.observe(element.current!);
        return () => {
            observer.disconnect();
            instance.dispose();
        };
    }, []);

    useEffect(() => {
        chart.current?.setOption(option, true);
    }, [option]);

    return <div ref={element} style={{height, width: '100%'}}/>;
}
