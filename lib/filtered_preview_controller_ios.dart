import 'package:flutter/services.dart';
import 'package:camera/camera.dart';    
import 'dart:async';  
import 'package:animatorfilter/filtered_preview_controller.dart';     
    

class FilteredPreviewControllerIOS extends FilteredPreviewController {
   
  FilteredPreviewControllerIOS(); 
 
  @override
  Future<void> update(CameraImage cameraImage) async {
    if (!initialized) {
      throw Exception('FilterController not initialized');
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
  
        await FilteredPreviewController.channel.invokeMapMethod<String, dynamic>('update', params);  

      } catch (e) {
        print('Error processing camera image: $e');
      } 

  }  

   //TODO: If required by application
   Future<Uint8List> processStillFrame(CameraImage cameraImage) async {  
    return Future.value(Uint8List(0));
   }  
    
}