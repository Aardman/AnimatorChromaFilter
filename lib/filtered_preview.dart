import 'package:flutter/widgets.dart';
import 'filtered_preview_controller.dart'; 
import 'package:flutter/foundation.dart';  
import 'package:flutter/material.dart';


class FilteredPreviewAndroid extends StatelessWidget {
  const FilteredPreviewAndroid(this.controller, {Key? key}) : super(key: key);
  final FilteredPreviewController controller; 

  @override
  Widget build(BuildContext context){
    if(!controller.initialized){
      return  Container();
    }

    return AspectRatio (
      aspectRatio: controller.width / controller.height,
      //The texture id returned from the native code used to draw the preview texture
      child: Texture(
        textureId: controller.textureId
      ),
    );
  }
}

class FilteredPreviewIOS extends StatelessWidget {
  const FilteredPreviewIOS(this.controller, {Key? key}) : super(key: key);
  final FilteredPreviewController controller; 

  @override
  Widget build(BuildContext context){
    if(!controller.initialized){
      return  Container();
    }

    return AspectRatio (
      aspectRatio: controller.width / controller.height,
      //The texture id returned from the native code used to draw the preview texture
      child:
        controller.imageBytes.lengthInBytes == 0
        ? const CircularProgressIndicator()
        : ProcessedImageWidget(
            imageData: controller.imageBytes
        )
    );
  }
} 

 class ProcessedImageWidget extends StatefulWidget {
  final Uint8List imageData;

  const ProcessedImageWidget({Key? key, required this.imageData}) : super(key: key);

  @override
  _ProcessedImageWidgetState createState() => _ProcessedImageWidgetState();
}

class _ProcessedImageWidgetState extends State<ProcessedImageWidget> {
  @override
  Widget build(BuildContext context) {
    return Image.memory(widget.imageData);
  }

  @override
  void didUpdateWidget(ProcessedImageWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.imageData != widget.imageData) {
      setState(() {});
    }
  }
}