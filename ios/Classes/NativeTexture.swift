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
    private var latestPixelBuffer: CVPixelBuffer?
    private let pixelBufferQueue = DispatchQueue(label: "com.aardman.pixelBufferQueue")
    
    init(registry: FlutterTextureRegistry, width: Int, height: Int) {
        self.width = width
        self.height = height
        super.init()
        self.textureId = registry.register(self)
    }
    
    // Updating latestPixelBuffer is in a sync block to lock it for exclusive access
    func updatePixelBuffer(with newPixelBuffer: CVPixelBuffer) {
        pixelBufferQueue.sync {
            self.latestPixelBuffer = newPixelBuffer
        }
    }
    
    // This is called by Flutter to acquire data to present in the Flutter Texture widget
    func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
        if let latestPixelBuffer {
            return Unmanaged.passRetained(latestPixelBuffer)
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
    
    // This is called to let Flutter know when to call copyPixelBuffer
    func textureFrameAvailable(registry: FlutterTextureRegistry){
        if let textureId {
            registry.textureFrameAvailable(textureId)
        }
    }
    
}
