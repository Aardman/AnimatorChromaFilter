import 'package:flutter/widgets.dart';
import 'filtered_preview_controller.dart';  
import 'package:flutter/material.dart';


class FilteredPreview extends StatelessWidget {
  const FilteredPreview(this.controller, {super.key});
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