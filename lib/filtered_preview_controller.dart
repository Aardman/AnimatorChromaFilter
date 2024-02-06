 import 'package:flutter/services.dart';
import 'package:camera/camera.dart';  
import 'package:image/image.dart' as img;  

import 'dart:async';      
import 'package:flutter/material.dart';  
    

const MethodChannel _channel =  MethodChannel('animatorfilter');
 
abstract class FilteredPreviewController   {
 
    double _width = 0;
    double _height = 0;
    bool _isDisposed = false;
    bool _initialized = false;

    //metrics
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
   Future<void> update(CameraImage cameraImage); 
 
  //Concrete API implementations 

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

     //enable filtering
    await _channel.invokeMethod('enableFilters');
    //  final backgroundParams  = {'backgroundImage' : 'demo_background.jpg'};
    //  await _channel.invokeMapMethod<String, dynamic>('setBackgroundImagePath', backgroundParams);

  }  

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
      final params = {
        // "colour": getCurrentBaseHue(),
        // "backgroundPath": tempFileForChroma.path,
        // "sensitivity": getNormalisedSensitivityValue(_backgroundSensitivity),
      };
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

      // int rowStride    = cameraImage.planes[1].bytesPerRow;   // = 1280
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

class FilteredPreviewControllerIOS extends FilteredPreviewController {
   
  FilteredPreviewControllerIOS();
    
 
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
          'imageFormat':cameraImage.format.group.name,
          'width':  cameraImage.planes[0].width,
          'height': cameraImage.planes[0].height,
          'rowStride' : cameraImage.planes[0].bytesPerRow
        };

       Stopwatch stopwatch  = Stopwatch()..start();
      
       // call into plugin/iOS
       await _channel.invokeMapMethod<String, dynamic>('update', params); 
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