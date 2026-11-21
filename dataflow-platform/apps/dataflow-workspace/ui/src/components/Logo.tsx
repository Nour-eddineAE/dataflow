import {useId} from 'react';

const WORDMARK = 'M.9 12a4.1 4.1 0 1 0 8.2 0a4.1 4.1 0 1 0-8.2 0M9.1 2.9V16.1'
    + 'M12.6 12a4.1 4.1 0 1 0 8.2 0a4.1 4.1 0 1 0-8.2 0M20.8 7.9V16.1'
    + 'M25.7 3.9V13.6a2.5 2.5 0 0 0 2.5 2.5h.6M23.7 7.9H28.4'
    + 'M31.3 12a4.1 4.1 0 1 0 8.2 0a4.1 4.1 0 1 0-8.2 0M39.5 7.9V16.1'
    + 'M47.4 2.9h-.5a2.5 2.5 0 0 0-2.5 2.5V16.1M42.4 7.9H47.1'
    + 'M50.4 2.9V16.1'
    + 'M53.9 12a4.1 4.1 0 1 0 8.2 0a4.1 4.1 0 1 0-8.2 0'
    + 'M64.9 7.9L67.6 16.1L70.3 7.9L73 16.1L75.7 7.9';

export function Logo() {
    const gradient = `df-mark-${useId().replace(/:/g, '')}`;
    return (
        <svg width={99} height={22} viewBox="0 0 108 24" aria-hidden className="df-logo">
            <defs>
                <linearGradient id={gradient} x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stopColor="#7a88ea"/>
                    <stop offset="1" stopColor="#3f4bb4"/>
                </linearGradient>
            </defs>
            <rect width={24} height={24} rx={6.5} fill={`url(#${gradient})`}/>
            <path d="M6 8C10.8 8 9.6 12 12 12s1.2 4 6 4" fill="none" stroke="#ffffff" strokeOpacity={0.9} strokeWidth={1.7}
                  strokeLinecap="round"/>
            <circle cx={6} cy={8} r={2.2} fill="#d39a66" stroke="#ffffff" strokeWidth={1}/>
            <circle cx={12} cy={12} r={2.2} fill="#c9d1dc" stroke="#ffffff" strokeWidth={1}/>
            <circle cx={18} cy={16} r={2.2} fill="#f0c34a" stroke="#ffffff" strokeWidth={1}/>
            <path d={WORDMARK} transform="translate(31.4 .6)" fill="none" stroke="currentColor" strokeWidth={1.8}
                  strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
    );
}
