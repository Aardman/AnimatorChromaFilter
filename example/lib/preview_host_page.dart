import 'dart:async';  
import 'dart:developer';
import 'dart:io';
import 'package:camera/camera.dart';
import 'package:animatorfilter/filtered_preview.dart';
import 'package:flutter/material.dart';
import 'package:animatorfilter/filtered_preview_controller.dart'; 
import 'package:animatorfilter/filtered_preview_controller_ios.dart';
import 'package:animatorfilter/filtered_preview_controller_android.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';


///
/// This is an example hosting page for the FilteredPreview widget 
/// and should be replaced by the application client hosting widget
///
class PreviewPage  extends StatefulWidget  {
  const PreviewPage({Key? key}) : super(key: key);

  @override
  State<PreviewPage> createState() => _PreviewPageState();

}

class _PreviewPageState  extends State<PreviewPage> {

  int _camFrameRotation = 0; 

  CameraController? _camController; 
  FilteredPreviewController? _controller;

  //Desired Preview Size  
  double _textureWidth = -1;
  double _textureHeight = -1;

  @override
  void initState() {
    super.initState();
    init();
  }

  @override
  dispose() async {
    super.dispose();
    await _controller?.dispose();
  } 

  //TODO: Make this more robust for Android, check deviceOrientation names 
  setTextureSize(){   
    if( _camController!.value.deviceOrientation.name == "portraitUp" || _camController!.value.deviceOrientation.name == "portraitDown"){ 
      _textureHeight =  _camController!.value.previewSize!.flipped.height  ;
      _textureWidth  =  _camController!.value.previewSize!.flipped.width   ; 
    }
    else {
      _textureHeight =  _camController!.value.previewSize!.height  ;
      _textureWidth  =  _camController!.value.previewSize!.width   ; 
    }
  }

  init() async {
    await initCamera(); 
    setTextureSize();
    await initPreviewController(_textureWidth, _textureHeight);
    await startImageStream();
  }

  //Demo image setup
  Future<void> setImages() async {
    try {
      File backgroundFile = await getImageFileFromAssets(
          "assets/backgrounds/bkgd_01.jpg");
      String? fullPath = backgroundFile.path; 
      //await _controller?.setBackgroundImagePath(fullPath);

    } catch (e) {
      log("🍎 Error setting background image, error: ${e.toString()}");
    }
  }

  Future<File> getImageFileFromAssets(String path) async {
    final byteData = await rootBundle.load('$path');
    final buffer = byteData.buffer;
    Directory tempDir = await getTemporaryDirectory();
    String tempPath = tempDir.path;
    var filePath = tempPath + '/tempfile.jpg';
    return File(filePath).writeAsBytes(
        buffer.asUint8List(byteData.offsetInBytes, byteData.lengthInBytes));
  }

  //Example method, depends on hosting app to supply these values
  Future<void> setFilterParameters() async {
    // var colours = sampleToggle ? [0.0, 255.0, 0.0] : [0.0, 0.0, 255.0];
    //
    // var sensitivity = _backgroundSensitivity;
    // // sensitivity = 0.7;
    // print("_backgroundSensitivity " + sensitivity.toString());
    //
    // var data = {
    //   "backgroundPath": fullPath,
    //   "colour": colours,
    //   "sensitivity": sensitivity,
    // };

    // await _controller?.updateFilters(data);
  }

//This version initialises the camera and starts the image stream 
  Future<void> initCamera() async {
    final cameras = await availableCameras();
    var idx = cameras.indexWhere((c) =>
    c.lensDirection == CameraLensDirection.back);
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
      imageFormatGroup: Platform.isAndroid
          ? ImageFormatGroup.yuv420
          : ImageFormatGroup.bgra8888,
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
      await _camController!.startImageStream((image) =>
          _processCameraImage(image));
    } catch (e) {
      log("Error initializing camera, error: ${e.toString()}");
    }
  } 

  //Will be called on each image returned from the camera
  //Framerate
  void _processCameraImage(CameraImage image) async { 
    await _controller?.update(image); 
  }

  initPreviewController(double width, double height) async {
    //Init the controller each conforms to the FilteredPreviewController interface
    if(Platform.isIOS){ 
     _controller = FilteredPreviewControllerIOS();
    }
    else {
      _controller = FilteredPreviewControllerAndroid();
    }
    await _controller!.initialize(width, height);

    //update ui
    setState(() {});
  }


  @override
  Widget build(BuildContext context) {

    var screenSize = MediaQuery.of(context).size;

    // Calculate the size for the SizedBox while maintaining the aspect ratio
    double aspectRatio = 1280 / 720;
    double width = screenSize.width * 0.9; // 90% of screen width
    double height = width / aspectRatio;

    // Ensure the height doesn't exceed the screen height
    if (height > screenSize.height) {
      height = screenSize.height * 0.9; // 90% of screen height
      width = height * aspectRatio;
    }

    if (_controller == null) {
      // Display a progress indicator when _controller is null
      return const Center(child: CircularProgressIndicator());
    } else {

      return Container(
        width: screenSize.width,
        height: screenSize.height,
        color: Colors.green, // Replace with your desired color
        child: Center(
          child: _controller == null
              ? const CircularProgressIndicator()
              : SizedBox(
            width: screenSize.width,
            height: screenSize.height,
            child:  FilteredPreview(_controller!),
          ),
        ),
      );
    }
  }

}