import 'package:flutter/services.dart';
import 'package:camera/camera.dart';    
import 'dart:async';  
import 'package:animatorfilter/filtered_preview_controller.dart';   


///Implementations of platform specific initialisation and image update
class FilteredPreviewControllerAndroid extends FilteredPreviewController {
   
    FilteredPreviewControllerAndroid(); 
  
  @override
  Future<void> update(CameraImage cameraImage) async {
    if (!initialized) {
      throw Exception('FilterController not initialized');
    }
 
    try {
    
      /// NB: The U and V planes have a rowStride the width of the Y plane and  a pixel stride of 2
      ///The chrominance data needs to be sampled correctly from this to create textures for image conversion
      
      Uint8List yBytes = cameraImage.planes[0].bytes; // Y plane
      Uint8List uBytes = cameraImage.planes[1].bytes; // U plane
      Uint8List vBytes = cameraImage.planes[2].bytes; // V plane

      /// TODO: Android
      // int rowStride    = cameraImage.planes[1].bytesPerRow;   // = 1280
      // int? pixelStride = cameraImage.planes[1].bytesPerPixel; //  = 2
  
      // Call the filter update method on the native platform
      final params = {
        'Y': yBytes,
        'U': uBytes,
        'V': vBytes,
        'width': width,
        'height': height
      };
 
      await FilteredPreviewController.channel.invokeMethod('update', params);

    } catch (e) {
         print('Error processing camera image: $e');
    }
  }

  //TODO: If required by application
  @override
   Future<Uint8List> processStillFrame(CameraImage cameraImage) {
    return Future.value(Uint8List(0));
   } 

} 

