import type { Metadata } from 'next'
import { HourglassMark, ShieldMark } from '@/components/FlatMarks'
import { basePath, brand, contactEmail } from '@/lib/site'

export const metadata: Metadata = {
  title: 'Kebijakan Privasi',
  description:
    'ALIVE tidak punya server dan tidak meminta izin internet. Ini halaman yang menjelaskan apa yang ada di HP-mu, izin apa yang diminta dan untuk apa, serta bagaimana menghapusnya.',
  alternates: { canonical: '/privacy/' },
  openGraph: {
    title: `Kebijakan Privasi | ${brand}`,
    description:
      'Tanpa akun, tanpa server, tanpa izin internet. Apa yang disimpan ALIVE, dan bagaimana menghapusnya.',
    url: '/privacy/',
    type: 'website'
  }
}

const effective = '11 Oktober 2026'
const effectiveEn = '11 October 2026'

function Block({
  label,
  as: Heading = 'h2',
  children
}: {
  label: string
  as?: 'h2' | 'h3'
  children: React.ReactNode
}) {
  return (
    <section className="flat-tile-white p-6">
      <Heading className="font-head text-lg font-bold text-flat-ink">{label}</Heading>
      <div className="mt-3 space-y-3 text-[15px] leading-relaxed text-ink-secondary">{children}</div>
    </section>
  )
}

