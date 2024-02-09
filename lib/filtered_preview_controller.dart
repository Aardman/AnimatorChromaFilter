import 'package:flutter/services.dart';
import 'package:camera/camera.dart';    
import 'dart:async';         
     
 
abstract class FilteredPreviewController   {
 
    static const MethodChannel _channel =  MethodChannel('animatorfilter');
 
    double _width = 0;
    double _height = 0;
    bool _isDisposed = false;
    bool _initialized = false;
    int _textureId = -1; 

    // Public getter for _channel
    static MethodChannel get channel => _channel;
   
    //Properties 

    bool get initialized => _initialized; 

    double get width  => _width;

    double get height => _height;
  
    int get textureId  => _textureId; 

   //Abstract Non-common platform specific API requirements 
   ///render filtered  cameraImage to the Flutter texture
   Future<void> update(CameraImage cameraImage);  
   ///filter the cameraImage and return JPEG encoded bytes
   Future<Uint8List> processStillFrame(CameraImage cameraImage);
 

  //Concrete API implementations 

  /// This needs to be called before calls to update(cameraImage)
  /// 
  /// width and height must correspond to the current camera frame size and aspect ratio
  /// as these are used to create GPU textures
  /// 
  /// This should also be called on orientation changes to reset the texture
  Future<void> initialize(double width, double height) async {
   
      if (_isDisposed) {
        throw Exception('Disposed FilterPreviewControllerAndroid');
      }

      //Retain values locally for processing images from stream
      _width = width;
      _height = height;

    // Initialize the texture on the native platform 
    final params = {'width': width, 'height': height};
    final reply = await _channel.invokeMapMethod<String, dynamic>('create', params); 
    _initialized = true;
    _textureId = reply!['textureId']; 
  
  }   
 
    
  /// 
  /// @params - Parameter list as an object with the following types
  /// 
  /// { 
  ///   'colour', [int, int, int], flutter 
  ///   'threshold', float,
  ///   'smoothing', float
  /// }
  /// 
  /// R, G, B values in colour int array range from 0 - 255
  Future<void> updateFilters(Object params) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try { 
      await _channel.invokeMethod('updateFilters', params);
    } catch (e) {
      print('Error processing camera image: $e');
    }
  } 

/// @backgroundImagePath - Path to the image on the platform specific file system 
/// 
/// This should be the same width and height and aspect ratio as the current camera frames
/// The iOS plugin will scale and crop incorrectly sized images to fit
  Future<void> setBackgroundImagePath(String backgroundImagePath) async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try {
        final params = {'img': backgroundImagePath};
        await _channel.invokeMethod('setBackgroundImagePath', params);
    } catch (e) {
      print('Error processing camera image: $e');
    }
  }
   
 /// Start filtering images
  Future<void> enableFilters() async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try { 
        await _channel.invokeMethod('enableFilters', {});
    } catch (e) {
      print('Error enabling filters: $e');
    }
  }

   /// Stop filtering images
  Future<void> disableFilters() async {
    if (!_initialized) {
      throw Exception('FilterController not initialized');
    }

    try { 
        await _channel.invokeMethod('disableFilters', {});
    } catch (e) {
      print('Error enabling filters: $e');
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