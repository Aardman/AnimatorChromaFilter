package com.aardman.animatorfilter

import android.graphics.Bitmap

object AnimatorNativeLibrary {
    init {
        System.loadLibrary("yuv-decoder")
    }

    external fun YUVtoRBGA(yuv: ByteArray?, width: Int, height: Int, out: IntArray?)
    external fun YUVtoARBG(yuv: ByteArray?, width: Int, height: Int, out: IntArray?)
    external fun adjustBitmap(srcBitmap: Bitmap?)
}