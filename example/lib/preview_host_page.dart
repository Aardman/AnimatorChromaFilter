import 'dart:async';  
import 'dart:developer';
import 'dart:io';
import 'package:camera/camera.dart';
import 'package:animatorfilter/filtered_preview.dart';
import 'package:flutter/material.dart';
import 'package:animatorfilter/filtered_preview_controller.dart'; 
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

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

  double _radius = 0;

  FilteredPreviewController? _controller;

  //Desired Preview Size 
  //TODO (2)
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

  init() async {
    await initCamera();
    _textureHeight = _camController!.value.previewSize!.height;
    _textureWidth = _camController!.value.previewSize!.width;
    await initPreviewController(_textureWidth, _textureHeight);
    await startImageStream();

    ///TODO: Static image setup for demo
    await setImages();
  }

  //Demo image setup
  Future<void> setImages() async {
    try {
      File backgroundFile = await getImageFileFromAssets(
          "assets/backgrounds/bkgd_01.jpg");
      String? fullPath = backgroundFile.path;
      if (fullPath != null) {
        await _controller?.setBackgroundImagePath(fullPath);
      }
    } catch (e) {
      log("Error setting background image, error: ${e.toString()}");
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
    int Framerate = 60;

    if (!mounted || DateTime
        .now()
        .millisecondsSinceEpoch - _lastRun < Framerate) {
      return;
    }

    await _controller?.update(image);


    _lastRun = DateTime
        .now()
        .millisecondsSinceEpoch;
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
            width: width,
            height: height,
            child:  Platform.isAndroid ? FilteredPreviewAndroid(_controller!) : FilteredPreviewIOS(_controller!) ,
          ),
        ),
      );
    }
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