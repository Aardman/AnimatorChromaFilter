package com.aardman.animatorfilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size

object ImageProcessing {

    /**
     * Gets the background scaled and cropped to the desired targetSize
     *
     * @param filePath    fully qualified path the the background image source
     * @param targetSize  desired size of the background
     * @param isLandscape
     * @return
     */
    fun getBackground(filePath: String?, targetSize: Size, isLandscape: Boolean): Bitmap {
        var backgroundBitmap = BitmapFactory.decodeFile(filePath)
        if (backgroundBitmap == null) {
            createImage(targetSize.width, targetSize.height, Color.MAGENTA)
        }
        return prepareBitmap(backgroundBitmap, targetSize, isLandscape)
    }

    /**
     * Prepare the bitmap for use in the chroma filter
     *
     * @param sourceBitmap The source bitmap/background image
     * @param targetSize   The desired output size of the image
     * @param isLandscape Indicates the orientation to be processed
     * @return a scaled and translated bitmap that is a center crop of the input
     */
    fun prepareBitmap(sourceBitmap: Bitmap, targetSize: Size, isLandscape: Boolean): Bitmap {
        val w = sourceBitmap.width
        val h = sourceBitmap.height

        var outputWidth = targetSize.width
        var outputHeight = targetSize.height

        if (!isLandscape) {
            outputWidth = targetSize.height
            outputHeight = targetSize.width
        }

        // Calculate scale from height
        val scaleFactor = h.toFloat() / outputHeight.toFloat()
        val scaledWidth = w / scaleFactor

        // Add scale transformation
        val matrix = Matrix().apply { postScale(1 / scaleFactor, 1 / scaleFactor) }

        // Create the output bitmap with the supplied transforms
        var outputBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, w, h, matrix, true)

        // Now need to crop and translate
        val translationInX = (scaledWidth - outputWidth) / 2
        outputBitmap = Bitmap.createBitmap(outputBitmap, translationInX.toInt(), 0, outputWidth, outputHeight)

        if (!isLandscape) {
            outputBitmap = rotateBitmap(outputBitmap)
        }

        sourceBitmap.recycle()

        return outputBitmap
    }

    private fun rotateBitmap(sourceBitmap: Bitmap): Bitmap {
        val rotationMatrix = Matrix().apply { postRotate(-90f) }
        return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, rotationMatrix, true)
    }

    /**
     * Generates a solid colour
     * @param width Width of the image
     * @param height Height of the image
     * @param color Color of the image
     * @return A one color image with the given width and height.
     */
    fun createImage(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { setColor(color) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    /**
     * Creates a YUV420 semi-planar (yuv420sp) array from separate Y, U, and V planes.
     * This method combines the Y plane as is and interleaves the U and V planes.
     *
     * @param yPlane The Y plane data as an unsigned byte array.
     * @param uPlane The U plane data as an unsigned byte array.
     * @param vPlane The V plane data as an unsigned byte array.
     * @param width The width of the image.
     * @param height The height of the image.
     * @return A yuv420sp array combining the Y, U, and V planes.
     */
    fun createYUV420SP(yPlane: ByteArray, uPlane: ByteArray, vPlane: ByteArray, width: Int, height: Int): ByteArray {
        val yuv420sp = ByteArray(width * height * 3 / 2)
        val ySize = width * height
        val uvSize = ySize / 4

        // Copy Y plane
        System.arraycopy(yPlane, 0, yuv420sp, 0, ySize)

        // Interleave U and V planes
        for (i in 0 until uvSize) {
            yuv420sp[ySize + 2 * i] = uPlane[i]
            yuv420sp[ySize + 2 * i + 1] = vPlane[i]
        }

        return yuv420sp
    }

}