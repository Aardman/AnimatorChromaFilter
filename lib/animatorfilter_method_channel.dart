import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'animatorfilter_platform_interface.dart';

/// An implementation of [AnimatorfilterPlatform] that uses method channels.
class MethodChannelAnimatorfilter extends AnimatorfilterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('animatorfilter');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
