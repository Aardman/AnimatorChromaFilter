import 'dart:async';
import 'dart:ui';
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
   double _radius = 0 ;
   FilteredPreviewController?  _controller;

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

   //static image loading 
   init() async {

    //To replace
    // await initImageInfoFromFile();
    await initImageInfoFromFile();

   }
 
 

   //This version sets a single value for the imageInfo from a file
   Future<void> initImageInfoFromFile() async {
     const imageProvider = AssetImage('assets/drawable/image.jpg');
     var stream = imageProvider.resolve(ImageConfiguration.empty);
      
     //init  promise  to  fulfil when image is  loaded  
     final Completer<ImageInfo> completer = Completer<ImageInfo>();
     var  listener =  ImageStreamListener((ImageInfo  info, bool _) {
       completer.complete(info);
     });
     
     stream.addListener(listener);
     
     final imageInfo  = await  completer.future; 
     
     await initPreviewController(imageInfo);
     
     stream.removeListener(listener);
   }
 

   initPreviewController(ImageInfo  info) async {

    final  rgba = await info.image.toByteData(format:  ImageByteFormat.rawRgba);
    
    //Init the controller
    _controller = FilteredPreviewController();
    await _controller!.initialize(rgba!, info.image.width, info.image.height);

    await _controller!.draw(_radius);

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
                        _radius = val;
                        _controller!.draw(_radius);
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