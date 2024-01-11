import 'dart:async';
import 'dart:ffi';
import 'dart:ui';
import 'dart:developer';
import 'dart:io';
import 'package:camera/camera.dart';
import 'package:animatorfilter/filtered_preview.dart';
import 'package:flutter/material.dart';
import 'package:animatorfilter/filtered_preview_controller.dart';


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