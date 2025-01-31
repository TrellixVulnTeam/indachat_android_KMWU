#include <jni.h>
#include <stdio.h>
#include <setjmp.h>
#include <android/bitmap.h>
#include <libwebp/src/webp/decode.h>
#include <libwebp/src/webp/encode.h>
#include "utils.h"
#include "image.h"

jclass jclass_NullPointerException;
jclass jclass_RuntimeException;

jclass jclass_Options;
jfieldID jclass_Options_inJustDecodeBounds;
jfieldID jclass_Options_outHeight;
jfieldID jclass_Options_outWidth;

const uint32_t PGPhotoEnhanceHistogramBins = 256;
const uint32_t PGPhotoEnhanceSegments = 4;

jclass createGlobarRef(JNIEnv *env, jclass class) {
    if (class) {
        return (*env)->NewGlobalRef(env, class);
    }
    return 0;
}

jint imageOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env) {
    jclass_NullPointerException = createGlobarRef(env, (*env)->FindClass(env, "java/lang/NullPointerException"));
    if (jclass_NullPointerException == 0) {
        return -1;
    }
    jclass_RuntimeException = createGlobarRef(env, (*env)->FindClass(env, "java/lang/RuntimeException"));
    if (jclass_RuntimeException == 0) {
        return -1;
    }
    
    jclass_Options = createGlobarRef(env, (*env)->FindClass(env, "android/graphics/BitmapFactory$Options"));
    if (jclass_Options == 0) {
        return -1;
    }
    jclass_Options_inJustDecodeBounds = (*env)->GetFieldID(env, jclass_Options, "inJustDecodeBounds", "Z");
    if (jclass_Options_inJustDecodeBounds == 0) {
        return -1;
    }
    jclass_Options_outHeight = (*env)->GetFieldID(env, jclass_Options, "outHeight", "I");
    if (jclass_Options_outHeight == 0) {
        return -1;
    }
    jclass_Options_outWidth = (*env)->GetFieldID(env, jclass_Options, "outWidth", "I");
    if (jclass_Options_outWidth == 0) {
        return -1;
    }
    
    return JNI_VERSION_1_6;
}

static inline uint64_t getColors(const uint8_t *p) {
    return p[0] + (p[1] << 16) + ((uint64_t) p[2] << 32);
}

static inline uint64_t getColors565(const uint8_t *p) {
    uint16_t *ps = (uint16_t *) p;
    return ((((ps[0] & 0xF800) >> 11) * 255) / 31) + (((((ps[0] & 0x07E0) >> 5) * 255) / 63) << 16) + ((uint64_t) (((ps[0] & 0x001F) * 255) / 31) << 32);
}

static void fastBlurMore(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;
    
    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 4) {
        return;
    }

    uint64_t *rgb = malloc(w * h * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);
        
        for (i = 1; i <= radius; i++) {
            cur = getColors(&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }
        
        x = 0;
        
    #define update(start, middle, end) \
            rgb[y * w + x] = (rgbsum >> 6) & 0x00FF00FF00FF00FF; \
            rgballsum += getColors(&pix[yw + (start) * 4]) - 2 * getColors(&pix[yw + (middle) * 4]) + getColors(&pix[yw + (end) * 4]); \
            rgbsum += rgballsum; \
            x++; \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
    #undef update
        
        yw += stride;
    }
    
    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }
        
        y = 0;
        int32_t yi = x * 4;
        
    #define update(start, middle, end) \
            int64_t res = rgbsum >> 6; \
            pix[yi] = res;              \
            pix[yi + 1] = res >> 16;    \
            pix[yi + 2] = res >> 32;    \
            rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
            rgbsum += rgballsum; \
            y++; \
            yi += stride;
        
        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
    #undef update
    }
}

