import {
    ArchiveIcon,
    ArrowClockwiseIcon,
    ArrowCounterClockwiseIcon,
    ArrowDownIcon,
    ArrowElbowDownLeftIcon,
    ArrowLeftIcon,
    ArrowLineRightIcon,
    ArrowSquareInIcon,
    ArrowSquareOutIcon,
    ArrowUUpLeftIcon,
    ArrowUUpRightIcon,
    ArrowUpIcon,
    ArrowsLeftRightIcon,
    ArrowsMergeIcon,
    BellIcon,
    BellRingingIcon,
    BookOpenIcon,
    BookmarkSimpleIcon,
    BroomIcon,
    CaretDownIcon,
    CaretRightIcon,
    ChartBarIcon,
    CheckIcon,
    CheckCircleIcon,
    CircleDashedIcon,
    ClockIcon,
    ClockCounterClockwiseIcon,
    CloudArrowDownIcon,
    CodeIcon,
    CoffeeIcon,
    ColumnsIcon,
    ColumnsPlusRightIcon,
    CopyIcon,
    CopySimpleIcon,
    CrosshairIcon,
    CursorTextIcon,
    DatabaseIcon,
    DesktopIcon,
    DotsNineIcon,
    DotsThreeIcon,
    DownloadSimpleIcon,
    EnvelopeSimpleIcon,
    ExportIcon,
    EyeIcon,
    FileIcon,
    FileCodeIcon,
    FileSqlIcon,
    FileTextIcon,
    FilesIcon,
    FloppyDiskIcon,
    FlowArrowIcon,
    FolderIcon,
    FoldersIcon,
    FunnelIcon,
    GearIcon,
    GitBranchIcon,
    GitCommitIcon,
    GitDiffIcon,
    GithubLogoIcon,
    HandIcon,
    HardDrivesIcon,
    HeartbeatIcon,
    HouseIcon,
    KeyIcon,
    KeyboardIcon,
    LinkSimpleIcon,
    ListBulletsIcon,
    ListChecksIcon,
    ListNumbersIcon,
    LockIcon,
    MagicWandIcon,
    MagnifyingGlassIcon,
    MagnifyingGlassMinusIcon,
    MagnifyingGlassPlusIcon,
    MarkdownLogoIcon,
    MinusIcon,
    MinusSquareIcon,
    MoonIcon,
    PauseIcon,
    PencilIcon,
    PencilSimpleIcon,
    PlayIcon,
    PlusIcon,
    PulseIcon,
    RocketIcon,
    RowsIcon,
    ShieldCheckIcon,
    ShieldPlusIcon,
    ShieldWarningIcon,
    SigmaIcon,
    SignOutIcon,
    SlidersHorizontalIcon,
    SquaresFourIcon,
    StarIcon,
    StopIcon,
    SunIcon,
    SwapIcon,
    TableIcon,
    TerminalWindowIcon,
    UploadSimpleIcon,
    TrashIcon,
    TreeStructureIcon,
    UniteIcon,
    UserIcon,
    UsersIcon,
    UsersThreeIcon,
    WarningIcon,
    WarningCircleIcon,
    XIcon,
    XCircleIcon,
    type Icon as Glyph,
    type IconWeight,
} from '@phosphor-icons/react';
import type {ComponentType, CSSProperties} from 'react';

export type IconProps = {
    size?: number | string;
    color?: string;
    className?: string;
    style?: CSSProperties;
    weight?: IconWeight;
};

export type Icon = ComponentType<IconProps>;

function glyph(Base: Glyph, preset: IconWeight = 'regular'): Icon {
    return function Drawn({weight, ...props}: IconProps) {
        return <Base weight={weight ?? preset} {...props}/>;
    };
}

