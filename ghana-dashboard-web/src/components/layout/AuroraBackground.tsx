/** Fixed, non-interactive animated aurora + perspective grid that sits behind the whole app. */
export function AuroraBackground() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden bg-surface">
      <div
        className="absolute -left-[15%] -top-[25%] h-[70vh] w-[70vh] animate-aurora rounded-full blur-3xl"
        style={{ background: 'radial-gradient(circle, var(--aurora-a), transparent 65%)' }}
      />
      <div
        className="absolute -right-[10%] top-[10%] h-[60vh] w-[60vh] animate-aurora-slow rounded-full blur-3xl"
        style={{ background: 'radial-gradient(circle, var(--aurora-c), transparent 65%)' }}
      />
      <div
        className="absolute -bottom-[25%] left-[25%] h-[60vh] w-[70vh] animate-aurora rounded-full blur-3xl"
        style={{ background: 'radial-gradient(circle, var(--aurora-b), transparent 65%)' }}
      />
      <div
        className="absolute inset-0 opacity-[0.35] dark:opacity-[0.5]"
        style={{
          backgroundImage:
            'linear-gradient(var(--line) 1px, transparent 1px), linear-gradient(90deg, var(--line) 1px, transparent 1px)',
          backgroundSize: '56px 56px',
          maskImage: 'radial-gradient(ellipse at 50% 0%, #000 0%, transparent 70%)',
          WebkitMaskImage: 'radial-gradient(ellipse at 50% 0%, #000 0%, transparent 70%)',
        }}
      />
    </div>
  )
}
