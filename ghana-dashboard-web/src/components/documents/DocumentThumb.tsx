import type { DocumentItem, DocumentKind } from '@/api/documents'
import { useFileUrl } from '@/components/documents/useObjectUrl'
import { Icon } from '@/utils/icons'

export function DocumentThumb({ document }: { document: DocumentItem }) {
  const url = useFileUrl(document.id)

  return (
    <div className="relative flex aspect-4/3 items-center justify-center overflow-hidden rounded-card border border-line bg-surface-3">
      {document.kind === 'IMAGE' && url ? (
        <img
          src={url}
          alt={document.title}
          loading="lazy"
          className="size-full object-cover"
        />
      ) : document.kind === 'IMAGE' ? (
        <div className="flex flex-col items-center gap-1.5 text-ink-soft">
          <Icon name="image" className="size-6" />
          <span className="text-[10px]">...</span>
        </div>
      ) : (
        <KindBadge kind={document.kind} label={document.fileType} />
      )}

      {document.kind === 'VIDEO' && (
        <span className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-black/55 text-white">
            <svg viewBox="0 0 24 24" fill="currentColor" className="size-6 translate-x-0.5">
              <path d="M8 5v14l11-7z" />
            </svg>
          </span>
        </span>
      )}

      <span className="pointer-events-none absolute inset-x-0 bottom-0 truncate bg-gradient-to-t from-black/70 to-transparent px-2 pb-1.5 pt-4 text-[11px] font-medium text-white">
        {document.title}
      </span>
    </div>
  )
}

export function KindBadge({ kind, label }: { kind: DocumentKind | string; label?: string }) {
  const icon = kind === 'VIDEO' ? 'file' : kind === 'PDF' ? 'file' : 'image'
  return (
    <div className="flex flex-col items-center gap-1.5 px-3 text-center">
      <Icon name={icon} className="size-8 text-ink-soft" />
      {label && (
        <span className="truncate text-[10px] uppercase tracking-wide text-ink-soft">{label}</span>
      )}
    </div>
  )
}
