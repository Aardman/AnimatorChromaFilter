//
//  FilterConstants.swift
//  camera
//
//  Created by Paul Freeman on 14/04/2022.
//

import Foundation

public enum FilterConstants  {
    public static let defaultOrientationPortraitUp:UInt32 = 6
    public static let defaultThreshold:Float = 0.3
    public static let defaultColour:(Float,Float,Float) = (0.0, 1.0, 0.0)
    public static let defaultSmoothing:Float = 0.1
}

enum ParamNames: String {
    
    /// image related names
    case imgPath
    case imageData
    case width
    case height
    
    /// chroma filter specific names
    case img
    case colour
    case sensitivity
    case hueRange
    case smoothing
    case polygon
    
}