static void fastBlur(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    if (pix == NULL) {
        return;
    }
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;
    int32_t shift;
    if (radius == 1) {
        shift = 2;
    } else if (radius == 3) {
        shift = 4;
    } else if (radius == 7) {
        shift = 6;
    } else if (radius == 15) {
        shift = 8;
    } else {
        return;
    }
    
    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 4) {
        return;
    }

    uint64_t *rgb = malloc(w * h * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);
        
        for (i = 1; i <= radius; i++) {
            cur = getColors(&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }
        
        x = 0;
        
        #define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> shift) & 0x00FF00FF00FF00FFLL; \
                rgballsum += getColors(&pix[yw + (start) * 4]) - 2 * getColors(&pix[yw + (middle) * 4]) + getColors(&pix[yw + (end) * 4]); \
                rgbsum += rgballsum;        \
                x++;                        \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
        
        #undef update
        
        yw += stride;
    }
    
    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }
        
        y = 0;
        int32_t yi = x * 4;
        
        #define update(start, middle, end)  \
                int64_t res = rgbsum >> shift;   \
                pix[yi] = res;              \
                pix[yi + 1] = res >> 16;    \
                pix[yi + 2] = res >> 32;    \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;
        
        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
        #undef update
    }
    
    free(rgb);
}

static void fastBlurMore565(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 2) {
        return;
    }

    uint64_t *rgb = malloc(w * h * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors565(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors565(&pix[yw + i * 2]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end) \
            rgb[y * w + x] = (rgbsum >> 6) & 0x00FF00FF00FF00FF; \
            rgballsum += getColors565(&pix[yw + (start) * 2]) - 2 * getColors565(&pix[yw + (middle) * 2]) + getColors565(&pix[yw + (end) * 2]); \
            rgbsum += rgballsum; \
            x++; \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 2;

#define update(start, middle, end) \
            int64_t res = rgbsum >> 6; \
            pix[yi] = ((res >> 13) & 0xe0) | ((res >> 35) & 0x1f); \
            pix[yi + 1] = (res & 0xf8) | ((res >> 21) & 0x7); \
            rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
            rgbsum += rgballsum; \
            y++; \
            yi += stride;

        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
#undef update
    }
}

static void fastBlur565(int32_t w, int32_t h, int32_t stride, uint8_t *pix, int32_t radius) {
    if (pix == NULL) {
        return;
    }
    const int32_t r1 = radius + 1;
    const int32_t div = radius * 2 + 1;
    int32_t shift;
    if (radius == 1) {
        shift = 2;
    } else if (radius == 3) {
        shift = 4;
    } else if (radius == 7) {
        shift = 6;
    } else if (radius == 15) {
        shift = 8;
    } else {
        return;
    }

    if (radius > 15 || div >= w || div >= h || w * h > 150 * 150 || stride > w * 2) {
        return;
    }

    uint64_t *rgb = malloc(w * h * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }

    int32_t x, y, i;

    int32_t yw = 0;
    const int32_t we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = getColors565(&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);

        for (i = 1; i <= radius; i++) {
            cur = getColors565(&pix[yw + i * 2]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }

        x = 0;

#define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> shift) & 0x00FF00FF00FF00FFLL; \
                rgballsum += getColors565(&pix[yw + (start) * 2]) - 2 * getColors565(&pix[yw + (middle) * 2]) + getColors565(&pix[yw + (end) * 2]); \
                rgbsum += rgballsum;        \
                x++;

        while (x < r1) {
            update(0, x, x + r1);
        }
        while (x < we) {
            update(x - r1, x, x + r1);
        }
        while (x < w) {
            update(x - r1, x, w - 1);
        }

#undef update

        yw += stride;
    }

    const int32_t he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }

        y = 0;
        int32_t yi = x * 2;

#define update(start, middle, end)  \
                uint64_t res = rgbsum >> shift;   \
                pix[yi] = ((res >> 13) & 0xe0) | ((res >> 35) & 0x1f); \
                pix[yi + 1] = (res & 0xf8) | ((res >> 21) & 0x7); \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;

        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
#undef update
    }

    free(rgb);
}

