
import 'animatorfilter_platform_interface.dart';

class Animatorfilter {
  Future<String?> getPlatformVersion() {
    return AnimatorfilterPlatform.instance.getPlatformVersion();
  }
}
