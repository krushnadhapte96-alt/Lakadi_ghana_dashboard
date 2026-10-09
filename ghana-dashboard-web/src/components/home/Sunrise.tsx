import type { SVGProps } from 'react'

/**
 * The greeting illustration: a sun climbing over a mustard field. Hand-drawn rather than
 * iconographic so the first thing a shopkeeper sees at 6am is warm, not corporate.
 *
 * Motion is CSS-only (see the `animate-*` classes in tailwind.config) so it costs nothing
 * beyond a compositor transform, and it stops dead under `prefers-reduced-motion`.
 */
export function Sunrise({ className }: Pick<SVGProps<SVGSVGElement>, 'className'>) {
  return (
    <svg viewBox="0 0 200 150" fill="none" className={className} role="img" aria-hidden="true">
      <defs>
        <linearGradient id="sunrise-sky" x1="100" y1="0" x2="100" y2="110" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--color-gold-300)" stopOpacity="0.55" />
          <stop offset="1" stopColor="var(--color-gold-300)" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="sunrise-field" x1="100" y1="78" x2="100" y2="150" gradientUnits="userSpaceOnUse">
          <stop stopColor="var(--color-gold-500)" />
          <stop offset="1" stopColor="var(--color-gold-700)" />
        </linearGradient>
        <clipPath id="sunrise-clip">
          <rect x="0" y="0" width="200" height="150" rx="14" />
        </clipPath>
      </defs>

      <g clipPath="url(#sunrise-clip)">
        <rect width="200" height="150" fill="var(--color-surface-3)" />
        <rect width="200" height="110" fill="url(#sunrise-sky)" />

        {/* Sun: rises and settles, once, on mount. */}
        <g className="animate-sunrise-rise motion-reduce:animate-none">
          <circle cx="100" cy="96" r="26" fill="var(--color-gold-400)" opacity="0.28" />
          <circle cx="100" cy="96" r="17" fill="var(--color-gold-500)" />
          <path
            d="M84 88a16 16 0 0 0 16 12"
            stroke="var(--color-gold-200)"
            strokeWidth="3"
            strokeLinecap="round"
            opacity="0.7"
          />
        </g>

        {/* Rays sweep slowly - the only looping motion, so it stays unobtrusive. */}
        <g
          className="origin-[100px_96px] animate-sun-rays motion-reduce:animate-none"
          stroke="var(--color-gold-600)"
          strokeWidth="2.5"
          strokeLinecap="round"
          opacity="0.5"
        >
          <path d="M100 62v-9" />
          <path d="M132 78l6.5-6.5" />
          <path d="M68 78l-6.5-6.5" />
          <path d="M140 96h9" />
          <path d="M60 96h-9" />
        </g>

        {/* Distant treeline. */}
        <path
          d="M0 92c14-10 22 2 34-4s18-12 30-6 20 12 32 6 16-14 28-10 22 14 34 10 24-10 42-4v14H0Z"
          fill="var(--color-brand-700)"
          opacity="0.35"
        />

        {/* Field. */}
        <path d="M0 96h200v54H0Z" fill="url(#sunrise-field)" />

        {/* Stalks sway out of phase with each other. */}
        <g stroke="var(--color-gold-200)" strokeWidth="2" strokeLinecap="round" opacity="0.55">
          {[
            { x: 26, d: 1.6 },
            { x: 52, d: 2.4 },
            { x: 78, d: 1.1 },
            { x: 104, d: 2.0 },
            { x: 130, d: 1.4 },
            { x: 156, d: 2.7 },
            { x: 180, d: 1.8 },
          ].map((stalk) => (
            <path
              key={stalk.x}
              d={`M${stalk.x} 150V${108 + (stalk.x % 17)}`}
              style={{ animationDelay: `${stalk.d}s` }}
              className="origin-bottom animate-stalk-sway motion-reduce:animate-none"
            />
          ))}
        </g>

        {/* Two birds drifting across. */}
        <g
          stroke="var(--color-brand-800)"
          strokeWidth="2"
          strokeLinecap="round"
          fill="none"
          opacity="0.55"
        >
          <path
            d="M36 34c4-4 8-4 11 0 3-4 7-4 11 0"
            className="animate-bird-drift motion-reduce:animate-none"
          />
          <path
            d="M104 20c3-3 6-3 8 0 2-3 5-3 8 0"
            style={{ animationDelay: '3.5s' }}
            className="animate-bird-drift motion-reduce:animate-none"
          />
        </g>
      </g>
    </svg>
  )
}