export default function PrivacyPage() {
  return (
    <>
      <a href="#main-content" className="skip-link">
        Lewati ke konten / Skip to content
      </a>

      <header className="sticky top-0 z-50 border-b border-black/[0.06] bg-flat-ground/95 backdrop-blur-sm">
        <div className="mx-auto flex w-full max-w-5xl items-center gap-2.5 px-4 py-2.5 sm:px-6 lg:px-10">
          <a
            href={`${basePath}/`}
            className="inline-flex min-h-[44px] shrink-0 items-center gap-2.5 rounded-pill px-2 transition-opacity hover:opacity-80"
          >
            <HourglassMark size={22} />
            <span className="font-display text-lg font-bold tracking-[-0.03em] text-flat-ink">
              {brand}
            </span>
          </a>

          <nav aria-label="Navigasi kebijakan privasi" className="ml-auto flex items-center gap-1 sm:gap-2">
            <a href="#privasi-id" className="nav-link">
              ID
            </a>
            <a href="#privacy-en" className="nav-link">
              EN
            </a>
            <a href={`${basePath}/`} className="pill-btn pill-btn-primary ml-1 px-4 py-2 text-sm">
              Beranda
            </a>
          </nav>
        </div>
      </header>

      <main id="main-content" tabIndex={-1} className="mx-auto w-full max-w-3xl px-4 py-12 outline-none sm:px-6 md:py-16">
        <section id="privasi-id" aria-labelledby="privacy-id-heading">
          <div className="alive-hero-enter flat-card bg-g-mint p-6 md:p-8">
            <div className="flex items-start justify-between gap-4">
              <div>
                <span className="eyebrow-pill bg-field-mint text-flat-ink">
                  Tanpa akun · Tanpa server
                </span>
                <h1 id="privacy-id-heading" className="mt-4 font-display text-display-sm text-flat-ink">
                  Kebijakan Privasi
                </h1>
                <p className="mt-2 text-sm font-medium text-ink-secondary">Berlaku sejak {effective}</p>
              </div>
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-field-mint">
                <ShieldMark size={24} />
              </span>
            </div>
          </div>

          <div className="mt-8 space-y-5">
            <Block label="Yang tidak terjadi">
              <p>
                ALIVE tidak punya akun, tidak punya server, dan tidak meminta izin internet.
                Aplikasi ini secara teknis tidak bisa mengirim apa pun keluar dari HP-mu, bukan
                karena kami berjanji tidak akan, tapi karena jalannya memang tidak ada.
              </p>
              <p>
                Tidak ada analytics, tidak ada iklan, tidak ada SDK pihak ketiga yang mengumpulkan
                data, dan tidak ada laporan crash yang dikirim otomatis.
              </p>
            </Block>

            <Block label="Yang ada di HP-mu">
              <p>
                Semua ini disimpan di folder privat aplikasi, tempat hanya ALIVE dan sistem yang
                bisa membaca:
              </p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Perkiraan durasi pemakaian app hiburan per hari, dan total layar harian.</li>
                <li>Batas hiburan yang kamu tetapkan, sisa jatah, dan hari yang sudah dihitung.</li>
                <li>Sesi fokus, daftar app yang boleh dipakai, misi harian dan bukti yang kamu catat.</li>
                <li>Pengaturan tampilan dan status layar sambutan.</li>
              </ul>
              <p>
                Yang dihitung hanya app yang benar-benar ALIVE tutup atau awasi. Isi chat, isi
                layar, dan nama file-mu tidak dibaca sama sekali.
              </p>
            </Block>

            <Block label="Izin yang diminta, dan untuk apa">
              <ul className="list-disc space-y-2 pl-5">
                <li>
                  <strong className="text-flat-ink">Akses penggunaan:</strong> membaca durasi app di layar, supaya jatah
                  harian bisa dihitung. Izin ini kamu berikan sendiri di pengaturan sistem dan bisa
                  kamu cabut kapan pun.
                </li>
                <li>
                  <strong className="text-flat-ink">Aksesibilitas:</strong> menutup app hiburan saat jatah habis. Ini izin
                  yang paling kuat di antara semuanya, karena dia membuat ALIVE tahu app mana yang
                  sedang terbuka. Karena itu juga dia hanya aktif kalau kamu menyalakannya, dan dia
                  tidak membaca isi layar.
                </li>
                <li>
                  <strong className="text-flat-ink">Tampil di atas app lain:</strong> dipakai hanya saat perlindungan sedang
                  mati, untuk menaruh satu kartu pemulihan di atas app yang sedang kamu buka.
                </li>
                <li>
                  <strong className="text-flat-ink">Notifikasi:</strong> satu notifikasi saja, yang menagih perlindungan yang
                  kamu matikan.
                </li>
                <li>
                  <strong className="text-flat-ink">Aktivitas fisik (penghitung langkah):</strong> hanya untuk misi yang memang
                  membuktikannya. Tidak dipakai untuk melacak lokasi, dan lokasi tidak diminta.
                </li>
              </ul>
            </Block>

            <Block label="Cadangan bawaan Android">
              <p>
                Kalau kamu menyalakan cadangan perangkat di sistem Android, pengaturan ALIVE bisa
                ikut tercadang ke akun Google-mu. Itu fitur sistem, bukan ALIVE yang meng-upload.
                Riwayat pemakaian per app sengaja dikecualikan dari cadangan itu, jadi angka harimu
                tidak ikut ke mana-mana. Kamu bisa mematikan cadangan kapan pun di pengaturan sistem.
              </p>
            </Block>

            <Block label="Menghapus semuanya">
              <p>
                Copot aplikasinya, atau buka Pengaturan &rarr; Aplikasi &rarr; ALIVE &rarr;
                Penyimpanan &rarr; Hapus data. Tidak ada salinan di server kami untuk dihapus,
                karena tidak ada server.
              </p>
            </Block>

            <Block label="Untuk siapa aplikasi ini">
              <p>
                ALIVE dibuat untuk orang dewasa yang mengatur layarnya sendiri, dan tidak
                ditargetkan untuk anak-anak.
              </p>
            </Block>

            <Block label="Perubahan dan pertanyaan">
              <p>
                Kalau halaman ini berubah, tanggal di atas ikut berubah. Pertanyaan soal privasi
                bisa dikirim ke{' '}
                <a
                  href={`mailto:${contactEmail}`}
                  className="font-semibold text-flat-ink underline decoration-2 underline-offset-4 hover:text-ink-secondary"
                >
                  {contactEmail}
                </a>
                .
              </p>
            </Block>
          </div>
        </section>

        <hr className="my-12 border-black/[0.08]" />

        <section id="privacy-en" lang="en" aria-labelledby="privacy-en-heading">
          <div className="flat-card bg-g-sky p-6 md:p-8">
            <span className="eyebrow-pill bg-field-sky text-flat-ink">Local-only · Zero servers</span>
            <h2 id="privacy-en-heading" className="mt-4 font-display text-display-sm text-flat-ink">
              Privacy Policy
            </h2>
            <p className="mt-2 text-sm font-medium text-ink-secondary">Effective {effectiveEn}</p>
          </div>

          <div className="mt-8 space-y-5">
            <Block as="h3" label="What does not happen">
              <p>
                ALIVE has no account and no server, and it does not ask for internet permission. The
                app cannot send anything off your phone, not because we promise not to, but because
                there is no route to do it.
              </p>
              <p>
                No analytics, no advertising, no third-party SDK that collects data, and no crash
                report sent automatically.
              </p>
            </Block>

            <Block as="h3" label="What is on your phone">
              <p>
                All of this sits in the app&apos;s private folder, where only ALIVE and the system
                can read it:
              </p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Estimated entertainment app usage per day, and the daily screen total.</li>
                <li>The limit you set, the allowance left, and the days already counted.</li>
                <li>
                  Focus sessions, the apps a session allows, daily missions and the evidence you
                  log.
                </li>
                <li>Display settings and the state of the welcome screens.</li>
              </ul>
              <p>
                Only apps ALIVE actually closes or watches are counted. Chat contents, screen
                contents and your file names are never read.
              </p>
            </Block>

            <Block as="h3" label="The permissions it asks for, and why">
              <ul className="list-disc space-y-2 pl-5">
                <li>
                  <strong className="text-flat-ink">Usage access:</strong> reads how long an app is on screen so the daily
                  allowance can be counted. You grant it in system settings and can revoke it
                  anytime.
                </li>
                <li>
                  <strong className="text-flat-ink">Accessibility:</strong> closes entertainment apps when the allowance is
                  gone. It is the strongest permission here, because it lets ALIVE know which app is
                  open. That is why it is only on when you switch it on, and why it never reads what
                  is on the screen.
                </li>
                <li>
                  <strong className="text-flat-ink">Display over other apps:</strong> used only while protection is off, to
                  put one recovery card over whatever you have open.
                </li>
                <li>
                  <strong className="text-flat-ink">Notifications:</strong> one notification, which asks for the protection
                  you switched off.
                </li>
                <li>
                  <strong className="text-flat-ink">Physical activity (step counter):</strong> only for missions that prove
                  it. It is not used for location, and location is never requested.
                </li>
              </ul>
            </Block>

            <Block as="h3" label="Android&apos;s own backup">
              <p>
                If you have device backup on in Android settings, ALIVE&apos;s preferences may be
                backed up to your own Google account. That is a system feature, not ALIVE uploading
                anything. The per-app usage history is deliberately excluded from that backup, so
                your day&apos;s numbers do not travel. You can switch backup off anytime in system
                settings.
              </p>
            </Block>

            <Block as="h3" label="Deleting everything">
              <p>
                Uninstall the app, or open Settings &rarr; Apps &rarr; ALIVE &rarr; Storage &rarr;
                Clear data. There is no copy on our servers to delete, because there are no servers.
              </p>
            </Block>

            <Block as="h3" label="Who this is for">
              <p>
                ALIVE is built for adults managing their own screen, and is not directed at children.
              </p>
            </Block>

            <Block as="h3" label="Changes and questions">
              <p>
                When this page changes, the date above changes with it. Privacy questions go to{' '}
                <a
                  href={`mailto:${contactEmail}`}
                  className="font-semibold text-flat-ink underline decoration-2 underline-offset-4 hover:text-ink-secondary"
                >
                  {contactEmail}
                </a>
                .
              </p>
            </Block>
          </div>
        </section>
      </main>

      <footer className="border-t border-black/[0.07] bg-flat-tile px-4 py-10 sm:px-6 lg:px-10">
        <div className="mx-auto grid w-full max-w-3xl gap-8 md:grid-cols-2">
          <div>
            <div className="flex items-center gap-2.5">
              <HourglassMark size={22} />
              <span className="font-display text-lg font-bold text-flat-ink">{brand}</span>
            </div>
            <p className="mt-3 text-sm text-ink-secondary">
              Tanpa akun · tanpa server · tanpa izin internet.
            </p>
          </div>

          <nav aria-label="Tautan kebijakan privasi" className="md:justify-self-end md:text-right">
            <h2 className="font-head text-xs font-bold uppercase tracking-wider text-ink-secondary">
              Tautan
            </h2>
            <ul className="mt-2 space-y-1 text-sm">
              <li>
                <a href={`${basePath}/`} className="footer-link">
                  Beranda ALIVE
                </a>
              </li>
              <li>
                <a href={`mailto:${contactEmail}`} className="footer-link">
                  {contactEmail}
                </a>
              </li>
              <li>
                <a href="#main-content" className="footer-link">
                  Kembali ke atas
                </a>
              </li>
            </ul>
          </nav>
        </div>
      </footer>
    </>
  )
}
