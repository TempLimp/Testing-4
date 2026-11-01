# Flow for iOS — parity checklist

Generated from upstream `77ae9d9` by S01. Status: `todo`, `done` (with evidence), `iOS limit` (with reason), `skipped`.
A session may only change lines for its own session id. S22 requires zero `todo`.

## 1. Upstream package coverage

| Package | Files | Lines | Session | Status | Evidence |
|---|---|---|---|---|---|
| `FlowApplication.kt` | 1 | 319 | S13 | todo | |
| `MainActivity.kt` | 1 | 549 | S13 | todo | |
| `StartupWarmups.kt` | 1 | 97 | S13 | todo | |
| `TvActivity.kt` | 1 | 18 | skip | skipped | |
| `data/audio` | 9 | 1257 | S08 | todo | |
| `data/backup` | 6 | 871 | S19 | todo | |
| `data/comments` | 2 | 329 | S07 | todo | |
| `data/download` | 4 | 546 | S18 | todo | |
| `data/engagement` | 5 | 420 | S08 | todo | |
| `data/feed` | 1 | 67 | S07 | todo | |
| `data/innertube/SubscriptionSweepFeed.kt` | 1 | 78 | S03 | done | Verbatim apart from the clock. Its logic is exercised through RssSubscriptionServiceTest upstream, which moves to S07 with that file. |
| `data/innertube/RssSubscriptionService.kt` | 1 | 560 | S07 | todo | Moved from S03: it is built on `data/subscriptions` (ChannelRssClient, ChannelUploadsClient) and `data/shorts` (ChannelReelIndex), both S07, and its only consumer is SubscriptionFeedRepository (S07). RssSubscriptionServiceTest (mockk) moves with it. |
| `data/local` (all but ThumbnailQuality.kt) | 85 | 15258 | S06 | todo | S04 pulled forward PlayerPreferences.kt's `VideoQuality`, `VideoCodec` and `MusicAudioQuality` enums, which stream selection reads, into `data/local/PlayerPreferences.kt`; S06 ports the rest of that file around them, unchanged. |
| `data/local/ThumbnailQuality.kt` | 1 | 22 | S03 | done | Pulled forward: ThumbnailUrlResolver (S03) needs it. Verbatim; ThumbnailQualityTiersTest. |
| `data/localmedia` | 23 | 2024 | S19 | todo | `LocalMediaIds.kt`'s id helpers (PREFIX, isLocal, of, mediaStoreId) were ported in S03 for ThumbnailUrlResolver; its `videoUri`/`audioUri` (MediaStore content URIs) are left to S19. |
| `data/lyrics` | 30 | 3864 | S08 | todo | |
| `data/migration` | 1 | 76 | S19 | todo | |
| `data/model` (all but Models.kt, RichText.kt, SponsorBlockSegment.kt) | 10 | 387 | S08 | todo | |
| `data/model/SponsorBlockSegment.kt` | 1 | 13 | S04 | done | Pulled forward: `player/stream/ResolvedPlayback` carries it. Gson's `@SerializedName` becomes kotlinx.serialization's `@SerialName`, same names. |
| `data/model/Models.kt`, `RichText.kt` | 2 | 215 | S03 | done | Pulled forward: the InnerTube pages build Video, Channel, Playlist, Comment and RichText. `Comment.repliesPage` (NewPipe's `Page`) is dropped: the InnerTube path pages replies by `continuationToken`. Covered by the innertube page tests. |
| `data/music` | 12 | 1420 | S08 | todo | |
| `data/newmusic` | 1 | 551 | S08 | todo | |
| `data/notes` | 2 | 227 | S08 | todo | |
| `data/paging` | 6 | 534 | S07 | todo | |
| `data/playlist` | 3 | 310 | S07 | todo | |
| `data/profile` | 21 | 1878 | S08 | todo | |
| `data/recognition` | 10 | 965 | S21 | todo | |
| `data/recommendation` | 64 | 20446 | S09 | todo | |
| `data/repository` | 12 | 2444 | S07 | todo | |
| `data/scrobble` | 9 | 1218 | S08 | todo | |
| `data/search` | 1 | 25 | S07 | todo | |
| `data/shorts` | 27 | 3016 | S07 | todo | |
| `data/stats` | 10 | 1587 | S08 | todo | |
| `data/subscriptions` | 14 | 1782 | S07 | todo | |
| `data/transcript` | 2 | 284 | S07 | todo | |
| `data/update` | 6 | 433 | skip | skipped | |
| `data/video` | 59 | 7398 | S07 | todo | |
| `di` | 8 | 563 | S13 | todo | |
| `discord` | 3 | 92 | skip | skipped | |
| `extensions` | 1 | 133 | S12 | todo | Moved from S03: PlayerExtensions.kt extends Media3's `Player` (play/pause, repeat, queue windows); its only consumer is Media3MusicService. iOS replaces Media3 with AVPlayer behind EnhancedMusicPlayerManager (S12). |
| `innertube` | 4 | 4117 | S03 | done | 4/4. OkHttp engine → `network/HttpEngine` (NSURLSession on iOS, CIO on jvm); `YouTube.proxy` carries an AppProxyConfig instead of a java.net.Proxy plus a header; `getMediaInfo` takes `rytdEnabled` instead of reading PlayerPreferences (S08's InnertubeMusicService passes it). NewPipe's `YoutubeParsingHelper.getClientVersion` is ported as `innertube/WebClientVersion.kt`. Tests: YouTubeSearchParamsTest, LiveInnerTubeSmokeTest (music search, music browse, video search with the site's client version), CI run 41056924212 (jvm 514 run, iOS simulator 505 run, 0 failed; live API reached from both, no block). |
| `innertube/models` | 78 | 4897 | S03 | done | 78/78, verbatim apart from Base64 (kotlin.io.encoding). Tests: YouTubeClientTest, YouTubeLocaleTest, NextBodyTest, AudioXTagsTest, AudioFormatMetadataTest, ChannelVideosResponseTest, LockupViewModelMappingTest, PlayerAttestationTest, PlayerResponseFieldsTest, VideoChaptersParserTest, VideoHeatmapParserTest. |
| `innertube/pages` | 58 | 7734 | S03 | done | 58/58. CompactNumberSuffixes reads `utils/CompactNumbers` (android.icu's CompactDecimalFormat has no Foundation counterpart); YouTubeCountParser drops BigDecimal and Character.UnicodeScript. Every upstream page test is ported; the 39 that read `explore/` and `shorts/` fixtures are `@Ignore`d, as upstream skips them (those fixtures are gitignored there). RichTextTargetsTest's rendering case moves to S13 with RichTextRendering. |
| `innertube/utils` | 1 | 96 | S03 | done | SHA-1 through Ktor's `sha1`. |
| `network` | 4 | 337 | S03 | done | OkHttp → Ktor. A Ktor client's proxy is fixed when it is built, so upstream's per-connection ProxySelector and pool eviction become ProxyAwareClient rebuilding per proxy signature, with a direct client for local hosts (AppProxySelector). VpnStateMonitor and NetworkState read NWPathMonitor on iOS (`network/NetworkPath`). Tests: AppProxyConfigTest, AppProxySelectorTest (common), AppProxyConfigJvmTest (the jvm-only unresolved-address cases), ProxyAwareClientTest, NetworkStateTest. Proxy use on iOS: see section 7. |
| `notification` | 14 | 2126 | S11, S18 | todo | |
| `platform` | 5 | 219 | S13 | todo | |
| `player` | 47 | 7905 | S10 | todo | |
| `player/analytics` | 2 | 117 | S10 | todo | |
| `player/audio` (all but LoudnessGain.kt) | 5 | 775 | S12 | todo | |
| `player/audio/LoudnessGain.kt` | 1 | 25 | S04 | done | Pulled forward: MusicPlayerUtils reports a track's loudness gain with it. Verbatim. |
| `player/cache` | 3 | 392 | S10 | todo | |
| `player/config` | 2 | 231 | S04 | done | Pulled forward: QualityManager and AutoQualityPolicy read PlayerConfig (verbatim). VideoSizeCap keeps `forHeap`; its Media3 `DefaultTrackSelector` extensions (`resetVideoSizeTo`, `adaptiveTrackSelectorDefaults`) work on `VideoTrackParameters`, the parameters the iOS track selector applies (AdaptiveTrackSelectorDefaultsTest ported on it). `forDevice(Context)` (ActivityManager's memory class) is the player's to decide on iOS (S10). |
| `player/datasource` (all but GoogleVideoRequestPolicy.kt) | 5 | 547 | S10 | todo | The loopback HLS proxy (`player/resolver/LocalHlsProxy`, S04) already fetches googlevideo the way YouTubeHttpDataSource does: the minting client's user agent and headers, and the same 403 log line. |
| `player/datasource/GoogleVideoRequestPolicy.kt` | 1 | 66 | S04 | done | Pulled forward for LocalHlsProxy's googlevideo requests. Verbatim. |
| `player/dlna` | 4 | 1549 | S21 | todo | |
| `player/error` (all but PlayerDiagnostics.kt, StreamDenialClassifier.kt) | 5 | 1413 | S10 | todo | |
| `player/error/PlayerDiagnostics.kt` | 1 | 239 | S04, S10 | todo | S04 ported the log ring buffer every extractor writes to (`log`, `logInfo`, `logWarning`, `logError`, `logRefocusGlitch`, `clear`; SimpleDateFormat becomes kotlinx-datetime). `logPlaybackError` (Media3's PlaybackException), `buildReport` and `copyToClipboard` read the player manager, device and clipboard, so S10 adds them. |
| `player/error/StreamDenialClassifier.kt` | 1 | 100 | S04 | done | Pulled forward: ClientGateTracker and MusicPlayerUtils classify refused URLs with it. URLDecoder becomes Ktor's decoder. |
| `player/factory` | 2 | 355 | S10 | todo | |
| `player/media` | 2 | 680 | S11 | todo | |
| `player/musicvideo` | 6 | 453 | S10 | todo | |
| `player/preload` | 1 | 302 | S10 | todo | |
| `player/quality` | 4 | 787 | S04 | done | AdaptiveLadder, AutoQualityPolicy, LiveQualitySelection verbatim. QualityManager reads a `BandwidthMeter` and a `VideoTrackSelector` (`player/quality/VideoTrackSelector.kt`) where upstream takes Media3's DefaultBandwidthMeter and DefaultTrackSelector; the AVPlayer implementations (access log bandwidth, preferred resolution and peak bit rate) come with the player manager in S10. Tests: AdaptiveLadderTest, AutoQualityPolicyTest, LiveQualitySelectionTest, AdaptiveTrackSelectorDefaultsTest (on VideoTrackParameters), CI run 41215728299 (jvm 751 run, iOS simulator 741 run, 0 failed). |
| `player/recovery` | 3 | 251 | S10 | todo | |
| `player/renderer` | 4 | 522 | S10 | todo | |
| `player/resolver` | 4 | 673 | S04 | done | AVPlayer reads no DASH, so the resolver keeps upstream's priority order and logs but hands AVPlayer HLS: `ManifestGenerator` describes a stream as an `HlsRendition` (URL, init and sidx ranges, codec, bandwidth, size) under NewPipe's creator rules, `AdaptiveDashManifest` builds the quality ladder as the variants of one master, `VideoPlaybackResolver` publishes video plus audio as one master (upstream's MergingMediaSource) to `LocalHlsProxy`, a Ktor CIO server on 127.0.0.1 that writes each media playlist from the stream's sidx and forwards byte ranges to googlevideo. `MediaSourceBuilder` builds HLS and progressive sources; `buildDashSource` has no counterpart. Live plays YouTube's own HLS manifest. Streams AVPlayer cannot decode are filtered out (`PlatformCodecSupport`, section 7). Tests: AdaptiveDashManifestTest (rungs compared with the single-stream rendition instead of NewPipe's MPD), HlsPlaylistsTest, LivePlaybackChainTest (live: master, media playlist and an fMP4 segment through the proxy, jvm and iOS), LocalHlsFfmpegJvmTest (ffmpeg decodes 10 s of the proxied HLS), CI run 41215728299 (jvm 751 run, iOS simulator 741 run, 0 failed). |
| `player/sabr` (all but the files below) | 15 | 3174 | S05 | todo | Until S05, a SABR session resolves (`tryWebSabr`) but plays through its deciphered direct formats, upstream's own fallback; nothing routes playback into SABR. |
| `player/sabr` SabrRoutingPolicy.kt, core/SabrCpn.kt, core/SabrBase64.kt, integration/SabrClientIdentity.kt, integration/SabrUrlResolver.kt | 5 | 452 | S04 | done | Pulled forward: InnerTubeVideoStreamExtractor's ladder decides and resolves SABR sessions with them (pure). SecureRandom becomes random UUID bytes, okio base64 Kotlin's, android.net.Uri `utils/Uri`. S05 must not port them again. |
| `player/sabr/proto/ProtobufReader.kt` | 1 | 100 | S03 | done | Pulled forward: ArtistDiscographyParams (S03) reads protobuf with it. Verbatim apart from UTF-8 decoding. |
| `player/service` | 1 | 67 | S11 | todo | |
| `player/shorts` | 7 | 1013 | S15 | todo | |
| `player/sponsorblock` | 1 | 290 | S10 | todo | |
| `player/state` | 3 | 109 | S04 | done | Pulled forward: StreamProcessor and QualityManager build its options. Verbatim. |
| `player/stream` (all but PlaybackLoadResolver.kt) | 26 | 3372 | S04 | done | NewPipe's stream types are ported under their own packages (`org.schabi.newpipe.extractor`: MediaFormat, Stream, VideoStream, AudioStream, DeliveryMethod, AudioTrackType, StreamType, ItagItem with its itag table, ParsingException), so the files keep their imports. android.net.Uri, SystemClock, ConcurrentHashMap and URLEncoder go to `utils/Uri`, TimeSource, Ktor's collections and atomicfu locks. The ladder runs unchanged; the music path skips formats AVPlayer cannot decode (section 7). Tests: every upstream test of the package except the two that move with PlaybackLoadResolver, CI run 41215728299 (jvm 751 run, iOS simulator 741 run, 0 failed); live: LivePlaybackChainTest. |
| `player/stream/PlaybackLoadResolver.kt` | 1 | 287 | S10 | todo | Moved from S04: its five constructor dependencies are YouTubeRepository and SponsorBlockRepository (S07), ViewHistory and PlayerPreferences (S06) and VideoDownloadManager (S07/S18), and its only consumer is VideoPlayerViewModel (S14). PlaybackLoadResolverTest (mockk) and PlaybackReadinessRulesTest (its companion's rules) move with it. |
| `player/subtitle` | 4 | 340 | S10 | todo | |
| `player/surface` | 2 | 314 | S10 | todo | |
| `player/tracker` | 1 | 149 | S10 | todo | |
| `service` | 10 | 2515 | S11 | todo | |
| `service/auto` | 4 | 598 | S21 | todo | |
| `sync` | 3 | 533 | S20 | todo | |
| `sync/apply` | 6 | 1413 | S20 | todo | |
| `sync/canonical` | 2 | 475 | S20 | todo | |
| `sync/crypto` | 2 | 253 | S20 | todo | |
| `sync/identity` | 2 | 220 | S20 | todo | |
| `sync/mapping` | 5 | 826 | S20 | todo | |
| `sync/merge` | 10 | 1244 | S20 | todo | |
| `sync/protocol` | 4 | 949 | S20 | todo | |
| `sync/qr` | 2 | 219 | S20 | todo | |
| `sync/transport` | 5 | 439 | S20 | todo | |
| `sync2/changes` | 1 | 184 | S20 | todo | |
| `sync2/crypto` | 12 | 541 | S20 | todo | |
| `sync2/data` | 6 | 366 | S20 | todo | |
| `sync2/device` | 1 | 80 | S20 | todo | |
| `sync2/engine` | 5 | 705 | S20 | todo | |
| `sync2/lan` | 4 | 705 | S20 | todo | |
| `sync2/linking` | 9 | 1037 | S20 | todo | |
| `sync2/roster` | 3 | 402 | S20 | todo | |
| `sync2/service` | 8 | 1037 | S20 | todo | |
| `sync2/store` | 7 | 947 | S20 | todo | |
| `ui` | 23 | 3399 | S13 | todo | |
| `ui/components` | 8 | 956 | S13 | todo | |
| `ui/components/categories` | 4 | 387 | S14 | todo | |
| `ui/components/channel` | 11 | 1585 | S14 | todo | |
| `ui/components/donation` | 4 | 232 | S17 | todo | |
| `ui/components/equalizer` | 7 | 1062 | S16 | todo | |
| `ui/components/home` | 1 | 282 | S14 | todo | |
| `ui/components/layout` | 14 | 1268 | S13 | todo | |
| `ui/components/library` | 25 | 3384 | S15 | todo | |
| `ui/components/music` | 31 | 4989 | S16 | todo | |
| `ui/components/musicplayer` | 52 | 10452 | S16 | todo | |
| `ui/components/profiles` | 5 | 505 | S17 | todo | |
| `ui/components/search` | 10 | 1440 | S14 | todo | |
| `ui/components/settings` | 9 | 1107 | S17 | todo | |
| `ui/components/shared` | 138 | 19009 | S13 | todo | |
| `ui/components/shorts` | 12 | 2191 | S15 | todo | |
| `ui/components/stats` | 4 | 578 | S16 | todo | |
| `ui/components/videoplayer` | 94 | 15074 | S14 | todo | |
| `ui/screens/categories` | 3 | 603 | S14 | todo | |
| `ui/screens/channel` | 11 | 2094 | S14 | todo | |
| `ui/screens/crash` | 2 | 351 | S17 | todo | |
| `ui/screens/equalizer` | 5 | 1422 | S16 | todo | |
| `ui/screens/history` | 2 | 600 | S15 | todo | |
| `ui/screens/home` | 33 | 6001 | S14 | todo | |
| `ui/screens/library` | 23 | 3547 | S15 | todo | |
| `ui/screens/music` | 52 | 8734 | S16 | todo | |
| `ui/screens/notes` | 7 | 1399 | S15 | todo | |
| `ui/screens/notifications` | 5 | 692 | S15 | todo | |
| `ui/screens/onboarding` | 14 | 2147 | S17 | todo | |
| `ui/screens/player` | 73 | 12268 | S14 | todo | |
| `ui/screens/playlists` | 16 | 2293 | S15 | todo | |
| `ui/screens/recap` | 14 | 2277 | S16 | todo | |
| `ui/screens/recognition` | 3 | 791 | S16 | todo | |
| `ui/screens/search` | 4 | 755 | S14 | todo | |
| `ui/screens/settings` | 148 | 19293 | S17 | todo | |
| `ui/screens/shorts` | 6 | 1287 | S15 | todo | |
| `ui/screens/subscriptions` | 21 | 2909 | S15 | todo | |
| `ui/screens/sync` | 16 | 2472 | S17 | todo | |
| `ui/screens/update` | 6 | 713 | skip | skipped | |
| `ui/screens/widgets` | 4 | 170 | S17 | todo | |
| `ui/startup` | 5 | 431 | S13 | todo | |
| `ui/theme` | 16 | 1926 | S02 | done | 16/16 in `shared/.../ui/theme`, 12 verbatim. Ported tests: FlowTypographyTest, PaletteColorsTest, ThemeMigrationTest, InversePrimaryContrastTest, AppFontTest (AppFont cases; CustomFontStore cases move with `data/local`, S06), MaterialYouSchemeTest (rewritten for the iOS limit below); new ColorToneTest. Green on jvm and iOS simulator, CI run 41029990779. |
| `ui/tv` | 37 | 4110 | skip | skipped | |
| `ui/utils` | 3 | 159 | S13 | todo | |
| `util` | 1 | 50 | S17 | todo | Moved from S03: AppIcons.kt lists Android launcher aliases (`R.mipmap`, manifest suffixes); the iOS counterpart is alternate app icons, built with the icon picker in AppearanceScreen (S17). |
| `utils` (DateDisplay, FormatUtils, HexColors, LocaleCatalogs, NetworkConnectivityObserver, NetworkState, NewPipeImageUtils, OriginalTextPolicy, PerformanceDispatcher, SearchFolding, SponsorBlockCategoryLabels, ThumbnailUrlResolver, Timestamps, YouTubeLink) | 14 | 2070 | S03 | done | java.util.Locale, android.icu and java.text go through `utils/Locale`, `utils/PlatformFormat` (ICU4J on jvm, Foundation on iOS) and `utils/CompactNumbers` (CLDR data from ICU4J 77.1; CompactNumberDataTest matches ICU4J's CompactDecimalFormat on 671,216 cases across every ICU locale). NetworkState and NetworkConnectivityObserver lose their Context parameter. NewPipeImageUtils keeps `avatarImageIdentityKey`; its two NewPipe `Image` helpers go to S07 with the NewPipe path. PerformanceDispatcher.networkIO is a 4–16 wide view of Dispatchers.IO at default priority (upstream: own pool at nice 5). Tests: DateDisplayTest (+DateDisplayJvmTest), DateOnlyUploadTest, FilterBySearchTest, FormatDurationTest, FormatMultiplierLabelTest, OriginalTextPolicyTest, ScheduledStartTest, StreamedDateParsingTest, ThumbnailQualityTiersTest, ThumbnailUrlResolverTest, TimestampsTest, YouTubeLinkTest, PlatformFormatTest. |
| `utils/AppLanguageManager.kt` | 1 | 230 | S06 | todo | Moved from S03: its first consumer is PlayerPreferences (S06). It keeps the language in SharedPreferences and hands it to Android's LocaleManager and Context configuration, which iOS replaces with DataStore and the system's per-app language. AppLanguageListTest, AppLanguageManagerTest, AppLanguageResolutionTest move with it. |
| `utils/AsyncTrace.kt` | 1 | 24 | S09 | todo | Moved from S03: androidx.tracing async sections for the benchmarks; first consumer FlowNeuroEngine (S09). |
| `utils/Clipboard.kt` | 1 | 34 | S13 | todo | Moved from S03: Android ClipData behind Compose's Clipboard; consumers are UI and player error sheets. |
| `utils/ContentLocalization.kt` | 1 | 62 | S07 | todo | Moved from S03: it configures NewPipe's Localization/ContentCountry for YouTubeRepository's NewPipe path (S07) and reads PlayerPreferences' CONTENT_LANGUAGE_FOLLOW_APP (S06). ContentLocalizationTest moves with it. |
| `utils/DeviceDetails.kt` | 1 | 195 | S17 | todo | Moved from S03: Android device, codec and storage details for the diagnostics screen. |
| `utils/ExitReasonText.kt` | 1 | 49 | S17 | todo | Moved from S03: describes ApplicationExitInfo for FlowDiagnostics; ExitReasonTextTest moves with it. |
| `utils/FlowCrashHandler.kt` | 1 | 210 | S13 | todo | Moved from S03: the uncaught-exception handler installed by FlowApplication/MainActivity. |
| `utils/FlowCrashReportFormatter.kt` | 1 | 76 | S13 | todo | Moved from S03 with FlowCrashHandler, its only consumer; FlowCrashReportFormatterTest moves with it. |
| `utils/FlowDiagnostics.kt` | 1 | 156 | S17 | todo | Moved from S03: ActivityManager diagnostics for the diagnostics screen. |
| `utils/MusicPlayerUtils.kt` | 1 | 275 | S04 | done | Resolves through the ported ladder. The audio language and quality are PlayerPreferences' defaults ("original", Auto) until S06 brings DataStore (`shortcut:` in the file). Since S04b Opus in WebM is kept (the bundled engine plays it), and left out only while AirPlay is on. AudioLanguageMatchTest; live: LivePlaybackChainTest.aMusicTrackBecomesHlsAVPlayerCanOpen and the playback gate's song. |
| `utils/RichTextRendering.kt` | 1 | 127 | S13 | todo | Moved from S03: Compose AnnotatedString rendering for comment and description composables; it needs ShareVideo's watch URL. RichTextTargetsTest's rendering case moves with it. |
| `utils/SharedFiles.kt` | 1 | 12 | S19 | todo | Moved from S03: FileProvider share URIs for exports. |
| `utils/ShareVideo.kt` | 1 | 162 | S13 | todo | Moved from S03: share and open-in-YouTube Intents; iOS needs UIActivityViewController and a presenting view controller. ShareVideoTest moves with it. |
| `utils/TextUtilities.kt` | 1 | 103 | S13 | todo | Moved from S03: HtmlCompat/URLSpan comment formatting for ui/components/shared. |
| `utils/cipher` | 5 | 1637 | S04 | done | CipherWebView runs upstream's exports and discovery script against the player script in JavaScriptCore (`JsContext`; GraalJS on the jvm test target only) with a few browser globals defined, in place of an Android WebView. PlayerJsFetcher caches in Application Support; PipePipeNsigDecoder on Ktor with its player id in `utils/SharedPreferences`. Tests: PipePipeNsigDecoderTest, SignatureCallSiteTest, StringArrayObfuscationTest, CI run 41215728299 (jvm 751 run, iOS simulator 741 run, 0 failed); live: LivePlaybackChainTest.thePlayerScriptsCipherRunsInThePlatformEngine (the current player script's n function and signature, jvm and iOS) and the gate's cipher step: JavaScriptCore in the app solved player 11a45ffb's signature and n parameter (41215728299). |
| `utils/potoken` (all but NewPipePoTokenProvider.kt) | 9 | 1417 | S04 | done | PoTokenWebView is a WKWebView on iOS: upstream's po_token.html (compiled in from the pin), loaded with a youtube.com base URL, every subresource load blocked by a content rule list, and upstream's JavaScript interface as a user script over a script message handler. The jvm test target has no web view and reports it unsupported, as Android does. VisitorIdentityStore keeps its identity in `utils/SharedPreferences` (NSUserDefaults) and tells networks apart by NWPath's interfaces and gateways. Tests: JavaScriptUtilTest, PoTokenAttestationPolicyTest, RerollBudgetTest, VisitorIdTest, VisitorIdentityAgeTest, CI run 41215728299 (jvm 751 run, iOS simulator 741 run, 0 failed); live: the gate's PoToken step minted 124-character player and streaming tokens, not low-trust, in WKWebView (41215728299). |
| `utils/potoken/NewPipePoTokenProvider.kt` | 1 | 63 | S07 | todo | Moved from S04: it serves NewPipe's YoutubeStreamExtractor, which iOS gets only if S07 ports the NewPipe path behind YouTubeRepository's StreamInfo calls. The InnerTube extractor mints through WebPoTokenSession, ported here. |
| `utils/protobuf` | 1 | 91 | S03 | done | kotlinx-io Buffer for ByteArrayOutputStream. Covered by YouTubeSearchParamsTest and ReelParamsTest. |
| `utils/relativedate` | 2 | 580 | S03 | done | Pulled forward from S07: the InnerTube parsers date items with RelativeUploadDateParser. Character.getType/digit → Kotlin's CharCategory and digitToInt. RelativeUploadDateParserTest. |
| `widget/config` | 1 | 69 | S21 | todo | |
| `widget/core` | 26 | 2035 | S21 | todo | |
| `widget/downloads` | 2 | 300 | S21 | todo | |
| `widget/nowplaying` | 6 | 746 | S21 | todo | |
| `widget/onrepeat` | 2 | 209 | S21 | todo | |
| `widget/playlist` | 5 | 624 | S21 | todo | |
| `widget/quickactions` | 3 | 213 | S21 | todo | |
| `widget/recent` | 3 | 316 | S21 | todo | |
| `widget/recognize` | 1 | 115 | S21 | todo | |
| `widget/turntable` | 1 | 162 | S21 | todo | |
| `widget/week` | 2 | 288 | S21 | todo | |

## 2. README features

| # | Area | Feature | Session | Status | Evidence |
|---|---|---|---|---|---|
| F01 | Video | High-quality playback via ExoPlayer (Media3) with resolution switching (1080p, 720p, 480p, 360p) | S10 | todo | |
| F02 | Video | SponsorBlock — automatically skips sponsors, intros, outros, and filler | S10 | todo | |
| F03 | Video | DeArrow — replaces clickbait thumbnails and titles with community-sourced alternatives | S10 | todo | |
| F04 | Video | Return Youtube Dislike (RYD) | S10 | todo | |
| F05 | Video | Background playback — listen to audio with the screen off | S11 | todo | |
| F06 | Video | Picture-in-Picture — keep watching while using other apps | S11 | todo | |
| F07 | Video | Casting to smart TVs and streaming devices | S21 | todo | |
| F08 | Video | Playback speed control (0.25x to 2x) | S10 | todo | |
| F09 | Video | Video chapters with seek jumping | S10 | todo | |
| F10 | Video | Gesture controls for brightness, volume, and seeking | S11 | todo | |
| F11 | Video | Subtitles with customizable font size, color, and background | S10 | todo | |
| F12 | Video | Downloads with VP9, AV1, and standard format support | S18 | todo | |
| F13 | Video | Resume playback from where you left off | S10 | todo | |
| F14 | Music | Dedicated music player with album art and audio visualizations | S12, S16 | todo | |
| F15 | Music | Queue management with add, remove, and reorder | S12 | todo | |
| F16 | Music | Shuffle and repeat (single/all) | S12 | todo | |
| F17 | Music | Persistent mini player across the app | S16 | todo | |
| F18 | Music | Synchronized lyrics display | S08, S16 | todo | |
| F19 | Music | Fetches tracks from YouTube Music | S03 | done | LiveInnerTubeSmokeTest.aMusicSearchFindsTheSong and theMusicHomeBrowseHasShelves against the live API, CI run 41056924212 (jvm 514 run, iOS simulator 505 run, 0 failed; live API reached from both, no block); parsing covered by the ported page tests. Playback is S04. |
| F20 | Music | Playback speed control for music playback | S12 | todo | |
| F21 | Recommendations (FlowNeuro Engine) | Runs 100% on-device — no server, no telemetry, no account needed | S09 | todo | |
| F22 | Recommendations (FlowNeuro Engine) | Learns from what you watch, skip, like, dislike, search for, and how long you watch | S09 | todo | |
| F23 | Recommendations (FlowNeuro Engine) | Distinguishes weekday and weekend patterns, morning and night preferences | S09 | todo | |
| F24 | Recommendations (FlowNeuro Engine) | Detects when you're getting bored of a topic and mixes in new content | S09 | todo | |
| F25 | Recommendations (FlowNeuro Engine) | Prevents your feed from collapsing into the same 2-3 topics | S09 | todo | |
| F26 | Recommendations (FlowNeuro Engine) | Surfaces related videos from your recent watches to create natural topic transitions | S09 | todo | |
| F27 | Recommendations (FlowNeuro Engine) | Uses engagement signals (like-to-view ratios) to filter out low-quality content | S09 | todo | |
| F28 | Recommendations (FlowNeuro Engine) | Full transparency dashboard — see what the algorithm knows and why it recommended something | S17 | todo | |
| F29 | Recommendations (FlowNeuro Engine) | Export/import your entire recommendation profile as a file | S19 | todo | |
| F30 | Library | Local watch history | S15 | todo | |
| F31 | Library | Favorites and custom playlists | S15 | todo | |
| F32 | Library | Shorts feed with bookmarking | S15 | todo | |
| F33 | Library | Continue watching shelf | S14 | todo | |
| F34 | Library | Subscription management with cached feeds | S15 | todo | |
| F35 | Privacy | No Google account required | S03 | done | Every request in LiveInnerTubeSmokeTest goes out with no cookie, account or API key (searches use the anonymous WEB context), CI run 41056924212. |
| F36 | Privacy | No ads, analytics, or tracking | S22 | todo | |
| F37 | Privacy | All data stored locally on your device | S06 | todo | |
| F38 | Privacy | Import subscriptions and history from NewPipe | S19 | todo | |
| F39 | Privacy | Export or delete everything at any time | S19 | todo | |
| F40 | Appearance | 11 themes: Light, Dark, OLED Black, Ocean Blue, Forest Green, Sunset Orange, Purple Nebula, Midnight Black, Rose Gold, Arctic Ice, Crimson Red | S02 | done | Upstream at the pin has 26 palettes plus Material You, each in light, dark and AMOLED; the README's names are the old ones, which `ThemeMode.fromStored` maps (ThemeMigrationTest.everyReadmeThemeStillResolvesToAPalette). Theme picker demo in `launch.png`, CI run 41029990779. Material You: see section 7. The settings UI is S17. |
| F41 | Appearance | Built entirely with Jetpack Compose and Material 3 | S13 | todo | |

## 3. Navigation routes

| Route | Declared in | Session | Status | Evidence |
|---|---|---|---|---|
| `DOWNLOADS_ROUTE, DOWNLOADS_TAB_ROUTE` | `ui/DownloadsRoutes.kt:26` | S15 | todo | |
| `"onboarding"` | `ui/FlowNavigation.kt:71` | S17 | todo | |
| `"home"` | `ui/FlowNavigation.kt:83` | S14 | todo | |
| `UPDATE_ROUTE` | `ui/FlowNavigation.kt:110` | skip | skipped | |
| `"notifications"` | `ui/FlowNavigation.kt:116` | S15 | todo | |
| `SHORTS_ROUTE_PATTERN` | `ui/FlowNavigation.kt:129` | S15 | todo | |
| `"subscriptions"` | `ui/FlowNavigation.kt:155` | S15 | todo | |
| `"library"` | `ui/FlowNavigation.kt:182` | S15 | todo | |
| `"search"` | `ui/FlowNavigation.kt:249` | S14 | todo | |
| `EQUALIZER_ROUTE` | `ui/FlowNavigation.kt:271` | S16 | todo | |
| `"categories"` | `ui/FlowNavigation.kt:276` | S14 | todo | |
| `"settings?target={target}"` | `ui/FlowNavigation.kt:291` | S17 | todo | |
| `RecapRoutes.STATS` | `ui/FlowNavigation.kt:312` | S16 | todo | |
| `RecapRoutes.STORY` | `ui/FlowNavigation.kt:331` | S16 | todo | |
| `"donations"` | `ui/FlowNavigation.kt:356` | S17 | todo | |
| `"channel?url={channelUrl}"` | `ui/FlowNavigation.kt:363` | S14 | todo | |
| `"history"` | `ui/FlowNavigation.kt:395` | S15 | todo | |
| `"playlists?kind={kind}"` | `ui/FlowNavigation.kt:430` | S15 | todo | |
| `"playlist/{playlistId}"` | `ui/FlowNavigation.kt:460` | S15 | todo | |
| `"savedShorts"` | `ui/FlowNavigation.kt:480` | S15 | todo | |
| `"notes"` | `ui/FlowNavigation.kt:490` | S15 | todo | |
| `"localMedia"` | `ui/FlowNavigation.kt:497` | S19 | todo | |
| `MUSIC_PLAYER_ROUTE_PATTERN` | `ui/LinkPlaybackNavigation.kt:35` | S16 | todo | |
| `LINKED_PLAYLIST_ROUTE_PATTERN` | `ui/LinkPlaybackNavigation.kt:69` | S19 | todo | |
| `"player/{videoId}"` | `ui/LinkPlaybackNavigation.kt:93` | S14 | todo | |
| `"music"` | `ui/MusicNavigation.kt:30` | S16 | todo | |
| `"moodsAndGenres"` | `ui/MusicNavigation.kt:78` | S16 | todo | |
| `"musicSearch?query={query}"` | `ui/MusicNavigation.kt:90` | S16 | todo | |
| `"musicRecognize"` | `ui/MusicNavigation.kt:132` | S16 | todo | |
| `"recognitionHistory"` | `ui/MusicNavigation.kt:164` | S16 | todo | |
| `"youtube_browse/{browseId}?params={params}"` | `ui/MusicNavigation.kt:196` | S16 | todo | |
| `MUSIC_ARTIST_ROUTE_PATTERN` | `ui/MusicNavigation.kt:241` | S16 | todo | |
| `"artistItems/{channelId}/{browseId}?params={params}"` | `ui/MusicNavigation.kt:270` | S16 | todo | |
| `MUSIC_PLAYLIST_ROUTE_PATTERN` | `ui/MusicNavigation.kt:322` | S16 | todo | |
| `ON_REPEAT_SHUFFLE_ROUTE` | `ui/WidgetPlaybackNavigation.kt:40` | S21 | todo | |
| `WIDGET_PLAYLIST_ROUTE` | `ui/WidgetPlaybackNavigation.kt:54` | S21 | todo | |

## 4. Screens (`*Screen.kt`)

| Screen | Path | Session | Status | Evidence |
|---|---|---|---|---|
| CategoriesScreen | `ui/screens/categories/CategoriesScreen.kt` | S14 | todo | |
| ChannelScreen | `ui/screens/channel/ChannelScreen.kt` | S14 | todo | |
| CrashReportScreen | `ui/screens/crash/CrashReportScreen.kt` | S17 | todo | |
| EqualizerScreen | `ui/screens/equalizer/EqualizerScreen.kt` | S16 | todo | |
| HistoryScreen | `ui/screens/history/HistoryScreen.kt` | S15 | todo | |
| HomeScreen | `ui/screens/home/HomeScreen.kt` | S14 | todo | |
| DownloadsScreen | `ui/screens/library/DownloadsScreen.kt` | S15 | todo | |
| LibraryScreen | `ui/screens/library/LibraryScreen.kt` | S15 | todo | |
| LocalMediaScreen | `ui/screens/library/LocalMediaScreen.kt` | S15 | todo | |
| SavedShortsGridScreen | `ui/screens/library/SavedShortsGridScreen.kt` | S15 | todo | |
| ArtistItemsScreen | `ui/screens/music/ArtistItemsScreen.kt` | S16 | todo | |
| MusicCollectionScreen | `ui/screens/music/collection/MusicCollectionScreen.kt` | S16 | todo | |
| EnhancedMusicScreen | `ui/screens/music/EnhancedMusicScreen.kt` | S16 | todo | |
| MoodsAndGenresScreen | `ui/screens/music/MoodsAndGenresScreen.kt` | S16 | todo | |
| MusicSearchScreen | `ui/screens/music/MusicSearchScreen.kt` | S16 | todo | |
| YouTubeBrowseScreen | `ui/screens/music/YouTubeBrowseScreen.kt` | S16 | todo | |
| NotesScreen | `ui/screens/notes/NotesScreen.kt` | S15 | todo | |
| NotificationScreen | `ui/screens/notifications/NotificationScreen.kt` | S15 | todo | |
| OnboardingScreen | `ui/screens/onboarding/OnboardingScreen.kt` | S17 | todo | |
| EnhancedVideoPlayerScreen | `ui/screens/player/EnhancedVideoPlayerScreen.kt` | S14 | todo | |
| PlaylistDetailScreen | `ui/screens/playlists/PlaylistDetailScreen.kt` | S15 | todo | |
| PlaylistsScreen | `ui/screens/playlists/PlaylistsScreen.kt` | S15 | todo | |
| RecapScreen | `ui/screens/recap/RecapScreen.kt` | S16 | todo | |
| RecapStoryScreen | `ui/screens/recap/story/RecapStoryScreen.kt` | S16 | todo | |
| RecognitionHistoryScreen | `ui/screens/recognition/RecognitionHistoryScreen.kt` | S16 | todo | |
| RecognitionScreen | `ui/screens/recognition/RecognitionScreen.kt` | S16 | todo | |
| SearchScreen | `ui/screens/search/SearchScreen.kt` | S14 | todo | |
| AboutScreen | `ui/screens/settings/about/AboutScreen.kt` | S17 | todo | |
| DonationsScreen | `ui/screens/settings/about/DonationsScreen.kt` | S17 | todo | |
| AppearanceScreen | `ui/screens/settings/appearance/AppearanceScreen.kt` | S17 | todo | |
| DateTimeScreen | `ui/screens/settings/appearance/DateTimeScreen.kt` | S17 | todo | |
| FontScreen | `ui/screens/settings/appearance/FontScreen.kt` | S17 | todo | |
| NavigationBarScreen | `ui/screens/settings/appearance/NavigationBarScreen.kt` | S17 | todo | |
| PlayerAppearanceScreen | `ui/screens/settings/appearance/player/PlayerAppearanceScreen.kt` | S17 | todo | |
| CustomThemeEditorScreen | `ui/screens/settings/appearance/theme/CustomThemeEditorScreen.kt` | S17 | todo | |
| CustomThemesScreen | `ui/screens/settings/appearance/theme/CustomThemesScreen.kt` | S17 | todo | |
| ThemeScreen | `ui/screens/settings/appearance/theme/ThemeScreen.kt` | S17 | todo | |
| BackupScreen | `ui/screens/settings/backup/BackupScreen.kt` | S17 | todo | |
| ContentSettingsScreen | `ui/screens/settings/content/ContentSettingsScreen.kt` | S17 | todo | |
| LibrarySettingsScreen | `ui/screens/settings/content/LibrarySettingsScreen.kt` | S17 | todo | |
| MusicHomeSettingsScreen | `ui/screens/settings/content/MusicHomeSettingsScreen.kt` | S17 | todo | |
| DiagnosticsScreen | `ui/screens/settings/diagnostics/DiagnosticsScreen.kt` | S17 | todo | |
| DownloadSettingsScreen | `ui/screens/settings/downloads/DownloadSettingsScreen.kt` | S17 | todo | |
| HistorySettingsScreen | `ui/screens/settings/history/HistorySettingsScreen.kt` | S17 | todo | |
| SettingsHomeScreen | `ui/screens/settings/home/SettingsHomeScreen.kt` | S17 | todo | |
| IntegrationsScreen | `ui/screens/settings/integrations/IntegrationsScreen.kt` | S17 | todo | |
| LocalMediaSettingsScreen | `ui/screens/settings/localmedia/LocalMediaSettingsScreen.kt` | S17 | todo | |
| NetworkSettingsScreen | `ui/screens/settings/network/NetworkSettingsScreen.kt` | S17 | todo | |
| NotificationSettingsScreen | `ui/screens/settings/notifications/NotificationSettingsScreen.kt` | S17 | todo | |
| BufferSettingsScreen | `ui/screens/settings/playback/BufferSettingsScreen.kt` | S17 | todo | |
| PlaybackSettingsScreen | `ui/screens/settings/playback/PlaybackSettingsScreen.kt` | S17 | todo | |
| ProfileEditScreen | `ui/screens/settings/profiles/ProfileEditScreen.kt` | S17 | todo | |
| ProfilesScreen | `ui/screens/settings/profiles/ProfilesScreen.kt` | S17 | todo | |
| QualitySettingsScreen | `ui/screens/settings/quality/QualitySettingsScreen.kt` | S17 | todo | |
| LanguageRegionScreen | `ui/screens/settings/region/LanguageRegionScreen.kt` | S17 | todo | |
| ScrobblingScreen | `ui/screens/settings/scrobbling/ScrobblingScreen.kt` | S17 | todo | |
| DiscoverChannelsScreen | `ui/screens/settings/taste/DiscoverChannelsScreen.kt` | S17 | todo | |
| FavouriteArtistsScreen | `ui/screens/settings/taste/FavouriteArtistsScreen.kt` | S17 | todo | |
| HiddenContentScreen | `ui/screens/settings/taste/HiddenContentScreen.kt` | S17 | todo | |
| TasteScreen | `ui/screens/settings/taste/TasteScreen.kt` | S17 | todo | |
| TopicPreferencesScreen | `ui/screens/settings/topics/TopicPreferencesScreen.kt` | S17 | todo | |
| WellbeingScreen | `ui/screens/settings/wellbeing/WellbeingScreen.kt` | S17 | todo | |
| WidgetsSettingsScreen | `ui/screens/settings/widgets/WidgetsSettingsScreen.kt` | S17 | todo | |
| ShortsScreen | `ui/screens/shorts/ShortsScreen.kt` | S15 | todo | |
| SubscriptionsScreen | `ui/screens/subscriptions/SubscriptionsScreen.kt` | S15 | todo | |
| SyncScreen | `ui/screens/sync/SyncScreen.kt` | S17 | todo | |
| UpdateScreen | `ui/screens/update/UpdateScreen.kt` | skip | skipped | |
| PlaylistConfigScreen | `ui/screens/widgets/PlaylistConfigScreen.kt` | S17 | todo | |
| QuickActionsConfigScreen | `ui/screens/widgets/QuickActionsConfigScreen.kt` | S17 | todo | |

## 5. Home-screen widgets (Glance → WidgetKit)

| Widget | Session | Status | Evidence |
|---|---|---|---|
| DownloadsWidget | S21 | todo | |
| NowPlayingWidget | S21 | todo | |
| OnRepeatWidget | S21 | todo | |
| PlaylistWidget | S21 | todo | |
| QuickActionsWidget | S21 | todo | |
| RecentlyPlayedWidget | S21 | todo | |
| RecognizeWidget | S21 | todo | |
| TurntableWidget | S21 | todo | |
| WeekWidget | S21 | todo | |

## 6. Strings and locales

| Item | Count | Session | Status | Evidence |
|---|---|---|---|---|
| Default strings (`values/strings.xml`) | 4028 | S02 | done | 4028 strings, 105 plurals, 2 arrays in `composeResources/values` (StringResourcesTest.everyUpstreamKeyIsPresent, jvm and iOS). Converted by `tools/import_resources.py` with aapt2's quote, whitespace and escape rules. UpstreamStringsTest reads all 90,352 values (strings, arrays, plural picks) through CMP and matches upstream in every locale. CI re-imports and fails on any drift. |
| Locales (ar, az, b+yue+Hant, bs, cs, de, es, et, fa, fr, hi, hu, in, it, ja, kab, ko, lo, pa, pl, pt, pt-rBR, ru, si, sl, sr, ta, tr, uk, ur, vi, zh-rCN, zh-rTW) | 33 | S02 | done | Same 33 dirs. `values-in` ships as `values-id`, the code iOS and JDK 17+ report. Upstream's fa, lo, sl, ta and b+yue+Hant files are empty and read English, as on Android. 1,286 stale translations of keys the default no longer has are dropped (no code can reference them). iOS: `launch-id.png` (`-AppleLanguages (id)`), CI run 41029990779. |

## 7. Platform behaviour and iOS limits

| Item | Session | Status | Evidence / reason |
|---|---|---|---|
| Background audio playback (screen off, app in background) | S11 | todo | |
| Picture-in-Picture | S11 | todo | S04b's verification pass, on the iPhone (`docs/evidence/s04b-verify/`): a tap on a button over the bundled engine's layer opened Picture in Picture (`AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer:)`); the window stayed up and 6.05 s played inside it, 363 frames, 0 dropped. A start without a tap is refused. S11 still owns the player's own button, the restore path, and AVPlayer's side (AvPlayerEngine gives its controller). |
| Lock-screen / Control Center Now Playing + remote commands | S11 | todo | |
| AirPlay route picker | S11 | todo | |
| Brightness / volume / seek gestures (volume via MPVolumeView) | S11 | todo | |
| Open shared YouTube links (Share Extension + flow:// scheme; youtube.com links cannot be claimed) | S19 | todo | |
| Adaptive DASH streams as local HLS for AVPlayer | S04 | done | `player/resolver/LocalHlsProxy`, a loopback HTTP server (Ktor CIO on 127.0.0.1) rather than an AVAssetResourceLoaderDelegate, because AVFoundation lets a resource loader answer HLS playlists and keys but not media segments, and S05's SABR media must reach AVPlayer as segments through the same server. Info.plist allows local networking. **Played in AVPlayer on an iPhone** (iOS 26.3, home Wi-Fi, gate build of 054e070, `docs/evidence/s04/iphone-playback-gate.json` and `.webp`): a YouTube Music song (VISIONOS, AAC itag 140: readyToPlay, an audio track, 5.08 s played) and a video (VISIONOS, a six-rung H.264 ladder playing 1080p `avc1.64002a`: readyToPlay, video and audio tracks, 21 decoded 1920x1080 frames, 5.08 s played), with the cipher in JavaScriptCore and a WKWebView PoToken mint passing too. That run logged one AVPlayer `-12318 Segment exceeds specified bandwidth`, because the master stated YouTube's average bitrates as BANDWIDTH; since fcc0d6c it states each rendition's peak and average segment rate measured from its sidx (HlsPlaylistsTest; ffmpeg still decodes the proxied stream), **verified on the iPhone** in S04b's verification pass (`docs/evidence/s04b-verify/`): AVPlayer held to the 1080p rung (a master with only that variant) played 30 s at 1920x1080 with an empty error log, in both runs. Also verified from a residential network on jvm (LivePlaybackChainTest). In CI the simulator gate is BLOCKED at the player response, a warning, not a failure (run 41222019979): GitHub's runners are bot-walled on every client. |
| VP9 / WebM playback decision (bundled decoder vs H.264/AV1) | S04b | done | **Played on an iPhone** (iPhone 12 Pro Max, `iPhone13,4`, iOS 26.3, gate build of 494b6a6, `docs/evidence/s04b/`, verdict PASS): VP9 2160p60 and 1440p60 beside Opus, and AV1 2160p60 and 1440p60 (dav1d) beside AAC, each 20 s at 60 fps with 1208-1209 frames shown and 0 dropped, no frame shown late, no gap in the sound, the clock at real time, seeks landing 1-15 ms before their target (within one frame); CPU 103 % of one core for VP9 2160p, 191 % for AV1 2160p (6 cores), thermal state nominal throughout. Highest sustained rung: 2160p for both. The phone has no hardware VP9 or AV1 decoder. Picture in Picture: supported and possible on the sample-buffer layer; starting it needs a tap (S11). 10-bit and HDR (S04b's verification pass, `docs/evidence/s04b-verify/`): YouTube offers HDR to upstream's clients as 10-bit AV1 only (no vp09.02 on four HDR videos, 2026-11-01); on the iPhone 10-bit AV1 HDR played at 2160p60 and 1440p60 as P010 tagged BT.2020 and PQ, the screen at its full EDR headroom (8.0), 0 frames dropped, 351 % CPU at 2160p, and the user saw it as HDR. VP9 2160p60 played ten minutes unplugged with 0 frames dropped in every 30 s window (thermal state serious by 90 s; battery under 5 %). 10-bit VP9 shown on a phone stays unverified, as YouTube does not serve it; on CI's simulator its P010 display stalls on some hosts while decoding runs at 0.68 ms a frame. S04b also: FFmpeg 8.1.3 (LGPL-2.1-or-later: matroska and mov demuxers; vp9, opus and aac decoders) and dav1d 1.5.4 (BSD-2-Clause) from `tools/build_ffmpeg_xcframework.sh`, tarballs checked against pinned SHA-256 (CI run 41239412046: both "OK", configure "License: LGPL version 2.1 or later", device slice platform iOS and simulator slice iOS-simulator, minos 17.0). The Swift engine (`iosApp/iosApp/Engine`) decodes into AVSampleBufferDisplayLayer and AVSampleBufferAudioRenderer under one AVSampleBufferRenderSynchronizer, behind the `PlaybackEngine` contract AVPlayer now shares (`AvPlayerEngine`). Routing follows upstream's choice (PlaybackEngineRoutingTest, jvm and iOS): Auto on a fast network starts at 2160p VP9, so the bundled engine; where only 1080p fits it starts on H.264, so AVPlayer; a preferred AV1 goes to dav1d without a hardware decoder; while AirPlay is on only AVPlayer's formats count. CI engine tests on clips (BundledEngineTests, run 41245514593 on main): VP9, 10-bit VP9 and AV1 beside Opus, and VP9 beside AAC, each 140-141 frames shown and 0 dropped, A/V onset drift 0.19-0.69 ms (bound 5 ms), no shown frame late (bound 40 ms; 3.5 ms in an earlier run); seeks to 3.0 s and 4.5 s landed exactly on target (run 41243654147 had landed one at 4.533 s, which 494b6a6 fixed by holding the clock for the first frame). Picture in Picture: CI's simulator reports it unsupported, so only the phone can show it. From a residential network ffmpeg decoded a live VP9 file and its audio through LocalHlsProxy's file route (LocalHlsFfmpegJvmTest). Before S04b: the user chose a bundled decoder on 2026-10-31: VP9 and AV1 without hardware decode play through a bundled FFmpeg + dav1d engine (S04b), H.264 (and HEVC and AV1 with hardware decode) stay on AVPlayer, the choice following upstream's `VideoCodecUtils` and `QualityManager`. Until then `PlatformCodecSupport` offers AVPlayer only what it plays. Motivation, the S04 gate **on the user's iPhone** (iOS 26.3, `docs/evidence/s04/iphone-playback-gate.json`): hardware H.264 and HEVC, `hardwareVp9=false`, `hardwareAv1=false`, and AVURLAsset says `video/mp4; av01` is not playable, nor `vp09` in MP4 or VP9 in WebM. YouTube offered the test video's 2160p and 1440p only as VP9 or AV1, so AVPlayer alone tops out at 1080p H.264 where upstream on Android plays 2160p VP9. On the simulator, iOS 26.5 (41215728299), AVURLAsset says playable for `avc1` and `av01` in MP4 (software, no hardware decoder there) but not `vp09` in MP4 or VP9 in WebM, so remuxing VP9 into MP4 would not make it playable. |
| Opus audio (YouTube's WebM audio, itags 249-251) | S04b | done | **Played on an iPhone** (`docs/evidence/s04b/`): a YouTube Music song as upstream picked it on Wi-Fi, Opus 251 in the bundled engine, 20 s at real time with no gap in the sound, 1.9 % CPU, a seek landing 6 ms before its target; and Opus 251 beside VP9 2160p60 and 1440p60 (0 frames dropped). In the verification pass the user heard the song and the videos. S04b also: Opus is decodable again, so upstream's own choices pick it: music on Wi-Fi (MusicAudioQuality AUTO resolves to HIGH, which favours WebM; off Wi-Fi it resolves to MEDIUM, nearest 128 kb/s, which can be AAC 140), and video audio by highest bitrate, which for the gate's video is AAC 140 by 129,481 to 128,612 b/s. Opus and AAC beside a bundled-engine video both decode (`canMergeAudio(.., BUNDLED)`). CI (BundledEngineTests, run 41245514593): Opus alone decoded with its tone onset at 2.0007 s (bound 5 ms), 0 underruns; beside VP9 and AV1, drift 0.69 ms. Before S04b: the user chose a bundled decoder on 2026-10-31: Opus plays through the S04b engine where upstream prefers it. AVPlayer reads no WebM (`audio/webm; opus` not playable on the simulator or the iPhone), so until then music and video play AAC (itag 140, or 139) where upstream prefers Opus 160 kb/s. |
| AC-3 / EC-3 surround audio tracks (itags 380, 328) | S04, S04b | iOS limit | NewPipe's itag table lacks them, so upstream's bridge gives them no byte ranges (it makes up an ItagItem for unknown video itags only) and ExoPlayer plays them progressively beside the video. Neither iOS engine is handed a progressive file beside a video, so the audio selector passes them over (`canMergeAudio`, either engine). Checked in S04b: the bundled engine does not remove this. The missing part is not a decoder (AVPlayer decodes AC-3 and E-AC-3, and FFmpeg's ac3/eac3 decoders are LGPL) but the stream's description: taking it on means describing those two itags beyond NewPipe's table, a change to upstream's bridge, and a device check with a video that offers them, which neither the gate's video nor CI has. Not cheap enough for S04b. |
| Live DVR through YouTube's DASH manifest | S04, S04b | iOS limit | Upstream falls back to the live DASH manifest when there is no HLS one; AVPlayer reads no DASH. YouTube's live HLS manifest plays. Checked in S04b: the bundled engine does not remove this. It reads whole files through LocalHlsProxy's byte ranges, while a live DASH manifest is a timeline of segment URLs; FFmpeg's DASH demuxer would need libxml2 and FFmpeg's own HTTP stack, a second fetch path that bypasses `GoogleVideoRequestPolicy`, which S04b rules out. |
| Background downloads (URLSession background) | S18 | todo | |
| New-upload checks via BGAppRefreshTask (best effort) | S18 | todo | |
| Local media via Music library + Files import | S19 | todo | |
| CarPlay (entitlement required) | S21 | todo | |
| DLNA discovery (multicast entitlement required) | S21 | todo | |
| Paid vs free Apple ID capability check (App Group, CarPlay, multicast) | S21 | todo | |
| Material You (Android 12+ dynamic color) | S02 | iOS limit | iOS has no system dynamic color (no Monet). `ThemeMode.MATERIAL_YOU` resolves to Flow Default, as upstream does below Android 12 (MaterialYouSchemeTest). S17 should hide the entry like upstream's ThemeViewModel does below Android 12. |
| "Condensed" app font | S02 | iOS limit | Upstream draws Android's `sans-serif-condensed` (Roboto Condensed), which iOS lacks. The port uses Avenir Next Condensed, which iOS ships in the same five weights (AppFontTest). **Verified on the iPhone** in S04b's verification pass (`docs/evidence/s04b-verify/`): CoreText picks Regular, Medium, DemiBold, Bold and Heavy for the port's weights 400-800, and the port's Compose family draws the five distinctly (the gate's screenshot). |
| Numbers inside formatted strings | S13 | todo | `formatResourceString` prints digits and decimal points as Locale.ROOT ("1.5"). Android formats with the app locale ("1,5" in German, Arabic-Indic digits in Arabic). Marked `shortcut:` in `utils/ResourceFormat.kt`. |
| Locale fallback across scripts (zh-Hant-HK, zh-Hans-SG) | S22 | todo | CMP matches language and region but does not infer scripts, so zh-HK and zh-SG read English where Android picks zh-rTW and zh-rCN. Possible fix: add `values-b+zh+Hant` and `values-b+zh+Hans` copies. |
| Per-app language and localized system UI (`CFBundleLocalizations`) | S17 | todo | Info.plist declares no localizations yet. iOS then offers no per-app language setting and shows system UI in the development language. Add it with the language settings screen. |
| Captive-portal detection (Android's NET_CAPABILITY_VALIDATED) | S03 | iOS limit | NWPathMonitor reports a path as satisfied before a captive portal is signed in to, so NetworkState.isOnline is true there where Android says offline. |
| VPN detection for "bypass proxy on VPN" | S03 | iOS limit | iOS has no public API for whether the app's traffic is tunnelled. VpnStateMonitor reads the app's NWPath and counts a tunnel interface (utun, ipsec, ppp, tun, tap) of type "other"; a VPN that does not show one is missed. |
| App proxy on iOS (HTTP and SOCKS5, with credentials) | S17 | done | Built in S03 (`network/HttpEngine.ios.kt`): HTTP and HTTPS keys in connectionProxyDictionary, HTTP proxy credentials through the auth challenge, SOCKS5 credentials as kCFStreamPropertySOCKSUser/Password keys. **Verified against real proxies** in S04b's verification pass: on CI, tinyproxy 1.11.3 (HTTP) and microsocks 1.0.5 (SOCKS5), each plain and with credentials, carried the app's HTTPS requests to example.com and youtube.com (ProxyRoutingTest, ProxyAwareClient on the Darwin engine in the iOS simulator), each proxy's own log shows them, and each proxy moved to a closed port made them fail, so nothing goes around it (`docs/evidence/s04b-verify/`). SOCKS5 with credentials works on iOS. The network settings screen that sets the proxy is S17's. |
| Non-vector drawables (splash insets, widget selectors, layer-list, animated-vector) | S02 | iOS limit | Android splash and Glance chrome with no CMP equivalent; `tools/import_resources.py` lists all 14 and skips them. The splash belongs to S13 and widget visuals to S21 (WidgetKit). |

## 8. Skipped by decision

| Item | Reason |
|---|---|
| Android TV UI (`ui/tv`, `TvActivity.kt`) | iPhone/iPad app; tvOS would be a separate target |
| In-app APK updater (`data/update`, `ui/screens/update`) | iOS apps cannot self-update; sideload stores handle it |
| Discord Rich Presence (`discord`) | github-flavor extra; unreliable under iOS background limits |
| Nightly flavor, `benchmark/` module, baseline profiles | Android build tooling |
