//
//  FilterConstants.swift
//  camera
//
//  Created by Paul Freeman on 14/04/2022.
//

import Foundation

enum FilterConstants  {
   public static let defaultOrientationPortraitUp:UInt32 = 6
   public static let defaultThreshold:Float = 0.4
   public static let defaultColour:(Float,Float,Float) = (0.0, 1.0, 0.0)
   public static let defaultSmoothing:Float = 0.3
}

enum ParamNames: String {
    
    //images
    case imgPath
    case imageData
    case width
    case height
    
    //chroma filter
    case backgroundPath
    case colour
    case sensitivity
    case hueRange
    case smoothing
    case polygon
 
}
