import type { DayStateKind, MemberKind } from '@/components/FlatMarks'

/**
 * Bilingual content dictionary for ALIVE (`id` primary, `en` paired via `typeof id`).
 * Synchronized with Android Pass-2 Editorial Toybox sections; zero em dashes.
 */

export type HeroMemberEntry = {
  kind: MemberKind
  name: string
  note: string
}

export type DayStateEntry = {
  code: string
  label: string
  body: string
  pillClass: string
}

const id = {
  meta: {
    brand: 'ALIVE',
    tagline: 'Sadari waktumu.',
    skip: 'Lewati ke konten',
    toTop: 'Kembali ke atas'
  },

  nav: {
    menuLabel: 'Navigasi utama',
    toy: 'Simulasi',
    guardian: 'Guardian',
    privacy: 'Privasi',
    download: 'Unduh APK',
    langLabel: 'Bahasa'
  },

  hero: {
    eyebrow: 'Android · Offline · Tanpa Akun',
    title: ['Sadari waktumu.', 'Kendalikan layarmu.'],
    lead: 'HP-mu bukan musuhnya. Malam-malam yang habis tanpa kamu sadari, itu masalahnya.',
    body: 'ALIVE mencatat waktu hiburanmu, menjaga jatah harian yang kamu tetapkan saat masih jernih, dan menutup app-nya dengan tenang saat habis. Semua data tinggal di perangkatmu.',
    cta: 'Unduh ALIVE',
    secondaryCta: 'Janji privasi',
    ctaNote: 'alive.apk · ±5 MB · Android 8.0 ke atas',
    companionAlt: 'Karakter pendamping ALIVE berwarna pink lembut dengan mata kapsul hitam',
    familyHint: 'Klik karakter ALIVE untuk lihat perannya:',
    members: [
      { kind: 'study', name: 'Study', note: 'Tetes kuning emas untuk sesi belajar terfokus.' },
      { kind: 'reading', name: 'Reading', note: 'Kubah biru tenang bagi jam membaca tanpa feed.' },
      { kind: 'creative', name: 'Creative', note: 'Awan pink untuk bikin karya, bukan cuma konsumsi.' },
      { kind: 'coding', name: 'Coding', note: 'Heksagon ungu saat tenggelam merangkai kode.' },
      { kind: 'mindfulness', name: 'Mindfulness', note: 'Kapsul lilac untuk jeda napas dan refleksi malam.' },
      { kind: 'exercise', name: 'Exercise', note: 'Bola hijau segar mengantar waktu gerak fisik.' }
    ] as HeroMemberEntry[]
  },

  toybox: {
    badge: 'Simulasi batas harian',
    title: 'Hitung malammu. Lihat sisanya.',
    lead: 'Geser kebiasaan scroll malam dan batas jatahmu. Bentuk status harian ALIVE langsung merespons.',
    scrollLabel: 'Kebiasaan scroll hiburan per malam',
    limitLabel: 'Batas harian yang kamu pilih',
    realTag: 'Nyata',
    minutesUnit: 'menit',
    hoursUnit: 'jam',
    perMonthSuffix: '/ bulan waktu hidup kembali',
    zeroResultTitle: 'Batas harian terjaga utuh',
    zeroResultSub: 'Kebiasaanmu masih di bawah atau tepat pada jatah. Tidak ada menit yang bocor.',
    mathNote: 'Hitungan langsung: selisih menit harian x 30 hari apabila jatah dijaga konsisten.',
    barScrollLabel: 'Kebiasaan scroll',
    barLimitLabel: 'Batas jatah',
    restToggle: 'Simulasikan mode tidur malam (Rest)',
    statePanelLabel: 'Status ALIVE sekarang',
    states: {
      normal: {
        code: 'TERJAGA',
        label: 'Normal · Aman di jalur',
        body: 'Masih ada ruang napas nyaman. ALIVE diam di latar tanpa gangguan.',
        pillClass: 'bg-g-mint text-flat-ink'
      },
      near: {
        code: '30M TERAKHIR',
        label: 'Menjelang habis',
        body: 'Bintang kuning menyapa singkat di dalam app: 30 menit tersisa malam ini.',
        pillClass: 'bg-field-butter text-flat-ink'
      },
      spent: {
        code: 'JATAH HABIS',
        label: 'Tutup · Hiburan dikunci',
        body: 'Oktagon merah menutup app hiburan dengan tenang. Chat penting dan app kerja tetap aman.',
        pillClass: 'bg-red-pale text-flat-ink'
      },
      recovery: {
        code: 'PEMULIHAN',
        label: 'Time Bank & Pemulihan aktif',
        body: 'Pemakaian jauh melewati batas. Hari pemulihan dan Time Bank membantumu balik besok tanpa hukuman.',
        pillClass: 'bg-field-mint text-flat-ink'
      },
      rest: {
        code: 'ISTIRAHAT',
        label: 'Mode tidur malam aktif',
        body: 'Hari ditutup dengan tenang. Layar kembali sunyi supaya besok mulai jernih.',
        pillClass: 'bg-flat-soft text-flat-ink'
      }
    } as Record<DayStateKind, DayStateEntry>
  },

  specLedger: {
    proofLabel: 'Rekam layar satu malam nyata',
    proofFigure: '3 jam 33 menit',
    proofAllowance: '152% dari jatah hiburan',
    proofScreen: '4j 58m total layar',
    proofNote: 'Telepon, chat penting, dan peta tidak pernah memotong jatah. Hanya hiburan yang kamu batasi.',
    apps: [
      { name: 'Chrome', minutes: '1j 33m', share: 48 },
      { name: 'TikTok', minutes: '1j 12m', share: 34 },
      { name: 'WhatsApp', minutes: '43m (tak dipotong)', share: 18 }
    ],
    ledgerTitle: 'Cara ALIVE menahan tanpa terasa mengurung',
    rows: [
      {
        num: '01',
        name: 'Peringatan lembut di menit ke-30 terakhir',
        body: 'Isyarat sunyi muncul di app terbuka, bukan notifikasi agresif yang memicu kelelahan dopamin.'
      },
      {
        num: '02',
        name: 'Tutup otomatis saat jatah habis',
        body: 'Feed berhenti sendiri agar atensi tidak terus terpecah sampai larut. Chat keluarga & alat kerja tetap bebas.'
      },
      {
        num: '03',
        name: 'Time Bank & rantai pemulihan',
        body: 'Menit sisa dari hari tenang ditabung ke Time Bank. Terlewat satu malam tetap dihargai lewat hari pemulihan.'
      },
      {
        num: '04',
        name: 'Ruang pengganti setelah layar mati',
        body: 'Timer Fokus, Misi berbukti, Widget home screen, dan 11 warna keluarga ALIVE mengisi ruang kosongnya.'
      }
    ],
    catLabel: '12 hue kategori misi Android ALIVE'
  },

  guardianBand: {
    badge: 'Pendamping batas · Guardian',
    title: ['Jangan biarkan', 'Guardian keluar.'],
    lead: 'Selama janjimu utuh, Guardian diam di latar. Tameng crimson bermata kapsul ini baru maju saat penjagaan dimatikan atau batas dilangkahi.',
    poseGroupLabel: 'Pilih ekspresi Guardian',
    altPrefix: 'Karakter Guardian ALIVE berbentuk tameng crimson bermata kapsul hitam',
    poses: [
      {
        mood: 'idle' as const,
        label: 'Siaga',
        code: 'ACTIVE',
        title: 'Menjaga tenang di latar belakang.',
        body: 'Perlindungan aktif dan batasmu utuh. Nol gangguan.'
      },
      {
        mood: 'alert' as const,
        label: 'Waspada',
        code: 'DEGRADED',
        title: 'Perlindungan sedang dimatikan.',
        body: 'Guardian mencatat layanan aksesibilitas berhenti sebelum jatah bocor.'
      },
      {
        mood: 'recovery' as const,
        label: 'Pemulihan',
        code: 'RECOVERY',
        title: 'Batas terlewat tanpa pengawasan.',
        body: 'Kartu pemulihan muncul untuk mengajakmu balik ke jalur dengan sadar.'
      },
      {
        mood: 'restored' as const,
        label: 'Utuh lagi',
        code: 'RESTORED',
        title: 'Kembali menjaga setelah pemulihan.',
        body: 'Begitu perlindungan menyala lagi, rantai penjagaan langsung utuh.'
      }
    ],
    stepsTitle: 'Tiga langkah kehadirannya',
    places: [
      {
        name: 'Notifikasi persisten',
        body: 'Hadir tenang memberi tanda saat layanan aksesibilitas dimatikan.'
      },
      {
        name: 'Kartu pemulihan peach',
        body: 'Satu tombol langsung di Beranda untuk menyambung janji lagi.'
      },
      {
        name: 'Berdiri di muka layar',
        body: 'Hadir sebentar di depan app hiburan sampai kamu menutupnya sadar.'
      }
    ],
    honest: 'Jaga janjimu, dia tidak akan pernah mengganggu.'
  },

  privacyDownload: {
    privacyBadge: 'Privasi & Kendali Lokal',
    privacyTitle: 'Semua yang ALIVE tahu tetap di HP-mu.',
    privacyBody: 'Tanpa izin internet di AndroidManifest, tanpa akun, tanpa server, dan tanpa backup awan untuk riwayat harian.',
    privacyMarks: ['Tanpa akun', 'Tanpa server', 'Tanpa izin internet'],
    privacyLink: 'Baca Kebijakan Privasi lengkap',
    downloadBadge: 'Unduh Beta APK',
    title: 'Mulai kendalikan waktumu hari ini.',
    file: {
      name: 'alive.apk',
      size: '±5 MB',
      requires: 'Android 8.0 (API 26)+'
    },
    cta: 'Unduh APK Terbaru',
    honest: 'Beta APK dibagikan lewat GitHub Releases; Play Store sedang disiapkan.',
    permissions: [
      {
        name: 'Usage Access',
        body: 'Membaca total menit hiburan secara lokal.'
      },
      {
        name: 'Accessibility',
        body: 'Menutup app hiburan saat jatah selesai atau di jam Fokus.'
      }
    ]
  },

  footer: {
    line: 'ALIVE',
    sub: 'Sadari waktumu, kendalikan layarmu.',
    promise: 'Tanpa akun · tanpa server · tanpa izin internet.',
    legal: '© 2026 proyek independen tentang atensi',
    links: 'Tautan',
    privacy: 'Kebijakan Privasi',
    contact: 'Kontak'
  }
}

