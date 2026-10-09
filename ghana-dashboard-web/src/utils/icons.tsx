import {
  LayoutDashboard,
  ShoppingCart,
  Package,
  CreditCard,
  BarChart3,
  Calculator,
  Sprout,
  Factory,
  AlertTriangle,
  Undo2,
  FileText,
  Users,
  Home,
  Menu,
  X,
  Sun,
  Moon,
  Bell,
  Search,
  Settings,
  User,
  LogOut,
  MoreVertical,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  TrendingUp,
  TrendingDown,
  Minus,
  Plus,
  Pencil,
  Trash2,
  Eye,
  EyeOff,
  Download,
  Upload,
  Filter,
  List,
  Grid3X3,
  Calendar,
  Clock,
  Check,
  XCircle,
  AlertCircle,
  Image,
  File,
  Loader2,
  Receipt,
  Truck,
  Wallet,
  Scale,
  Phone,
  MessageCircle,
  MapPin,
  StickyNote,
} from 'lucide-react'

export type IconName =
  | 'dashboard'
  | 'sales'
  | 'sale'
  | 'inventory'
  | 'stock'
  | 'payments'
  | 'pnl'
  | 'expenses'
  | 'costing'
  | 'beej'
  | 'production'
  | 'expiry'
  | 'returns'
  | 'khata'
  | 'customers'
  | 'documents'
  | 'licences'
  | 'insights'
  | 'settings'
  | 'home'
  | 'more'
  | 'password'
  | 'logout'
  | 'menu'
  | 'close'
  | 'sun'
  | 'moon'
  | 'bell'
  | 'search'
  | 'user'
  | 'chevron-left'
  | 'chevron-right'
  | 'chevron-down'
  | 'chevron-up'
  | 'trending-up'
  | 'trending-down'
  | 'minus'
  | 'plus'
  | 'pencil'
  | 'trash'
  | 'eye'
  | 'eye-off'
  | 'download'
  | 'upload'
  | 'filter'
  | 'list'
  | 'grid'
  | 'calendar'
  | 'clock'
  | 'check'
  | 'x-circle'
  | 'alert-circle'
  | 'image'
  | 'file'
  | 'loader'
  | 'truck'
  | 'wallet'
  | 'scale'
  | 'phone'
  | 'message'
  | 'map-pin'
  | 'note'

const iconMap: Record<IconName, React.ComponentType<{ className?: string }>> = {
  dashboard: LayoutDashboard,
  sales: ShoppingCart,
  sale: ShoppingCart,
  inventory: Package,
  stock: Package,
  payments: CreditCard,
  pnl: BarChart3,
  expenses: Receipt,
  costing: Calculator,
  beej: Sprout,
  production: Factory,
  expiry: AlertTriangle,
  returns: Undo2,
  khata: FileText,
  customers: Users,
  documents: FileText,
  licences: FileText,
  insights: BarChart3,
  settings: Settings,
  home: Home,
  more: MoreVertical,
  password: User,
  logout: LogOut,
  menu: Menu,
  close: X,
  sun: Sun,
  moon: Moon,
  bell: Bell,
  search: Search,
  user: User,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'chevron-down': ChevronDown,
  'chevron-up': ChevronUp,
  'trending-up': TrendingUp,
  'trending-down': TrendingDown,
  minus: Minus,
  plus: Plus,
  pencil: Pencil,
  trash: Trash2,
  eye: Eye,
  'eye-off': EyeOff,
  download: Download,
  upload: Upload,
  filter: Filter,
  list: List,
  grid: Grid3X3,
  calendar: Calendar,
  clock: Clock,
  check: Check,
  'x-circle': XCircle,
  'alert-circle': AlertCircle,
  image: Image,
  file: File,
  loader: Loader2,
  truck: Truck,
  wallet: Wallet,
  scale: Scale,
  phone: Phone,
  message: MessageCircle,
  'map-pin': MapPin,
  note: StickyNote,
}

export function Icon({ name, className }: { name: IconName; className?: string }) {
  const Component = iconMap[name]
  if (!Component) return null
  return <Component className={className} />
}

// eslint-disable-next-line react-refresh/only-export-components
export { iconMap }

