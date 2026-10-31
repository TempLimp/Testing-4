# Third-party notices

The iOS app links these libraries into its binary. Their licence texts ship inside the app bundle
(`Flow.app/ThirdPartyLicenses`, copied there by `iosApp/project.yml` from the build).

| Library | Version | Licence | Used for | Source |
|---|---|---|---|---|
| [FFmpeg](https://ffmpeg.org) | 8.1.3 | LGPL-2.1-or-later | the bundled engine's demuxers (matroska, mov) and decoders (vp9, opus, aac), swresample and swscale | https://ffmpeg.org/releases/ffmpeg-8.1.3.tar.xz, SHA-256 `7138d28c96d9d3e3af4ee3d8cad72741f8ffb40da90c1112235dea3ecd3178a3` |
| [dav1d](https://code.videolan.org/videolan/dav1d) | 1.5.4 | BSD-2-Clause | AV1 decoding in the bundled engine | https://downloads.videolan.org/pub/videolan/dav1d/1.5.4/dav1d-1.5.4.tar.xz, SHA-256 `686616b7c69eb88d44459391ab25cac13b6647a3b288835c5784e71c1514a5c5` |

`tools/build_ffmpeg_xcframework.sh` builds both from those tarballs, unmodified, and fails unless FFmpeg's configure
reports "LGPL version 2.1 or later" with GPL, nonfree and version3 off. No GPL-only or nonfree FFmpeg component is
built. Both licences are compatible with this app's GPL-3.0: LGPL-2.1-or-later code may be used in a GPL-3.0 work, and
the app's complete source (including that script) is public, so anyone can rebuild it against a modified FFmpeg.

FFmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project.
