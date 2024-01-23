import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:camera/camera.dart';
import 'package:image/image.dart';
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

    var time = 0;
    var iterations = 0;


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

  //TODO Implement correct parameters passing ( colour, sensitivity, backgroundImagePath)
  Future<void> updateFilters(Object params) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try {
      final params = {};
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


  Future<void> update(CameraImage cameraImage) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    if (cameraImage == null || cameraImage.planes == null) {
      print('Camera image or planes are null');
      return;
    }

    try {
      Uint8List yBytes = cameraImage.planes[0].bytes; // Y plane
      Uint8List uBytes = cameraImage.planes[1].bytes; // U plane
      Uint8List vBytes = cameraImage.planes[2].bytes; // V plane

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