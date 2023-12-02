package com.aardman.animatorfilter;

//
//class ImageUtils {
//
//    ///Where this was used in the Camera plugin
//      /**
//     * GPUImage provides the GPUImageNativeLibrary with a native
//     * implementation for converting NV21 (YUV) planar byte array to RGB
//     * which is needed to load the input texture corresponding to glTextureId
//     */
//    public void onPreviewFrame(final byte[] data, final int width, final int height) {
//
//        if (glRgbPreviewBuffer == null) {
//            glRgbPreviewBuffer = IntBuffer.allocate(width * height);
//        }
//        if (openGLTaskQueue.isEmpty()) {
//            appendToTaskQueue(() -> {
//                GPUImageNativeLibrary.YUVtoRBGA(data, width, height, glRgbPreviewBuffer.array());
//                glTextureId = OpenGlUtils.loadTexture(glRgbPreviewBuffer, width, height, glTextureId);
//
//                if (imageWidth != width) {
//                    imageWidth = width;
//                    imageHeight = height;
//                    adjustImageScalingAndInitialiseBuffers();
//                }
//
//            }, openGLTaskQueue);
//        }
//        else {
//            Log.i(TAG, "DROPPED A FRAME FROM THE PREVIEW INPUT");
//        }
//
//    }
//
//
//    public static final byte[] generateNV21Data(@NotNull Image image) {
//        Rect crop = image.getCropRect();
//        int format = image.getFormat();
//        int width = crop.width();
//        int height = crop.height();
//        Image.Plane[] planes = image.getPlanes();
//        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
//        Image.Plane var10000 = planes[0];
//        //Intrinsics.checkNotNullExpressionValue(planes[0], "planes[0]");
//        byte[] rowData = new byte[var10000.getRowStride()];
//        int channelOffset = 0;
//        int outputStride = 1;
//        int i = 0;
//        // Intrinsics.checkNotNullExpressionValue(planes, "planes");
//
//        for(int var11 = planes.length; i < var11; ++i) {
//            switch(i) {
//                case 0:
//                    channelOffset = 0;
//                    outputStride = 1;
//                    break;
//                case 1:
//                    channelOffset = width * height + 1;
//                    outputStride = 2;
//                    break;
//                case 2:
//                    channelOffset = width * height;
//                    outputStride = 2;
//            }
//
//            var10000 = planes[i];
//            // Intrinsics.checkNotNullExpressionValue(planes[i], "planes[i]");
//            ByteBuffer buffer = var10000.getBuffer();
//            var10000 = planes[i];
//            // Intrinsics.checkNotNullExpressionValue(planes[i], "planes[i]");
//            int rowStride = var10000.getRowStride();
//            var10000 = planes[i];
//            // Intrinsics.checkNotNullExpressionValue(planes[i], "planes[i]");
//            int pixelStride = var10000.getPixelStride();
//            int shift = i == 0 ? 0 : 1;
//            int w = width >> shift;
//            int h = height >> shift;
//            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
//            int row = 0;
//
//            for(int var19 = h; row < var19; ++row) {
//                int length;
//                if (pixelStride == 1 && outputStride == 1) {
//                    length = w;
//                    buffer.get(data, channelOffset, w);
//                    channelOffset += w;
//                } else {
//                    length = (w - 1) * pixelStride + 1;
//                    buffer.get(rowData, 0, length);
//                    int col = 0;
//
//                    for(int var22 = w; col < var22; ++col) {
//                        data[channelOffset] = rowData[col * pixelStride];
//                        channelOffset += outputStride;
//                    }
//                }
//
//                if (row < h - 1) {
//                    buffer.position(buffer.position() + rowStride - length);
//                }
//            }
//        }
//        return data;
//    }
//
//}
//
