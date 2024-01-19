import 'dart:async';  
import 'dart:developer';
import 'dart:io';
import 'package:camera/camera.dart';
import 'package:animatorfilter/filtered_preview.dart';
import 'package:flutter/material.dart';
import 'package:animatorfilter/filtered_preview_controller.dart';
import 'package:image/image.dart' as img;

import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'dart:isolate';
import 'package:flutter/foundation.dart';
import 'package:tuple/tuple.dart';

// This function is a top-level function and should remain outside of any class.
img.Image decodeImageFn(Uint8List bytes) {
  return img.decodeImage(bytes)!;
}

img.Image copyResizeFn(Tuple3<img.Image, int, int> params) {
  return img.copyResize(params.item1, height: params.item2, width: params.item3);
}

class PreviewPage  extends StatefulWidget  {
  const PreviewPage({Key? key}) : super(key: key);

  @override
  State<PreviewPage> createState() => _PreviewPageState();

}

class _PreviewPageState  extends State<PreviewPage> {
  
  CameraController? _camController;
  int _camFrameRotation = 0;
  double _camFrameToScreenScale = 0;
  int _lastRun = 0;

  double _radius = 0 ;
  FilteredPreviewController?  _controller;

  //Desired Preview Size 
  //TODO (2)
  double _textureWidth  = -1;
  double _textureHeight = -1;

   @override
   void initState(){
    super.initState();
    init();
   }

   @override
   dispose() async {
    super.dispose();
    await _controller?.dispose();
   }
 
   init() async {   
    await initCamera(); 
    _textureHeight = _camController!.value.previewSize!.height;
    _textureWidth  = _camController!.value.previewSize!.width;
    await initPreviewController(_textureWidth, _textureHeight);
    await startImageStream();

    ///TODO: Static image setup for demo
     await setImages();
   }

  //Demo image setup
  Future<void> setImages() async {
    try {

      //Load images from files
      //var backgroundAsset = await AssetImage("assets/backgrounds/bkgd_01.jpg");
      img.Image? background = await assetImageToImage("assets/backgrounds/bkgd_01.jpg");

      if (background !=  null) { 
        var croppedBackground  = await resizeAndCropImage(background, 1280, 720);

        if (croppedBackground != null) {
          await _controller?.setBackgroundImage(croppedBackground);
        }
      }

    } catch (e) {
      log("Error initializing camera, error: ${e.toString()}");
    }
  }

  //Load using rootBundle
  Future<img.Image> assetImageToImage(String assetPath) async {
    final ByteData data = await rootBundle.load(assetPath);
    final Uint8List bytes = data.buffer.asUint8List();

    return await compute(decodeImageFn, bytes);
  }

  //loading this  way was unsuccessful
  // Future<img.Image?> loadImage(String path) async {
  //   final file = File(path);
  //   final bytes = await file.readAsBytes();
  //   return img.decodeImage(bytes);
  // }

  Future<img.Image?> resizeAndCropImage(img.Image originalImage, int w, int h) async {

    if (originalImage == null) {
      return null;
    }

    // Calculate new width based on the aspect ratio
    int newWidth = (originalImage.width / originalImage.height > 2) ? (2 * h) : originalImage.width;

    // Resize the image so that the short dimension is h pixels
    final resizedImage = await compute(copyResizeFn, Tuple3(originalImage, h, newWidth));

    //img.copyResize(originalImage, height: h, width: newWidth);


    // Calculate the crop starting point (x-axis)
    int cropStartX = (resizedImage.width - w) ~/ 2;

    // Ensure the cropping parameters are within the image bounds
    cropStartX = cropStartX < 0 ? 0 : cropStartX;
    int cropWidth = cropStartX + w > resizedImage.width ? resizedImage.width - cropStartX : w;

    // Crop the image
    return img.copyCrop(resizedImage, x: cropStartX, y:0, width:cropWidth, height:h);
  }



//This version initialises the camera and starts the image stream 
   Future<void> initCamera() async {
    final cameras = await availableCameras();
    var idx = cameras.indexWhere((c) => c.lensDirection == CameraLensDirection.back);
    if (idx < 0) {
      log("No Back camera found - error");
      return;
    }

    var desc = cameras[idx];
    _camFrameRotation = Platform.isAndroid ? desc.sensorOrientation : 0;
    _camController = CameraController(
      desc,
      ResolutionPreset.high, // 720p
      enableAudio: false,
      imageFormatGroup: Platform.isAndroid ? ImageFormatGroup.yuv420 : ImageFormatGroup.bgra8888,
    );

    try {
      await _camController!.initialize(); 
    } catch (e) {
      log("Error initializing camera, error: ${e.toString()}");
    }

    if (mounted) {
      setState(() {});
    }
  }

   //This version initialises the camera and starts the image stream 
   Future<void> startImageStream() async { 
    try { 
      await _camController!.startImageStream((image) => _processCameraImage(image));
    } catch (e) {
      log("Error initializing camera, error: ${e.toString()}");
    } 
  }


 //Will be called on each image returned from the camera
 //Framerate 
 void _processCameraImage(CameraImage image) async {

    int Framerate = 60;

    if ( !mounted ||  DateTime.now().millisecondsSinceEpoch - _lastRun < Framerate) {
      return;
    } 
 
    await _controller?.update(image);

  
     _lastRun = DateTime.now().millisecondsSinceEpoch;
  }

   initPreviewController(double width,  double height) async {

    //Init the controller
    _controller = FilteredPreviewController();
    await _controller!.initialize(width, height);

    //update ui
    setState(() {});

   }
 

 @override
  Widget build(BuildContext context) {
    if (_controller == null) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    return Material(
      child: Container(
        color: Colors.white,
         child: Column(
          children: [
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  FilteredPreview(_controller!),
                ],
              ),
            ),
            Row(
              children: [
                const SizedBox(width: 20),
                const Text(
                  'Blur',
                  style: TextStyle(color: Colors.black, fontSize: 20),
                ),
                Expanded(
                  child: Slider(
                    value: _radius,
                    min: 0,
                    max: 20,
                    onChanged: (val) {
                      setState(() {
                        // Now we wait for an update from the camera stream
                        // _radius = val;
                        // _controller!.draw(_radius);
                      });
                    },
                  ),
                )
              ],
            )
          ],
        ),
      ),
    );
  }


}


 //This version sets a single value for the imageInfo from a file
  //  Future<void> initImageInfoFromFile() async {
  //    const imageProvider = AssetImage('assets/drawable/image.jpg');
  //    var stream = imageProvider.resolve(ImageConfiguration.empty);
      
  //    //init  promise  to  fulfil when image is  loaded  
  //    final Completer<ImageInfo> completer = Completer<ImageInfo>();
  //    var  listener =  ImageStreamListener((ImageInfo  info, bool _) {
  //      completer.complete(info);
  //    });
     
  //    stream.addListener(listener);
     
  //    final imageInfo  = await  completer.future; 
     
  //    await initPreviewController(imageInfo);
     
  //    stream.removeListener(listener);
  //  }