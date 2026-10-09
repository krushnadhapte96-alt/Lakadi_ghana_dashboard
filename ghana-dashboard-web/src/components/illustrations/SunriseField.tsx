import type { SVGProps } from 'react'

/**
 * Home-screen hero: the sun coming up over a mustard field.
 *
 * <p>Decorative only, so it is hidden from assistive tech. The sun and its glow drift with
 * `animate-sunrise-rise` / `animate-sunrise-glow`, and the field rows sway with `animate-field-sway`
 * at different durations and delays so the crop never moves as a single block. The global
 * `prefers-reduced-motion` rule in `globals.css` flattens all three, so the illustration still
 * reads as a sunrise when animation is turned off.
 */
export function SunriseField(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 200 120" fill="none" aria-hidden="true" {...props}>
      <defs>
        <linearGradient id="sunrise-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--color-gold-100)" />
          <stop offset="70%" stopColor="var(--color-gold-50)" />
        </linearGradient>
        <linearGradient id="sunrise-field" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--color-gold-500)" />
          <stop offset="100%" stopColor="var(--color-gold-700)" />
        </linearGradient>
      </defs>

      {/* sky */}
      <path d="M0 84 L200 84 L200 0 L0 0 Z" fill="url(#sunrise-sky)" />

      {/* sun glow, then the disc lifting through it */}
      <circle
        cx="100"
        cy="66"
        r="34"
        fill="var(--color-gold-300)"
        className="origin-center animate-sunrise-glow motion-reduce:animate-none"
      />
      <g className="animate-sunrise-rise motion-reduce:animate-none">
        <circle cx="100" cy="66" r="19" fill="var(--color-gold-400)" />
        <circle cx="100" cy="66" r="13" fill="var(--color-gold-500)" />
      </g>

      {/* mustard rows: staggered sway durations keep the field from pulsing in unison */}
      <g className="animate-field-sway motion-reduce:animate-none" style={{ animationDuration: '7s' }}>
        <path d="M0 88 L200 88 L200 96 L0 96 Z" fill="var(--color-gold-300)" />
      </g>
      <g
        className="animate-field-sway motion-reduce:animate-none"
        style={{ animationDuration: '8.5s', animationDelay: '-1.2s' }}
      >
        <path d="M0 96 L200 96 L200 106 L0 106 Z" fill="var(--color-gold-400)" />
      </g>
      <g
        className="animate-field-sway motion-reduce:animate-none"
        style={{ animationDuration: '9.5s', animationDelay: '-2.6s' }}
      >
        <path d="M0 106 L200 106 L200 120 L0 120 Z" fill="url(#sunrise-field)" />
      </g>

      {/* a few stalks so the rows read as a crop rather than stripes */}
      <g className="stroke-gold-700" strokeWidth="1.5" strokeLinecap="round" opacity="0.55">
        <path d="M26 96 L26 89" />
        <path d="M58 96 L58 90" />
        <path d="M132 96 L132 89" />
        <path d="M170 96 L170 90" />
      </g>
    </svg>
  )
}
