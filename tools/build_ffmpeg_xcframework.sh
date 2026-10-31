#!/usr/bin/env bash
# Builds the bundled engine's decoders (S04b) as build/ffmpeg/FFmpeg.xcframework: FFmpeg, minimal and LGPL-2.1-or-later
# only, with dav1d (BSD-2-Clause) for AV1, as one static library per slice (iOS arm64, simulator arm64) plus headers,
# from release tarballs checked against the SHA-256 pinned here. CI caches the result keyed on this file's hash, so
# changing a version, checksum or flag below rebuilds it. Needs Xcode, curl, meson, ninja and pkg-config.
#
# Pinned 2026-10-31. FFmpeg 8.1.3 is the newest of the 8.x line (9.0.2 exists; 8.1 keeps the API the engine was
# written against). Its SHA-256 is of the tarball whose signature verified against FFmpeg's release key
# FCF9 86EA 15E6 E293 A564 4F10 B432 2F04 D676 58D8; dav1d's matches the .sha256 VideoLAN publishes.
set -euo pipefail

FFMPEG_VERSION=8.1.3
FFMPEG_SHA256=7138d28c96d9d3e3af4ee3d8cad72741f8ffb40da90c1112235dea3ecd3178a3
DAV1D_VERSION=1.5.4
DAV1D_SHA256=686616b7c69eb88d44459391ab25cac13b6647a3b288835c5784e71c1514a5c5
MIN_IOS=17.0

# What the engine reads and decodes, and nothing else: no programs, network, hardware, GPL or nonfree parts.
# --disable-autodetect also turns off pthreads, which the VP9 and dav1d frame threads need back.
FFMPEG_FLAGS=(
  --disable-everything --disable-autodetect --disable-programs --disable-doc --disable-network
  --disable-avdevice --disable-avfilter --disable-debug
  --enable-static --disable-shared --enable-pic --enable-pthreads
  --enable-demuxer=matroska,mov
  --enable-decoder=vp9,libdav1d,opus,aac
  --enable-parser=vp9,av1,opus,aac
  --enable-libdav1d --enable-swresample --enable-swscale
)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/build/ffmpeg-work"
OUT="$ROOT/build/ffmpeg"
rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

fetch() { # url sha256
  local file="$WORK/$(basename "$1")"
  curl -fsSL --retry 3 -o "$file" "$1"
  echo "$2  $file" | shasum -a 256 -c -
  tar -xf "$file" -C "$WORK"
}
fetch "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz" "$FFMPEG_SHA256"
fetch "https://downloads.videolan.org/pub/videolan/dav1d/$DAV1D_VERSION/dav1d-$DAV1D_VERSION.tar.xz" "$DAV1D_SHA256"