export const IconActivity = glyph(PulseIcon);
export const IconAdjustmentsHorizontal = glyph(SlidersHorizontalIcon);
export const IconAlertCircle = glyph(WarningCircleIcon);
export const IconAlertTriangle = glyph(WarningIcon);
export const IconArrowBackUp = glyph(ArrowUUpLeftIcon);
export const IconArrowBarToRight = glyph(ArrowLineRightIcon);
export const IconArrowDown = glyph(ArrowDownIcon);
export const IconArrowForwardUp = glyph(ArrowUUpRightIcon);
export const IconArrowLeft = glyph(ArrowLeftIcon);
export const IconArrowUp = glyph(ArrowUpIcon);
export const IconArrowsDiff = glyph(ArrowsLeftRightIcon);
export const IconArrowsJoin = glyph(ArrowsMergeIcon);
export const IconBell = glyph(BellIcon);
export const IconBellCheck = glyph(BellRingingIcon);
export const IconBook2 = glyph(BookOpenIcon);
export const IconBookmark = glyph(BookmarkSimpleIcon);
export const IconBrandGit = glyph(GitDiffIcon);
export const IconBrandGithub = glyph(GithubLogoIcon);
export const IconBucket = glyph(ArchiveIcon);
export const IconChartBar = glyph(ChartBarIcon);
export const IconCheck = glyph(CheckIcon);
export const IconChecklist = glyph(ListChecksIcon);
export const IconChevronDown = glyph(CaretDownIcon);
export const IconChevronRight = glyph(CaretRightIcon);
export const IconCircleCheck = glyph(CheckCircleIcon);
export const IconCircleCheckFilled = glyph(CheckCircleIcon, 'fill');
export const IconCircleDashed = glyph(CircleDashedIcon);
export const IconCircleX = glyph(XCircleIcon);
export const IconCircleXFilled = glyph(XCircleIcon, 'fill');
export const IconClearAll = glyph(BroomIcon);
export const IconClock = glyph(ClockIcon);
export const IconCloudDownload = glyph(CloudArrowDownIcon);
export const IconCode = glyph(CodeIcon);
export const IconCoffee = glyph(CoffeeIcon);
export const IconColumnInsertRight = glyph(ColumnsPlusRightIcon);
export const IconColumnRemove = glyph(MinusSquareIcon);
export const IconColumns = glyph(ColumnsIcon);
export const IconComponents = glyph(SquaresFourIcon);
export const IconCopy = glyph(CopyIcon);
export const IconCopyOff = glyph(CopySimpleIcon);
export const IconCornerDownLeft = glyph(ArrowElbowDownLeftIcon);
export const IconCursorText = glyph(CursorTextIcon);
export const IconDatabase = glyph(DatabaseIcon);
export const IconDatabaseExport = glyph(ExportIcon);
export const IconDatabaseImport = glyph(ArrowSquareInIcon);
export const IconDeviceDesktop = glyph(DesktopIcon);
export const IconDeviceFloppy = glyph(FloppyDiskIcon);
export const IconDots = glyph(DotsThreeIcon);
export const IconDownload = glyph(DownloadSimpleIcon);
export const IconEdit = glyph(PencilSimpleIcon);
export const IconExternalLink = glyph(ArrowSquareOutIcon);
export const IconEye = glyph(EyeIcon);
export const IconFile = glyph(FileIcon);
export const IconFileCode = glyph(FileCodeIcon);
export const IconFileText = glyph(FileTextIcon);
export const IconFiles = glyph(FilesIcon);
export const IconFilter = glyph(FunnelIcon);
export const IconFocusCentered = glyph(CrosshairIcon);
export const IconFolder = glyph(FolderIcon);
export const IconFolders = glyph(FoldersIcon);
export const IconGitBranch = glyph(GitBranchIcon);
export const IconGitCommit = glyph(GitCommitIcon);
export const IconGridDots = glyph(DotsNineIcon);
export const IconHandStop = glyph(HandIcon);
export const IconHeartbeat = glyph(HeartbeatIcon);
export const IconHistory = glyph(ClockCounterClockwiseIcon);
export const IconHome = glyph(HouseIcon);
export const IconKey = glyph(KeyIcon);
export const IconKeyboard = glyph(KeyboardIcon);
export const IconLayersUnion = glyph(UniteIcon);
export const IconLink = glyph(LinkSimpleIcon);
export const IconListDetails = glyph(ListBulletsIcon);
export const IconListNumbers = glyph(ListNumbersIcon);
export const IconLock = glyph(LockIcon);
export const IconLogout = glyph(SignOutIcon);
export const IconMail = glyph(EnvelopeSimpleIcon);
export const IconMarkdown = glyph(MarkdownLogoIcon);
export const IconMinus = glyph(MinusIcon);
export const IconMoon = glyph(MoonIcon);
export const IconPencil = glyph(PencilIcon);
export const IconPlayerPause = glyph(PauseIcon);
export const IconPlayerPlay = glyph(PlayIcon);
export const IconPlayerPlayFilled = glyph(PlayIcon, 'fill');
export const IconPlayerStop = glyph(StopIcon);
export const IconPlus = glyph(PlusIcon);
export const IconRefresh = glyph(ArrowClockwiseIcon);
export const IconUpload = glyph(UploadSimpleIcon);
export const IconRestore = glyph(ArrowCounterClockwiseIcon);
export const IconRocket = glyph(RocketIcon);
export const IconRoute = glyph(FlowArrowIcon);
export const IconSearch = glyph(MagnifyingGlassIcon);
export const IconServer = glyph(HardDrivesIcon);
export const IconSettings = glyph(GearIcon);
export const IconShieldCheck = glyph(ShieldCheckIcon);
export const IconShieldPlus = glyph(ShieldPlusIcon);
export const IconShieldX = glyph(ShieldWarningIcon);
export const IconSitemap = glyph(TreeStructureIcon);
export const IconSql = glyph(FileSqlIcon);
export const IconStar = glyph(StarIcon);
export const IconStarFilled = glyph(StarIcon, 'fill');
export const IconSum = glyph(SigmaIcon);
export const IconSun = glyph(SunIcon);
export const IconTable = glyph(TableIcon);
export const IconTableImport = glyph(RowsIcon);
export const IconTerminal2 = glyph(TerminalWindowIcon);
export const IconTransform = glyph(SwapIcon);
export const IconTrash = glyph(TrashIcon);
export const IconUser = glyph(UserIcon);
export const IconUsers = glyph(UsersIcon);
export const IconUsersGroup = glyph(UsersThreeIcon);
export const IconWand = glyph(MagicWandIcon);
export const IconX = glyph(XIcon);
export const IconZoomIn = glyph(MagnifyingGlassPlusIcon);
export const IconZoomOut = glyph(MagnifyingGlassMinusIcon);
