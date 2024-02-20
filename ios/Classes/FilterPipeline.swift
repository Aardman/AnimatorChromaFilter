//
//  CIPipeline.swift
//  CoreImageGreenScreen
//
//  Created by Paul Freeman on 02/03/2022.
//

import UIKit
import CoreImage
import AVFoundation
import Flutter
import VideoToolbox

@objc
public class FilterPipeline : NSObject {
    
    //Resources
    var ciContext : CIContext!
    var flutterTextureRegistry: FlutterTextureRegistry?
    var nativeTexture:NativeTexture?
    
    //Filters
    //This could simply be a Dictionary, but then we'd need to convert any structured values
    //whenever using them, this way we only perform such structure transforms in the constructor
    //of FilterParameters
    @objc public var filterParameters:FilterParameters? {
        willSet {
            //update the pipeline with whichever values are provided
            updateChangedFilters(newValue)
            //copy any new values
            if newValue?.backgroundImage == nil { newValue?.backgroundImage = filterParameters?.backgroundImage }
            if newValue?.maskColor       == nil { newValue?.maskColor = filterParameters?.maskColor ?? FilterConstants.defaultColour}
            if newValue?.threshold       == nil { newValue?.threshold = filterParameters?.threshold ?? FilterConstants.defaultThreshold }
            if newValue?.maskBounds      == nil { newValue?.maskBounds = filterParameters?.maskBounds }
        }
    }
    
    var backgroundCIImage:CIImage?
    var scaledBackgroundCIImage:CIImage?
    var chromaFilter:BlendingChromaFilter?
    
    public var filtersEnabled = false
    
    //MARK: - Initialise pipeline

    public override init(){
        super.init()
    }
    
    public init(filterParameters:FilterParameters, flutterTextureRegistry:FlutterTextureRegistry){
        super.init()
        setupCoreImage()
        self.filterParameters = filterParameters
        self.flutterTextureRegistry = flutterTextureRegistry
        updateChangedFilters(filterParameters)
    }
    
    public func initialize(filterParameters:FilterParameters){
        setupCoreImage()
        self.filterParameters = filterParameters
        updateChangedFilters(filterParameters)
    }
    
    ///The default hw device will be selected, currently a MTLDevice, no need to explicitly add
    ///using using the alternative constructor for CIContext
    func setupCoreImage(){
        ciContext = CIContext()
    }
    
    //MARK:  - Filter init and update
    
    /// For any non-nil data in the new set of parameters, update the
    /// filter parameters which may involve re-creating one or more CIFilters
    func updateChangedFilters(_ newValue:FilterParameters?){
        
        guard let newParams = newValue else { return }
        
        if let backgroundFilename = newParams.backgroundImage {
            updateBackground(backgroundFilename)
        }
        
        if let maskBounds = newParams.maskBounds {
            updateMaskBounds(maskBounds)
        }
        
        //gather changed and/or current values for colour and threshold
        filterParameters?.maskColor = newParams.maskColor
        filterParameters?.threshold = newParams.threshold
        filterParameters?.smoothing = newParams.smoothing
         
        /// gather changes to colour and threshold
        updateCustomChromaFilter(
            filterParameters?.maskColor,
            filterParameters?.threshold,
            filterParameters?.smoothing
        )
    }
    
    func  updateBackground(_ path: String){
        if let backgroundImage = UIImage(contentsOfFile:  path) {
            backgroundCIImage = CIImage(image: backgroundImage)
        }
    }
    
    func updateCustomChromaFilter(_ colour: (Float, Float, Float)?, _ threshold:Float?, _ smoothing:Float?){
        if self.chromaFilter == nil {
            chromaFilter = BlendingChromaFilter()
        }
        if let colour {
            self.chromaFilter?.red   = colour.0
            self.chromaFilter?.green = colour.1
            self.chromaFilter?.blue  = colour.2
        }
        if let threshold {
            self.chromaFilter?.threshold = threshold
        }
        if let smoothing {
            self.chromaFilter?.smoothing = smoothing
        }
        ///needed so that kernel data can be found  when  loaded
        BlendingChromaFilter.myBundle = Bundle(for: type(of: self))
    }
    
