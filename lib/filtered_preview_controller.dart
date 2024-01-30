import 'dart:ffi';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:camera/camera.dart';
import 'package:image/image.dart';
import 'Image_processor.dart';
import 'package:image/image.dart' as img;

const MethodChannel _channel =  MethodChannel('animatorfilter');
 
abstract class FilteredPreviewController   {


    double _width = 0;
    double _height = 0;
    bool _isDisposed = false;
    bool _initialized = false;

    var time = 0;
    var iterations = 0;  

    //Platform specific 
    //iOS
    Uint8List _imageBytes = Uint8List(0);
    //Android
    int _textureId = -1; 

    //Getters 
    bool get initialized {
     return _initialized;
    }

    double get width {
      return _width;
    }

    double get height {
      return _height;
    }

    //Platform specific 

    //iOS
    Uint8List get imageBytes {
      return _imageBytes;
    } 

    //Android  
    int get textureId {
      return _textureId;
    }

   //Abstract Non-common platform specific API requirements
   Future<void> initialize(double width, double height);
   Future<void> update(CameraImage cameraImage); 

  //Concrete API implementations
 
  //TODO Implement correct parameters passing ( colour, sensitivity, backgroundImagePath)
  /*
  var data = {
      "colour": getCurrentBaseHue(),
      "backgroundPath": tempFileForChroma.path,
      "sensitivity": getNormalisedSensitivityValue(_backgroundSensitivity),
      // "sensitivity": 0.3,
    };
  */
  Future<void> updateFilters(Object params) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try {
     // final params = {};
      await _channel.invokeMethod('updateFilters', params);
    } catch (e) {
      print('Error processing camera image: $e');
    }
  } 

  Future<void> setBackgroundImagePath(String backgroundImagePath) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try {
        final params = {'img': backgroundImagePath, 'width': 1280, 'height': 720};
        await _channel.invokeMethod('setBackgroundImagePath', params);
    } catch (e) {
      print('Error processing camera image: $e');
    }
  }
  
    //Common Lifecycle 
    Future<void> dispose() async {
    if (_isDisposed) {
      return;
    }

    // Dispose the filter on the native platform
    _channel.invokeMethod('dispose');
    _isDisposed = true;
  } 

}


//Implementations of platform specific initialisation and image update
class FilteredPreviewControllerAndroid extends FilteredPreviewController {
   
    FilteredPreviewControllerAndroid();
   
 Future<void> initialize(double width, double height) async {
    if (_isDisposed) {
      throw Exception('Disposed FilterPreviewControllerAndroid');
    }

    //Retain values locally for processing images from stream
    _width = width;
    _height = height;

   // Initialize the filter on the native platform
  //final params = {'img': bytes.buffer.asUint8List(0), 'width': width, 'height': height};
  final params = {'width': width, 'height': height};
  final reply = await _channel.invokeMapMethod<String, dynamic>('create', params); 
  _initialized = true;
  _textureId = reply!['textureId'];
 }
  
  Future<void> update(CameraImage cameraImage) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    if (cameraImage == null || cameraImage.planes == null) {
      print('Camera image or planes are null');
      return;
    }

    try {
      /**
       * NB: The U and V planes have a rowStride the width of the Y plane and  a pixel stride of 2
       * The chrominance data needs to be sampled correctly from this to create textures for image conversion
       */
      Uint8List yBytes = cameraImage.planes[0].bytes; // Y plane
      Uint8List uBytes = cameraImage.planes[1].bytes; // U plane
      Uint8List vBytes = cameraImage.planes[2].bytes; // V plane

      // int rowStride    = cameraImage.planes[1].bytesPerRow;    // = 1280
      // int? pixelStride = cameraImage.planes[1].bytesPerPixel; //  = 2

      if (yBytes == null || uBytes == null || vBytes == null) {
        print('One of the planes is null');
        return;
      }

      // Call the filter update method on the native platform
      final params = {
        'Y': yBytes,
        'U': uBytes,
        'V': vBytes,
        'width': width,
        'height': height
      };

      Stopwatch stopwatch  = Stopwatch()..start();
      await _channel.invokeMethod('update', params);
      stopwatch.stop();

      time += stopwatch.elapsedMilliseconds;
      iterations = iterations + 1;

      if (iterations == 100){
          print('100 updates executed in average of ${time/iterations}, time: ${time}');
          print('seconds = ${time/1000}, fps = ${iterations/(time/1000)}');
      }

    } catch (e) {
      print('Error processing camera image: $e');
    }
  } 
}
  

class FilteredPreviewControllerIoS extends FilteredPreviewController {
   
    FilteredPreviewControllerIoS();
   
 
 Future<void> initialize(double width, double height) async {
    if (_isDisposed) {
      throw Exception('Disposed FilterPreviewControllerIoS');
    }

    //Retain values locally for processing images from stream
    _width = width;
    _height = height;

   // Initialize the filter on the native platform
  //final params = {'img': bytes.buffer.asUint8List(0), 'width': width, 'height': height};
  final params = {'width': width, 'height': height};
  await _channel.invokeMapMethod<String, dynamic>('create', params); 
  _initialized = true; 
 } 
 

  Future<void> update(CameraImage cameraImage) async {
    if (!_initialized) {
      throw Exception('FilterControlrler not initialized');
    }

    if (cameraImage == null || cameraImage.planes == null) {
      print('Camera image or planes are null');
      return;
    }

    try { 

      Uint8List data = cameraImage.planes[0].bytes;  
 
      final params = {
        'imageData': data,
        'width':  cameraImage.planes[0].width,
        'height': cameraImage.planes[0].height,
        'rowstride' : cameraImage.planes[0].bytesPerRow
      };

      Stopwatch stopwatch  = Stopwatch()..start();
     
      final reply = await _channel.invokeMapMethod<String, dynamic>('update', params); 
      _initialized = true;
      Uint8List imageData = reply!['imageBytes'];
      _imageBytes = convertBGRA8888toRGBA8888(imageData);
      stopwatch.stop();

      time += stopwatch.elapsedMilliseconds;
      iterations = iterations + 1;

      if (iterations == 100){
          print('100 updates executed in average of ${time/iterations}, time: ${time}');
          print('seconds = ${time/1000}, fps = ${iterations/(time/1000)}');
      }

    } catch (e) {
      print('Error processing camera image: $e');
    }
  }

 Uint8List convertUnmodifiableUint8ArrayViewToUint8List(Uint8List unmodifiableArray) {
  return Uint8List.fromList(unmodifiableArray);
}

//TODO: perform in CoreImage on iOS side
Uint8List convertBGRA8888toRGBA8888(Uint8List bgra) {
  Uint8List rgba = convertUnmodifiableUint8ArrayViewToUint8List(bgra);
  for (int i = 0; i < rgba.length; i += 4) {
    // Swap the B and R channels.
    var temp = rgba[i];
    rgba[i] = rgba[i + 2];
    rgba[i + 2] = temp;
  }
  return rgba;
} 

    
}