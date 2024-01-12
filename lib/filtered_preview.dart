import 'package:flutter/widgets.dart';
import 'filtered_preview_controller.dart';


class FilteredPreview extends StatelessWidget {
  const FilteredPreview(this.controller, {Key? key}) : super(key: key);
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
 