    func updateMaskBounds(_ bounds:MaskBounds){
#if DEBUG
        print("🍎 Mask Bounds Updated \(bounds)")
#endif
    }
    
    //MARK: - API for processing from input raw image data from Flutter plugin, BGRA8888 format
     
    func update(_ rawBytes: Data, texture:NativeTexture) {
        if let outputImage = getProcessedImage(from: rawBytes, texture: texture) {
            let pixelBuffer = getPixelBuffer(outputImage)
            if let pixelBuffer,
               let flutterTextureRegistry {
                nativeTexture?.updatePixelBuffer(with: pixelBuffer)
                nativeTexture?.textureFrameAvailable(registry: flutterTextureRegistry)
            }
        }
    }
    
    func processStillFrame(_ rawBytes: Data, texture:NativeTexture) -> Data? {
        if let outputImage = getProcessedImage(from: rawBytes, texture: texture) {
            let processedBytes:Data? = CIImage.getAsJPEGData(outputImage)
            return processedBytes
        }
        else {
            return nil
        }
    }
    
    func getProcessedImage(from rawBytes:Data, texture:NativeTexture)  -> CIImage? {
        /// convert raw data to CoreImage CIImage
        guard let ciImage = convertToCIImage(with: rawBytes, width: texture.width, height: texture.height) else { print("❌ failed to convert input image"); return nil}
        
        var outputImage:CIImage?
        
        // apply filtering
        if (filtersEnabled){
            if let backgroundCIImage {
                scaledBackgroundCIImage = transformBackgroundToFit(backgroundCIImage: backgroundCIImage, cameraImage: ciImage)
            }
            outputImage = applyFilters(inputImage: ciImage)
        }
        else {
            outputImage = ciImage
        }
        
        return outputImage
    }
    