const en: typeof id = {
  meta: {
    brand: 'ALIVE',
    tagline: 'Know your time.',
    skip: 'Skip to content',
    toTop: 'Back to top'
  },

  nav: {
    menuLabel: 'Primary navigation',
    toy: 'Simulator',
    guardian: 'Guardian',
    privacy: 'Privacy',
    download: 'Download APK',
    langLabel: 'Language'
  },

  hero: {
    eyebrow: 'Android · Offline · No Account',
    title: ['Know your time.', 'Own your screen.'],
    lead: 'Your phone is not the enemy. The evenings that vanish unnoticed are.',
    body: 'ALIVE counts entertainment screen time, keeps the daily allowance you chose with a clear head, and closes those apps calmly when spent. Everything stays on-device.',
    cta: 'Download ALIVE',
    secondaryCta: 'Privacy promise',
    ctaNote: 'alive.apk · ~5 MB · Android 8.0+',
    companionAlt: 'Soft pink ALIVE companion character with black capsule eyes',
    familyHint: 'Click an ALIVE character to inspect its role:',
    members: [
      { kind: 'study', name: 'Study', note: 'Warm gold teardrop built for focused study blocks.' },
      { kind: 'reading', name: 'Reading', note: 'Calm sky dome marking feed-free reading hours.' },
      { kind: 'creative', name: 'Creative', note: 'Blossom pink cloud for making things instead of scrolling.' },
      { kind: 'coding', name: 'Coding', note: 'Indigo hexagon for deep unbroken build sessions.' },
      { kind: 'mindfulness', name: 'Mindfulness', note: 'Soft lilac capsule for evening quiet and reflection.' },
      { kind: 'exercise', name: 'Exercise', note: 'Fresh green circle cheering real-world movement.' }
    ]
  },

  toybox: {
    badge: 'Daily allowance simulator',
    title: 'Measure your evening. See what returns.',
    lead: 'Adjust your nightly scroll habit and chosen limit. Watch ALIVE’s daily state respond live.',
    scrollLabel: 'Current nightly entertainment scroll',
    limitLabel: 'Daily limit you set yourself',
    realTag: 'Real log',
    minutesUnit: 'min',
    hoursUnit: 'hr',
    perMonthSuffix: '/ month reclaimed',
    zeroResultTitle: 'Allowance stays intact',
    zeroResultSub: 'Your scroll time fits within your chosen boundary. Zero wasted hours.',
    mathNote: 'Transparent math: (daily scroll minus daily cap) x 30 days when the limit holds.',
    barScrollLabel: 'Nightly scroll',
    barLimitLabel: 'Daily allowance',
    restToggle: 'Preview night rest mode (Rest)',
    statePanelLabel: 'Current ALIVE state',
    states: {
      normal: {
        code: 'INTACT',
        label: 'Normal · Steady on track',
        body: 'Plenty of allowance left. ALIVE stays completely invisible in the background.',
        pillClass: 'bg-g-mint text-flat-ink'
      },
      near: {
        code: 'LAST 30M',
        label: 'Running low',
        body: 'Yellow star gives a quiet in-app cue when 30 minutes remain.',
        pillClass: 'bg-field-butter text-flat-ink'
      },
      spent: {
        code: 'SPENT',
        label: 'Closed · Entertainment locked',
        body: 'Red octagon closes capped feeds cleanly while chats, calls, and work apps remain open.',
        pillClass: 'bg-red-pale text-flat-ink'
      },
      recovery: {
        code: 'RECOVERY',
        label: 'Time Bank & Recovery active',
        body: 'Well past budget. Recovery days and Time Bank help reset tomorrow without harsh penalties.',
        pillClass: 'bg-field-mint text-flat-ink'
      },
      rest: {
        code: 'RESTING',
        label: 'Night rest mode active',
        body: 'Day closes peacefully so tomorrow begins unclouded.',
        pillClass: 'bg-flat-soft text-flat-ink'
      }
    }
  },

  specLedger: {
    proofLabel: 'Real evening log snapshot',
    proofFigure: '3 h 33 min',
    proofAllowance: '152% of entertainment cap',
    proofScreen: '4h 58m total screen',
    proofNote: 'Calls, important chats, and maps never drain your allowance. Only apps you choose to cap.',
    apps: [
      { name: 'Chrome', minutes: '1h 33m', share: 48 },
      { name: 'TikTok', minutes: '1h 12m', share: 34 },
      { name: 'WhatsApp', minutes: '43m (never billed)', share: 18 }
    ],
    ledgerTitle: 'How ALIVE holds the line without nagging',
    rows: [
      {
        num: '01',
        name: 'Quiet 30-minute heads-up inside the feed',
        body: 'Appears in context instead of noisy notifications, slowing dopamine fatigue early.'
      },
      {
        num: '02',
        name: 'Smooth shut-off when allowance ends',
        body: 'Entertainment stops automatically while messaging, calls, and work tools keep working.'
      },
      {
        num: '03',
        name: 'Time Bank & streak recovery',
        body: 'Unused calm minutes save into Time Bank for heavier days without punishing streak wipes.'
      },
      {
        num: '04',
        name: 'Room for reclaimed hours',
        body: 'Focus timers, proof-based Missions, launcher Widget, and 11 family hues fill the space.'
      }
    ],
    catLabel: '12 Android ALIVE mission category hues'
  },

  guardianBand: {
    badge: 'Boundary Keeper · Guardian',
    title: ['Don’t let the', 'Guardian step out.'],
    lead: 'As long as you keep your own limit, Guardian stays quiet. This crimson capsule-eyed shield only steps forward when protection drops or caps are broken.',
    poseGroupLabel: 'Pick Guardian expression',
    altPrefix: 'ALIVE Guardian character shaped like a crimson shield with black capsule eyes',
    poses: [
      {
        mood: 'idle' as const,
        label: 'Ready',
        code: 'ACTIVE',
        title: 'Watching calmly in the background.',
        body: 'Protection is running and your limit holds. Zero interruptions.'
      },
      {
        mood: 'alert' as const,
        label: 'Alert',
        code: 'DEGRADED',
        title: 'Protection switched off.',
        body: 'Guardian notices accessibility access stopped before minutes slip.'
      },
      {
        mood: 'recovery' as const,
        label: 'Recovery',
        code: 'RECOVERY',
        title: 'Cap crossed unguarded.',
        body: 'Shows a gentle recovery prompt to bring your limit back.'
      },
      {
        mood: 'restored' as const,
        label: 'Restored',
        code: 'RESTORED',
        title: 'Protection back intact.',
        body: 'Once access returns, your streak continuity resumes right away.'
      }
    ],
    stepsTitle: 'Three steps when triggered',
    places: [
      {
        name: 'Persistent status cue',
        body: 'Surfaces calmly if accessibility service is disabled.'
      },
      {
        name: 'Peach recovery card',
        body: 'One-tap recovery prompt directly on your home board.'
      },
      {
        name: 'Brief screen step-in',
        body: 'Stands calmly over restricted apps until you close them.'
      }
    ],
    honest: 'Keep your boundary and it stays completely out of sight.'
  },

  privacyDownload: {
    privacyBadge: 'Privacy & Local Control',
    privacyTitle: 'Everything ALIVE knows stays on your phone.',
    privacyBody: 'Zero internet permission in AndroidManifest, zero accounts, zero servers, and daily logs excluded from cloud backups.',
    privacyMarks: ['No accounts', 'No servers', 'No internet permission'],
    privacyLink: 'Read full Privacy Policy',
    downloadBadge: 'Download Beta APK',
    title: 'Take back your evenings today.',
    file: {
      name: 'alive.apk',
      size: '~5 MB',
      requires: 'Android 8.0 (API 26)+'
    },
    cta: 'Download Latest APK',
    honest: 'Beta APK distributed via GitHub Releases; Google Play listing is in progress.',
    permissions: [
      {
        name: 'Usage Access',
        body: 'Counts daily entertainment minutes strictly locally.'
      },
      {
        name: 'Accessibility',
        body: 'Closes capped apps cleanly at limit end or during Focus.'
      }
    ]
  },

  footer: {
    line: 'ALIVE',
    sub: 'Know your time. Own your screen.',
    promise: 'No accounts · no servers · no internet permission.',
    legal: '© 2026 an independent project on attention',
    links: 'Links',
    privacy: 'Privacy Policy',
    contact: 'Contact'
  }
}

export type Lang = 'id' | 'en'
export type Copy = typeof id

export const dictionaries: Record<Lang, Copy> = { id, en }
export const defaultLang: Lang = 'id'

export const langs: { code: Lang; label: string }[] = [
  { code: 'id', label: 'ID' },
  { code: 'en', label: 'EN' }
]
