#ifndef FLOW_FFMPEG_SHIM_H
#define FLOW_FFMPEG_SHIM_H

// The parts of FFmpeg the bundled engine (iosApp/iosApp/Engine) uses, and helpers for what Swift cannot import:
// function-like macros, and fixed-size arrays inside FFmpeg's structs.
#include <errno.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/channel_layout.h>
#include <libavutil/pixdesc.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>

static inline int flow_averror_eagain(void) { return AVERROR(EAGAIN); }
static inline int flow_averror_eof(void) { return AVERROR_EOF; }
static inline int flow_averror_exit(void) { return AVERROR_EXIT; }
static inline int flow_averror_eio(void) { return AVERROR(EIO); }
static inline int64_t flow_nopts(void) { return AV_NOPTS_VALUE; }
static inline int flow_is_keyframe(const AVPacket *packet) { return (packet->flags & AV_PKT_FLAG_KEY) != 0; }

/** Bits per component of a decoded picture: 8 for yuv420p, 10 for yuv420p10le. */
static inline int flow_pixel_depth(int format) {
    const AVPixFmtDescriptor *descriptor = av_pix_fmt_desc_get((enum AVPixelFormat)format);
    return descriptor ? descriptor->comp[0].depth : 0;
}

/**
 * A converter from a decoded picture to the bi-planar layout Core Video and AVSampleBufferDisplayLayer take: NV12, or
 * P010 for more than 8 bits. Same size, so it only interleaves the chroma planes and needs no filter. Opaque to Swift.
 */
static inline void *flow_biplanar_converter(void *previous, int width, int height, int format, int deep) {
    return sws_getCachedContext((SwsContext *)previous, width, height, (enum AVPixelFormat)format, width, height,
                                deep ? AV_PIX_FMT_P010LE : AV_PIX_FMT_NV12, SWS_POINT, NULL, NULL, NULL);
}

static inline int flow_convert_to_biplanar(void *converter, const AVFrame *frame, uint8_t *luma, int lumaStride,
                                           uint8_t *chroma, int chromaStride) {
    uint8_t *planes[4] = {luma, chroma, NULL, NULL};
    int strides[4] = {lumaStride, chromaStride, 0, 0};
    return sws_scale((SwsContext *)converter, (const uint8_t *const *)frame->data, frame->linesize, 0, frame->height,
                     planes, strides);
}

static inline void flow_free_converter(void *converter) { sws_freeContext((SwsContext *)converter); }

/** Sets up conversion of [frame]'s sound to interleaved 32-bit float in [channels] channels, at its own rate. */
static inline int flow_resampler(SwrContext **resampler, const AVFrame *frame, int channels) {
    AVChannelLayout layout;
    av_channel_layout_default(&layout, channels);
    int result = swr_alloc_set_opts2(resampler, &layout, AV_SAMPLE_FMT_FLT, frame->sample_rate, &frame->ch_layout,
                                     (enum AVSampleFormat)frame->format, frame->sample_rate, 0, NULL);
    av_channel_layout_uninit(&layout);
    return result < 0 ? result : swr_init(*resampler);
}

/** Converts [frame]'s samples into [out], which holds [capacity] samples per channel; returns how many were written. */
static inline int flow_resample(SwrContext *resampler, const AVFrame *frame, float *out, int capacity) {
    uint8_t *planes[1] = {(uint8_t *)out};
    return swr_convert(resampler, planes, capacity, (const uint8_t *const *)frame->extended_data, frame->nb_samples);
}

#endif
