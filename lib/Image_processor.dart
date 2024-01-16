import 'dart:typed_data';  
import 'dart:ui';
import 'package:flutter/foundation.dart'; 
import 'package:camera/camera.dart';

class ImageProcessor  {  

  static Uint8List getBytes(CameraImage image) {
    final WriteBuffer allBytes = WriteBuffer();
    for (Plane plane in image.planes) {
      allBytes.putUint8List(plane.bytes);
    }
    final bytes = allBytes.done().buffer.asUint8List();
    return bytes;
  }

}