import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'animatorfilter_method_channel.dart';

abstract class AnimatorfilterPlatform extends PlatformInterface {
  /// Constructs a AnimatorfilterPlatform.
  AnimatorfilterPlatform() : super(token: _token);

  static final Object _token = Object();

  static AnimatorfilterPlatform _instance = MethodChannelAnimatorfilter();

  /// The default instance of [AnimatorfilterPlatform] to use.
  ///
  /// Defaults to [MethodChannelAnimatorfilter].
  static AnimatorfilterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AnimatorfilterPlatform] when
  /// they register themselves.
  static set instance(AnimatorfilterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