build_slice() { # sdk target-triple meson-subsystem
  local sdk="$1" triple="$2" subsystem="$3"
  local sysroot cc prefix="$WORK/prefix-$sdk"
  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  cc="$(xcrun --sdk "$sdk" -f clang)"

  cat > "$WORK/$sdk.meson" <<EOF
[binaries]
c = ['$cc', '-target', '$triple', '-isysroot', '$sysroot']
ar = '$(xcrun --sdk "$sdk" -f ar)'
strip = '$(xcrun --sdk "$sdk" -f strip)'

[host_machine]
system = 'darwin'
subsystem = '$subsystem'
kernel = 'xnu'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'

[properties]
needs_exe_wrapper = true
EOF
  meson setup "$WORK/dav1d-build-$sdk" "$WORK/dav1d-$DAV1D_VERSION" --cross-file "$WORK/$sdk.meson" \
    --prefix "$prefix" --libdir lib --buildtype release --default-library static \
    -Dbitdepths=8,16 -Denable_asm=true -Denable_tools=false -Denable_tests=false -Denable_examples=false -Denable_docs=false
  ninja -C "$WORK/dav1d-build-$sdk" install

  local build="$WORK/ffmpeg-build-$sdk"
  mkdir -p "$build"
  (
    cd "$build"
    PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig" "$WORK/ffmpeg-$FFMPEG_VERSION/configure" \
      --prefix="$prefix" --enable-cross-compile --target-os=darwin --arch=aarch64 \
      --cc="$cc" --sysroot="$sysroot" --pkg-config=pkg-config --pkg-config-flags=--static \
      --extra-cflags="-target $triple" --extra-ldflags="-target $triple" \
      "${FFMPEG_FLAGS[@]}" | tee configure.log
    # The licence and the parts the engine relies on, as configure resolved them.
    grep -q '^License: LGPL version 2.1 or later$' configure.log
    for want in 'CONFIG_GPL 0' 'CONFIG_NONFREE 0' 'CONFIG_VERSION3 0' 'HAVE_PTHREADS 1' \
      'CONFIG_VP9_DECODER 1' 'CONFIG_LIBDAV1D_DECODER 1' 'CONFIG_OPUS_DECODER 1' 'CONFIG_AAC_DECODER 1' \
      'CONFIG_MATROSKA_DEMUXER 1' 'CONFIG_MOV_DEMUXER 1' 'CONFIG_SWSCALE 1' 'CONFIG_SWRESAMPLE 1'; do
      grep -q "^#define $want\$" config.h config_components.h || { echo "configure did not give $want"; exit 1; }
    done
    make -j"$(sysctl -n hw.ncpu)"
    make install
  )

  mkdir -p "$WORK/lib-$sdk"
  xcrun libtool -static -no_warning_for_no_symbols -o "$WORK/lib-$sdk/libFFmpeg.a" \
    "$prefix"/lib/libavformat.a "$prefix"/lib/libavcodec.a "$prefix"/lib/libswresample.a \
    "$prefix"/lib/libswscale.a "$prefix"/lib/libavutil.a "$prefix"/lib/libdav1d.a
  lipo -info "$WORK/lib-$sdk/libFFmpeg.a"
  otool -l "$WORK/lib-$sdk/libFFmpeg.a" | grep -m1 -A4 LC_BUILD_VERSION || true
}

build_slice iphoneos "arm64-apple-ios$MIN_IOS" ios
build_slice iphonesimulator "arm64-apple-ios$MIN_IOS-simulator" ios-simulator

xcodebuild -create-xcframework \
  -library "$WORK/lib-iphoneos/libFFmpeg.a" -headers "$WORK/prefix-iphoneos/include" \
  -library "$WORK/lib-iphonesimulator/libFFmpeg.a" -headers "$WORK/prefix-iphonesimulator/include" \
  -output "$OUT/FFmpeg.xcframework"

# The licences the app ships the libraries under (THIRD_PARTY_NOTICES.md), copied into its bundle at build time.
mkdir -p "$OUT/licenses"
cp "$WORK/ffmpeg-$FFMPEG_VERSION/COPYING.LGPLv2.1" "$OUT/licenses/FFmpeg-COPYING.LGPLv2.1.txt"
cp "$WORK/ffmpeg-$FFMPEG_VERSION/LICENSE.md" "$OUT/licenses/FFmpeg-LICENSE.md"
cp "$WORK/dav1d-$DAV1D_VERSION/COPYING" "$OUT/licenses/dav1d-COPYING.txt"
{
  echo "FFmpeg $FFMPEG_VERSION sha256 $FFMPEG_SHA256"
  echo "dav1d $DAV1D_VERSION sha256 $DAV1D_SHA256"
  echo "FFmpeg flags: ${FFMPEG_FLAGS[*]}"
  grep '^License:' "$WORK/ffmpeg-build-iphoneos/configure.log"
} > "$OUT/licenses/BUILD-INFO.txt"
cat "$OUT/licenses/BUILD-INFO.txt"
find "$OUT/FFmpeg.xcframework" -maxdepth 2 -name '*.a' -exec ls -l {} \;
