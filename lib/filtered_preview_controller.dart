import 'package:flutter/services.dart'; 
import 'package:camera/camera.dart';
import 'ImageConverter.dart';

const MethodChannel _channel =  MethodChannel('animatorfilter');

class FilteredPreviewController {
    FilteredPreviewController();

    int _textureId = -1;
    int _width = 0;
    int _height = 0;
    bool _isDisposed = false;
    bool _initialized = false;

//Getters 
 bool get initialized {
    return _initialized;
  }

  int get textureId {
    return _textureId;
  }

  int get width {
    return _width;
  }

  int get height {
    return _height;
  }

//Lifecycle
 
 Future<void> initialize(int width, int height) async {
    if (_isDisposed) {
      throw Exception('Disposed FilterPreviewController');
    }

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

    Uint8List formattedImage = formatImage(cameraImage);

    // val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // bmp.copyPixelsFromBuffer(ByteBuffer.wrap(srcImage))

    // Call the filter update method on the native platform
    //TODO Add the input data to the params
    final params = {'image': formattedImage};
    await _channel.invokeMethod('update', params);
  }   
 
  //TODO process CameraImage into correctly formatted argb byte array for passing to Plugin
  Uint8List formatImage(CameraImage cameraImage) {
    Uint8List bytes = ImageConverter.convertCameraImageToArgb(cameraImage);
    return bytes;
  } 

}