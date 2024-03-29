#include <metal_stdlib>
using namespace metal;

#include <CoreImage/CoreImage.h>

/// See  Shaders/README for compilation instructions, this was used to compile ChromaBlendShader.metallib in Resources
/// Compilation is performed pre-build in the terminal
/// This code came from Apples documentation for CoreImage
extern "C" {
  namespace coreimage {
        
    /// For a full chroma effect use mix() to interpolate colours including  the threshold function.
    float4 blendingChromaKernel(sample_t s, sample_t bg, float r, float g  , float b  , float threshold , float smoothing ){
       
      const float4 inputColor = s.rgba;
      const float4 blendColor = bg.rgba;
      const float3 maskColor =  float3(r, g, b);
      
      const float3 YVector = float3(0.2989, 0.5866, 0.1145);
      
      const float maskY = dot(maskColor, YVector);
      const float maskCr = 0.7131 * (maskColor.r - maskY);
      const float maskCb = 0.5647 * (maskColor.b - maskY);
      
      const float Y = dot(inputColor.rgb, YVector);
      const float Cr = 0.7131 * (inputColor.r - Y);
      const float Cb = 0.5647 * (inputColor.b - Y);
      
      const float blendValue = 1.0 - smoothstep(threshold, threshold + smoothing, distance(float2(Cr, Cb), float2(maskCr, maskCb)));
      
      const float4 outputColor = mix(inputColor, blendColor, blendValue);
 
      return outputColor;
      
    }
  }
}


