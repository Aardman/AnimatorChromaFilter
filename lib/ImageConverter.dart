import 'dart:typed_data';  
import 'dart:ui';
import 'package:flutter/foundation.dart'; 
import 'package:camera/camera.dart';


class ImageConverter  {  
 
static Uint8List convertCameraImageToArgb(CameraImage image) {
  final WriteBuffer allBytes = WriteBuffer();
  for (Plane plane in image.planes) {
    allBytes.putUint8List(plane.bytes);
  }
  final bytes = allBytes.done().buffer.asUint8List();

  final Size imageSize = Size(image.width.toDouble(), image.height.toDouble());
  final ImageFormatGroup format = image.format.group;

  if (format == ImageFormatGroup.yuv420) {
    return convertYUV420ToARGB(bytes, imageSize);
  } else if (format == ImageFormatGroup.bgra8888) {
    return bytes;
  } else {
    throw UnsupportedError("Unknown image format: $format");
  }
}

static Uint8List convertYUV420ToARGB(Uint8List bytes, Size imageSize) {
  int width = imageSize.width.toInt();
  int height = imageSize.height.toInt();

  var img = Uint8List(width * height * 4);
  int uvIndex, index, yp, up, vp;
  int r, g, b, y1192, u, v;
  for (int j = 0; j < height; j++) {
    for (int i = 0; i < width; i++) {
      uvIndex = (i ~/ 2) * (height ~/ 2) + (j ~/ 2);
      index = j * width + i;
      yp = bytes[index];
      up = bytes[uvIndex + width * height];
      vp = bytes[uvIndex + width * height + 1];

      yp = yp - 16;
      u = up - 128;
      v = vp - 128;

      y1192 = 1192 * yp;
      r = (y1192 + 1634 * v).toInt();
      g = (y1192 - 833 * v - 400 * u).toInt();
      b = (y1192 + 2066 * u).toInt();

      r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
      g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
      b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);

      img[index * 4 + 0] = 0xff; // alpha channel
      img[index * 4 + 1] = (r >> 10) & 0xff; // red channel
      img[index * 4 + 2] = (g >> 10) & 0xff; // green channel
      img[index * 4 + 3] = (b >> 10) & 0xff; // blue channel
    }
  }

  return img;
}

}