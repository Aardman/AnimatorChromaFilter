//
//  NativeTexture.swift
//  animatorfilter
//
//  Created by Paul Freeman on 01/02/2024.
//

import Foundation
import Flutter
 

class NativeTexture: NSObject, FlutterTexture {
    var textureId: Int64?
    var width: Int
    var height: Int
    var latestPixelBuffer: CVPixelBuffer?  
    private let pixelBufferQueue = DispatchQueue(label: "com.aardman.pixelBufferQueue")
 
    init(registry: FlutterTextureRegistry, width: Int, height: Int) {
        self.width = width
        self.height = height
        super.init()
        self.textureId = registry.register(self)
        //create the initial pixel buffer
        CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA, nil, &latestPixelBuffer)
    }
 
    func updatePixelBuffer(with newPixelBuffer: CVPixelBuffer) {
           pixelBufferQueue.sync {
               self.latestPixelBuffer = newPixelBuffer
           }
   }

   func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
       var pixelBuffer: CVPixelBuffer?
       pixelBufferQueue.sync {
           pixelBuffer = self.latestPixelBuffer
           self.latestPixelBuffer = nil
       }
       if let pixelBuffer {
           return Unmanaged.passRetained(pixelBuffer)
       }
       else {
           return nil
       }
   }

    // Call this method to unregister the texture when it's no longer needed
    func unregisterTexture(registry: FlutterTextureRegistry) {
        if let textureId = textureId {
            registry.unregisterTexture(textureId)
        }
    }
    
    func textureFrameAvailable(registry: FlutterTextureRegistry){
        if let textureId {
            registry.textureFrameAvailable(textureId)
        }
    }
    
}