JNIEXPORT void Java_org_indachat_messenger_Utilities_blurBitmap(JNIEnv *env, jclass class, jobject bitmap, jint radius, jint unpin, jint width, jint height, jint stride) {
    if (!bitmap) {
        return;
    }
    
    if (!width || !height || !stride) {
        return;
    }
    
    void *pixels = 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return;
    }
    if (stride == width * 2) {
        if (radius <= 3) {
            fastBlur565(width, height, stride, pixels, radius);
        } else {
            fastBlurMore565(width, height, stride, pixels, radius);
        }
    } else {
        if (radius <= 3) {
            fastBlur(width, height, stride, pixels, radius);
        } else {
            fastBlurMore(width, height, stride, pixels, radius);
        }
    }
    if (unpin) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
}

JNIEXPORT void Java_org_indachat_messenger_Utilities_calcCDT(JNIEnv *env, jclass class, jobject hsvBuffer, jint width, jint height, jobject buffer) {
    float imageWidth = width;
    float imageHeight = height;
    float _clipLimit = 1.25f;

    uint32_t totalSegments = PGPhotoEnhanceSegments * PGPhotoEnhanceSegments;
    uint32_t tileArea = (uint32_t) (floorf(imageWidth / PGPhotoEnhanceSegments) * floorf(imageHeight / PGPhotoEnhanceSegments));
    uint32_t clipLimit = (uint32_t) max(1, _clipLimit * tileArea / (float) PGPhotoEnhanceHistogramBins);
    float scale = 255.0f / (float) tileArea;

    unsigned char *bytes = (*env)->GetDirectBufferAddress(env, hsvBuffer);

    uint32_t **hist = calloc(totalSegments, sizeof(uint32_t *));
    uint32_t **cdfs = calloc(totalSegments, sizeof(uint32_t *));
    uint32_t *cdfsMin = calloc(totalSegments, sizeof(uint32_t));
    uint32_t *cdfsMax = calloc(totalSegments, sizeof(uint32_t));
    
    for (uint32_t a = 0; a < totalSegments; a++) {
        hist[a] = calloc(PGPhotoEnhanceHistogramBins, sizeof(uint32_t));
        cdfs[a] = calloc(PGPhotoEnhanceHistogramBins, sizeof(uint32_t));
    }
    
    float xMul = PGPhotoEnhanceSegments / imageWidth;
    float yMul = PGPhotoEnhanceSegments / imageHeight;
    
    for (uint32_t y = 0; y < imageHeight; y++) {
        uint32_t yOffset = y * width * 4;
        for (uint32_t x = 0; x < imageWidth; x++) {
            uint32_t index = x * 4 + yOffset;
            
            uint32_t tx = (uint32_t)(x * xMul);
            uint32_t ty = (uint32_t)(y * yMul);
            uint32_t t = ty * PGPhotoEnhanceSegments + tx;
            
            hist[t][bytes[index + 2]]++;
        }
    }
    
    for (uint32_t i = 0; i < totalSegments; i++) {
        if (clipLimit > 0) {
            uint32_t clipped = 0;
            for (uint32_t j = 0; j < PGPhotoEnhanceHistogramBins; ++j) {
                if (hist[i][j] > clipLimit) {
                    clipped += hist[i][j] - clipLimit;
                    hist[i][j] = clipLimit;
                }
            }
            
            uint32_t redistBatch = clipped / PGPhotoEnhanceHistogramBins;
            uint32_t residual = clipped - redistBatch * PGPhotoEnhanceHistogramBins;
            
            for (uint32_t j = 0; j < PGPhotoEnhanceHistogramBins; ++j) {
                hist[i][j] += redistBatch;
            }
            
            for (uint32_t j = 0; j < residual; ++j) {
                hist[i][j]++;
            }
        }
        memcpy(cdfs[i], hist[i], PGPhotoEnhanceHistogramBins * sizeof(uint32_t));
        
        uint32_t hMin = PGPhotoEnhanceHistogramBins - 1;
        for (uint32_t j = 0; j < hMin; ++j) {
            if (cdfs[j] != 0) {
                hMin = j;
            }
        }
        
        uint32_t cdf = 0;
        for (uint32_t j = hMin; j < PGPhotoEnhanceHistogramBins; ++j) {
            cdf += cdfs[i][j];
            cdfs[i][j] = (uint8_t) min(255, cdf * scale);
        }
        
        cdfsMin[i] = cdfs[i][hMin];
        cdfsMax[i] = cdfs[i][PGPhotoEnhanceHistogramBins - 1];
    }
    
    uint32_t resultSize = 4 * PGPhotoEnhanceHistogramBins * totalSegments;
    uint32_t resultBytesPerRow = 4 * PGPhotoEnhanceHistogramBins;
    
    unsigned char *result = (*env)->GetDirectBufferAddress(env, buffer);
    for (uint32_t tile = 0; tile < totalSegments; tile++) {
        uint32_t yOffset = tile * resultBytesPerRow;
        for (uint32_t i = 0; i < PGPhotoEnhanceHistogramBins; i++) {
            uint32_t index = i * 4 + yOffset;
            result[index] = (uint8_t)cdfs[tile][i];
            result[index + 1] = (uint8_t)cdfsMin[tile];
            result[index + 2] = (uint8_t)cdfsMax[tile];
            result[index + 3] = 255;
        }
    }

    for (uint32_t a = 0; a < totalSegments; a++) {
        free(hist[a]);
        free(cdfs[a]);
    }
    free(hist);
    free(cdfs);
    free(cdfsMax);
    free(cdfsMin);
}

