import 'package:flutter_test/flutter_test.dart';
import 'package:animatorfilter/animatorfilter.dart';
import 'package:animatorfilter/animatorfilter_platform_interface.dart';
import 'package:animatorfilter/animatorfilter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockAnimatorfilterPlatform
    with MockPlatformInterfaceMixin
    implements AnimatorfilterPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final AnimatorfilterPlatform initialPlatform = AnimatorfilterPlatform.instance;

  test('$MethodChannelAnimatorfilter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelAnimatorfilter>());
  });

  test('getPlatformVersion', () async {
    Animatorfilter animatorfilterPlugin = Animatorfilter();
    MockAnimatorfilterPlatform fakePlatform = MockAnimatorfilterPlatform();
    AnimatorfilterPlatform.instance = fakePlatform;

    expect(await animatorfilterPlugin.getPlatformVersion(), '42');
  });
}
