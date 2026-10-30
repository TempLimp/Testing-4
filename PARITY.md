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
| `data/innertube` | 2 | 638 | S03 | todo | |
| `data/local` | 86 | 15280 | S06 | todo | |
| `data/localmedia` | 23 | 2024 | S19 | todo | |
| `data/lyrics` | 30 | 3864 | S08 | todo | |
| `data/migration` | 1 | 76 | S19 | todo | |
| `data/model` | 13 | 615 | S08 | todo | |
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
| `extensions` | 1 | 133 | S03 | todo | |
| `innertube` | 4 | 4117 | S03 | todo | |
| `innertube/models` | 78 | 4897 | S03 | todo | |
| `innertube/pages` | 58 | 7734 | S03 | todo | |
| `innertube/utils` | 1 | 96 | S03 | todo | |
| `network` | 4 | 337 | S03 | todo | |
| `notification` | 14 | 2126 | S11, S18 | todo | |
| `platform` | 5 | 219 | S13 | todo | |
| `player` | 47 | 7905 | S10 | todo | |
| `player/analytics` | 2 | 117 | S10 | todo | |
| `player/audio` | 6 | 800 | S12 | todo | |
| `player/cache` | 3 | 392 | S10 | todo | |
| `player/config` | 2 | 231 | S10 | todo | |
| `player/datasource` | 6 | 613 | S10 | todo | |
| `player/dlna` | 4 | 1549 | S21 | todo | |
| `player/error` | 7 | 1752 | S10 | todo | |
| `player/factory` | 2 | 355 | S10 | todo | |
| `player/media` | 2 | 680 | S11 | todo | |
| `player/musicvideo` | 6 | 453 | S10 | todo | |
| `player/preload` | 1 | 302 | S10 | todo | |
| `player/quality` | 4 | 787 | S04 | todo | |
| `player/recovery` | 3 | 251 | S10 | todo | |
| `player/renderer` | 4 | 522 | S10 | todo | |
| `player/resolver` | 4 | 673 | S04 | todo | |
| `player/sabr` | 21 | 3726 | S05 | todo | |
| `player/service` | 1 | 67 | S11 | todo | |
| `player/shorts` | 7 | 1013 | S15 | todo | |
| `player/sponsorblock` | 1 | 290 | S10 | todo | |
| `player/state` | 3 | 109 | S10 | todo | |
| `player/stream` | 27 | 3659 | S04 | todo | |
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
| `util` | 1 | 50 | S03 | todo | |
| `utils` | 28 | 3785 | S03 | todo | |
| `utils/cipher` | 5 | 1637 | S04 | todo | |
| `utils/potoken` | 10 | 1480 | S04 | todo | |
| `utils/protobuf` | 1 | 91 | S03 | todo | |
| `utils/relativedate` | 2 | 580 | S07 | todo | |
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
| F19 | Music | Fetches tracks from YouTube Music | S03 | todo | |
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
| F35 | Privacy | No Google account required | S03 | todo | |
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
| Picture-in-Picture | S11 | todo | |
| Lock-screen / Control Center Now Playing + remote commands | S11 | todo | |
| AirPlay route picker | S11 | todo | |
| Brightness / volume / seek gestures (volume via MPVolumeView) | S11 | todo | |
| Open shared YouTube links (Share Extension + flow:// scheme; youtube.com links cannot be claimed) | S19 | todo | |
| Adaptive DASH streams as local HLS for AVPlayer | S04 | todo | |
| VP9 / WebM playback decision (bundled decoder vs H.264/AV1) | S04 | todo | |
| Background downloads (URLSession background) | S18 | todo | |
| New-upload checks via BGAppRefreshTask (best effort) | S18 | todo | |
| Local media via Music library + Files import | S19 | todo | |
| CarPlay (entitlement required) | S21 | todo | |
| DLNA discovery (multicast entitlement required) | S21 | todo | |
| Paid vs free Apple ID capability check (App Group, CarPlay, multicast) | S21 | todo | |
| Material You (Android 12+ dynamic color) | S02 | iOS limit | iOS has no system dynamic color (no Monet). `ThemeMode.MATERIAL_YOU` resolves to Flow Default, as upstream does below Android 12 (MaterialYouSchemeTest). S17 should hide the entry like upstream's ThemeViewModel does below Android 12. |
| "Condensed" app font | S02 | iOS limit | Upstream draws Android's `sans-serif-condensed` (Roboto Condensed), which iOS lacks. The port uses Avenir Next Condensed, which iOS ships in the same five weights (AppFontTest). Not verified on a device. |
| Numbers inside formatted strings | S13 | todo | `formatResourceString` prints digits and decimal points as Locale.ROOT ("1.5"). Android formats with the app locale ("1,5" in German, Arabic-Indic digits in Arabic). Marked `shortcut:` in `utils/ResourceFormat.kt`. |
| Locale fallback across scripts (zh-Hant-HK, zh-Hans-SG) | S22 | todo | CMP matches language and region but does not infer scripts, so zh-HK and zh-SG read English where Android picks zh-rTW and zh-rCN. Possible fix: add `values-b+zh+Hant` and `values-b+zh+Hans` copies. |
| Per-app language and localized system UI (`CFBundleLocalizations`) | S17 | todo | Info.plist declares no localizations yet. iOS then offers no per-app language setting and shows system UI in the development language. Add it with the language settings screen. |
| Non-vector drawables (splash insets, widget selectors, layer-list, animated-vector) | S02 | iOS limit | Android splash and Glance chrome with no CMP equivalent; `tools/import_resources.py` lists all 14 and skips them. The splash belongs to S13 and widget visuals to S21 (WidgetKit). |

## 8. Skipped by decision

| Item | Reason |
|---|---|
| Android TV UI (`ui/tv`, `TvActivity.kt`) | iPhone/iPad app; tvOS would be a separate target |
| In-app APK updater (`data/update`, `ui/screens/update`) | iOS apps cannot self-update; sideload stores handle it |
| Discord Rich Presence (`discord`) | github-flavor extra; unreliable under iOS background limits |
| Nightly flavor, `benchmark/` module, baseline profiles | Android build tooling |
