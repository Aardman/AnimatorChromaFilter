import 'package:flutter/services.dart'; 
import 'package:camera/camera.dart';
import 'Image_processor.dart';
import 'package:image/image.dart' as img;

const MethodChannel _channel =  MethodChannel('animatorfilter');

class FilteredPreviewController {
    FilteredPreviewController();

    int _textureId = -1;
    double _width = 0;
    double _height = 0;
    bool _isDisposed = false;
    bool _initialized = false;

//Getters 
 bool get initialized {
    return _initialized;
  }

  int get textureId {
    return _textureId;
  }

  double get width {
    return _width;
  }

  double get height {
    return _height;
  }

//Lifecycle
 
 Future<void> initialize(double width, double height) async {
    if (_isDisposed) {
      throw Exception('Disposed FilterPreviewController');
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

  Future<void> dispose() async {
    if (_isDisposed) {
      return;
    }

    // Dispose the filter on the native platform
    _channel.invokeMethod('dispose');
    _isDisposed = true;
  }

//API
 
   Future<void> update(CameraImage cameraImage) async {
  
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }
  
   //Profiling code
    //Stopwatch stopwatch  = Stopwatch()..start(); 
    Uint8List formattedImage =ImageProcessor.getBytes(cameraImage);  
    //stopwatch.stop();
    //print('update executed  in  ${stopwatch.elapsedMilliseconds}');
 
    // Call the filter update method on the native platform 
    final params = {'image': formattedImage, 'width': width, 'height': height}; 
    await _channel.invokeMethod('update', params);
  }   
    
}