    func getPixelBuffer(_ ciImage:CIImage) -> CVPixelBuffer? {
        var buffer: CVPixelBuffer?
        if let width = self.nativeTexture?.width,
           let height = self.nativeTexture?.height {
            let properties: [String: Any] = [
                kCVPixelBufferMetalCompatibilityKey as String: true,
                kCVPixelBufferOpenGLCompatibilityKey as String: true
            ]
            CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA, properties as CFDictionary, &buffer)
            if let buffer {
                ciContext.render(ciImage, to: buffer)
            }
        }
        return buffer
    }
    
    func filter(_  ciImage: CIImage) -> CIImage? {
        return ciImage
    }
    
    func convertToCIImage(with rawData: Data, width: Int, height: Int) -> CIImage? {
        /// BGRA8888 with no transparency so need to skip alpha channel.
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        
        return rawData.withUnsafeBytes { rawBufferPointer -> CIImage? in
            guard let pointer = rawBufferPointer.baseAddress else { return nil }
            guard let dataProvider = CGDataProvider(dataInfo: nil, data: pointer, size: rawData.count, releaseData: {_,_,_ in }),
                  let cgImage = CGImage(width: width,
                                        height: height,
                                        bitsPerComponent: 8,
                                        bitsPerPixel: 32,
                                        bytesPerRow: width * 4,
                                        space: colorSpace,
                                        bitmapInfo: bitmapInfo,
                                        provider: dataProvider,
                                        decode: nil,
                                        shouldInterpolate: true,
                                        intent: .defaultIntent) else { return nil }
            
            return CIImage(cgImage: cgImage)
        }
    }
    
    public func setBackgroundImageFrom(path:String){
        updateBackground(path)
    }
    
    //MARK: - Apply filtering
    
    /// Filters and transforms for the input image which must be correctly rotated
    /// prior to application of filters
    func applyFilters(inputImage camImage: CIImage) -> CIImage? {
        
        if scaledBackgroundCIImage == nil {
            createSolidColourBackground()
        }
        
        guard let chromaFilter = self.chromaFilter else { return camImage }
        
        /// ChromaFilter is a BlendingChromaFilter instance
        chromaFilter.cameraImage = camImage
        chromaFilter.backgroundImage = scaledBackgroundCIImage
        
        /// Apply and composite with the background image
        guard let chromaBlendedImage = chromaFilter.outputImage else { return camImage }
        
        return chromaBlendedImage
    }
    
    fileprivate func createSolidColourBackground() {
        /// Test background when there is no background image available
        let colourGen = CIFilter(name: "CIConstantColorGenerator")
        colourGen?.setValue(CIColor(red: 1.0, green: 0.0, blue: 0.0), forKey: "inputColor")
        scaledBackgroundCIImage = colourGen?.outputImage
    }
    
    //MARK: - Background formatting
    
    /// Camera image is a correctly oriented CI image from the camera, ie: if an AVPhotoResponse
    /// it has already been rotated to align with the input background
    func transformBackgroundToFit(backgroundCIImage:CIImage, cameraImage:CIImage) -> CIImage?  {
        let scaledImage = scaleImage(fromImage: backgroundCIImage, into: cameraImage.getSize())
        let translatedImage = translateImage(fromImage:scaledImage, centeredBy:cameraImage.getSize())
        let croppedImage = cropImage(ciImage: translatedImage, to: cameraImage.getSize())
        return croppedImage
    }
    
    //MARK: Scale background
    
    /// the into image is only provided for calculating the  desired size of the scaled output
    func scaleImage(fromImage:CIImage, into targetDimensions:CGSize) -> CIImage? {
        let sourceDimensions = fromImage.getSize()
        let scale = calculateScale(input: sourceDimensions, toFitWithinHeightOf: targetDimensions)
        guard let scaleFilter = CIFilter(name: "CILanczosScaleTransform") else { return nil }
        scaleFilter.setValue(fromImage, forKey: kCIInputImageKey)
        scaleFilter.setValue(scale,   forKey: kCIInputScaleKey)
        scaleFilter.setValue(1.0,     forKey: kCIInputAspectRatioKey)
        return scaleFilter.outputImage
    }
    
    /// Scale to fit the height
    func calculateScale(input: CGSize, toFitWithinHeightOf: CGSize) -> CGFloat {
        return  toFitWithinHeightOf.height / input.height
    }
    
    //MARK: Translate background
    
    /// Return the CGRect that is a window into the target size from the center
    func translateImage(fromImage:CIImage?, centeredBy targetSize:CGSize) -> CIImage? {
        guard let inputImage = fromImage else  { return nil }
        let offset = (inputImage.getSize().width - targetSize.width)/2
        let transform = CGAffineTransform(translationX: -offset, y: 0.0)
        return inputImage.transformed(by: transform)
    }
    
    //MARK: Crop background
    
    /// Crop out from the center of the provided CIImage
    /// Background must be translated first
    func cropImage(ciImage: CIImage?, to targetSize:CGSize) -> CIImage? {
        guard let image = ciImage else { return ciImage }
        let imageDimensions = image.getSize()
        if imageDimensions.width <= targetSize.width {
            return ciImage
        }
        else {
            //calculate window
            let windowRect = CGRect(origin: CGPoint.zero, size: targetSize)
            //add crop
            return ciImage?.cropped(to: windowRect)
        }
    }
    
}

//MARK: - Helper extensions

extension CIImage {
    
    func getSize() -> CGSize {
        return CGSize(width: extent.width, height:extent.height)
    }
    
    static func getAsJPEGData(_ image: CIImage) -> Data? {
        let context = CIContext(options: nil)
        guard let cgImage = context.createCGImage(image, from: image.extent) else {
            return nil
        }
        let uiImage = UIImage(cgImage: cgImage)
        return uiImage.jpegData(compressionQuality: 1.0)
    }
    
}

extension FileManager {
    
    func applicationDocumentsDirectory () -> String {
        let resultArray = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.documentDirectory ,FileManager.SearchPathDomainMask.userDomainMask, true)
        let root = resultArray[0]
        return "\(root)"
    }
    
    func cachesDirectory () -> String {
        let resultArray = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.cachesDirectory ,FileManager.SearchPathDomainMask.userDomainMask, true)
        let root = resultArray[0]
        return "\(root)"
    }
    
    func save(filename:String, image: UIImage){
        let path = "\(applicationDocumentsDirectory())/\(filename)"
        let fileUrl = URL(fileURLWithPath: path)
        var sourceData:Data? = image.jpegData(compressionQuality: 1.0)
        do {
            try sourceData?.write(to: fileUrl)
        }
        catch {
            print("Failed to write \(path)")
            sourceData = nil
        }
        sourceData = nil
    }
    
}