JNIEXPORT jint Java_org_indachat_messenger_Utilities_pinBitmap(JNIEnv *env, jclass class, jobject bitmap) {
    if (bitmap == NULL) {
        return 0;
    }
    unsigned char *pixels;
    return AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 ? 1 : 0;
}

JNIEXPORT void Java_org_indachat_messenger_Utilities_unpinBitmap(JNIEnv *env, jclass class, jobject bitmap) {
    if (bitmap == NULL) {
        return;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT jboolean Java_org_indachat_messenger_Utilities_loadWebpImage(JNIEnv *env, jclass class, jobject outputBitmap, jobject buffer, jint len, jobject options, jboolean unpin) {
    if (!buffer) {
        (*env)->ThrowNew(env, jclass_NullPointerException, "Input buffer can not be null");
        return 0;
    }
    
    jbyte *inputBuffer = (*env)->GetDirectBufferAddress(env, buffer);
    
    int32_t bitmapWidth = 0;
    int32_t bitmapHeight = 0;
    if (!WebPGetInfo((uint8_t*)inputBuffer, len, &bitmapWidth, &bitmapHeight)) {
        (*env)->ThrowNew(env, jclass_RuntimeException, "Invalid WebP format");
        return 0;
    }
    
    if (options && (*env)->GetBooleanField(env, options, jclass_Options_inJustDecodeBounds) == JNI_TRUE) {
        (*env)->SetIntField(env, options, jclass_Options_outWidth, bitmapWidth);
        (*env)->SetIntField(env, options, jclass_Options_outHeight, bitmapHeight);
        return 1;
    }
    
    if (!outputBitmap) {
        (*env)->ThrowNew(env, jclass_NullPointerException, "output bitmap can not be null");
        return 0;
    }
    
    AndroidBitmapInfo bitmapInfo;
    if (AndroidBitmap_getInfo(env, outputBitmap, &bitmapInfo) != ANDROID_BITMAP_RESUT_SUCCESS) {
        (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to get Bitmap information");
        return 0;
    }
    
    void *bitmapPixels = 0;
    if (AndroidBitmap_lockPixels(env, outputBitmap, &bitmapPixels) != ANDROID_BITMAP_RESUT_SUCCESS) {
        (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to lock Bitmap pixels");
        return 0;
    }
    
    if (!WebPDecodeRGBAInto((uint8_t*)inputBuffer, len, (uint8_t*)bitmapPixels, bitmapInfo.height * bitmapInfo.stride, bitmapInfo.stride)) {
        AndroidBitmap_unlockPixels(env, outputBitmap);
        (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to decode webp image");
        return 0;
    }
    
    if (unpin && AndroidBitmap_unlockPixels(env, outputBitmap) != ANDROID_BITMAP_RESUT_SUCCESS) {
        (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to unlock Bitmap pixels");
        return 0;
    }
    
    return 1;
}
