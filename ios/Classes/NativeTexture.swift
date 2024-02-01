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
    var pixelBuffer: CVPixelBuffer?

    init(registry: FlutterTextureRegistry, width: Int, height: Int) {
        self.width = width
        self.height = height
        super.init()
        self.textureId = registry.register(self)
    }

    func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
        if let buffer = pixelBuffer {
            return Unmanaged.passRetained(buffer)
        }
        return nil
    }

    // Call this method to unregister the texture when it's no longer needed
    func unregisterTexture(registry: FlutterTextureRegistry) {
        if let textureId = textureId {
            registry.unregisterTexture(textureId)
        }
    }
}
