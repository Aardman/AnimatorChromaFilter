#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint animatorfilter.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'animatorfilter'
  s.version          = '0.0.6'
  s.summary          = 'Flutter filter processor.'
  s.description      = <<-DESC
     A Flutter plugin to add Chromakeying to a Flutter app
                         DESC
  s.homepage         = 'http://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Aardman animations' => 'email@example.com' }
  s.source           = { :http => 'https://github.com/Aardman/AnimatorChromaFilter' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.resources = 'Resources/ChromaBlendShader.metallib'
 
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'VALID_ARCHS[sdk=iphonesimulator*]' => 'x86_64' }
  s.platform = :ios, '11.0'
 
end
