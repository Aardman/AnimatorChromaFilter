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
            if newValue?.maskColor       == nil { newValue?.maskColor = filterParameters?.maskColor }
            if newValue?.threshold       == nil { newValue?.threshold = filterParameters?.threshold }
            if newValue?.maskBounds      == nil { newValue?.maskBounds = filterParameters?.maskBounds }
        }
    }
    
    var backgroundCIImage:CIImage?
    var scaledBackgroundCIImage:CIImage?
    var chromaFilter:BlendingChromaFilter?
    public var filtersEnabled = false
    
    
    //MARK: - Initialise pipeline
    @objc
    public override init(){
        super.init()
    }
    
    @objc
    public init(filterParameters:FilterParameters, flutterTextureRegistry:FlutterTextureRegistry){
        super.init()
        setupCoreImage()
        self.filterParameters = filterParameters
        self.flutterTextureRegistry =  flutterTextureRegistry
        //TODO: remove before release
        saveSampleBackgroundToDocs()
        //explicit init on initialisation, for default values
        updateChangedFilters(filterParameters)
    }
    
    @objc
    public func initialize(filterParameters:FilterParameters){
        setupCoreImage()
        self.filterParameters = filterParameters
        //TODO: remove before release
        saveSampleBackgroundToDocs()
        //explicit init on initialisation, for default values
        updateChangedFilters(filterParameters)
    }
    
    ///The default hw device will be selected, currently a MTLDevice, no need to explicitly add
    ///using using the alternative constructor for CIContext
    func setupCoreImage(){
        ciContext = CIContext()
    }
    
    func saveSampleBackgroundToDocs(){
        if let backgroundImage = UIImage(named: "demo_background") {
            FileManager.default.save(filename: "demo_background.jpg", image: backgroundImage)
            if let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                let fileURL = documentsDirectory.appendingPathComponent("demo_background.jpg")
                updateBackground(fileURL.path)
            }
        }
    }
    
    //MARK:  - Filter init and update
    
    ///for any non-nil data in the new set of parameters, update the
    ///filter parameters which may involve re-creating one or more CIFilters
    func updateChangedFilters(_ newValue:FilterParameters?){
        guard let newParams = newValue else { return }
        
        if let backgroundFilename = newParams.backgroundImage {
            updateBackground(backgroundFilename)
        }
        
        if let maskBounds = newParams.maskBounds {
            updateMaskBounds(maskBounds)
        }
        
        //gather changed and/or current values for colour and threshold
        var colour = filterParameters?.maskColor
        if let c  = newParams.maskColor {
            colour = c
        }
        
        var threshold = filterParameters?.threshold
        if let t = newParams.threshold {
            threshold = t
        }
        
        //gather changes to colour and threshold
        updateCustomChromaFilter(colour, threshold)
    }
    
    func  updateBackground(_ path: String){
        print("🌆 Background Updated \(path)")
        if let backgroundImage = UIImage(contentsOfFile:  path) {
            backgroundCIImage = CIImage(image: backgroundImage)
        }
    }
    
    func updateCustomChromaFilter(_ colour: (Float, Float, Float)?, _ threshold:Float?){
        if self.chromaFilter == nil {
            chromaFilter = BlendingChromaFilter()
        }
        if let colour = colour {
            self.chromaFilter?.red   = colour.0
            self.chromaFilter?.green = colour.1
            self.chromaFilter?.blue  = colour.2
        }
        if let threshold = threshold {
            self.chromaFilter?.threshold = threshold
        }
        ///needed so that kernel data can be found  when  loaded
        BlendingChromaFilter.myBundle = Bundle(for: type(of: self))
    }
    
    func updateMaskBounds(_ bounds:MaskBounds){
        print("🍎 Mask Bounds Updated \(bounds)")
    }
    
    //MARK: - New API for revised processing from input raw image data - no @objc required
    
    /**
     * Update and render  to be called on a background thread, write operations to NativeTexture.pixelbuffer to be  dispatched to the main thread
     */
    func update(_ rawBytes: Data, texture:NativeTexture) {
        self.process(rawBytes, texture: texture)
    }
    
    func process(_ rawBytes: Data, texture:NativeTexture) {
        guard let ciImage = convertToCIImage(with: rawBytes, width: texture.width, height: texture.height) else { print("❌ failed to convert input image"); return}
        
        var outputImage:CIImage?
        
        //apply filtering
        if (true){
            if let background = backgroundCIImage {
                scaledBackgroundCIImage = transformBackgroundToFit(backgroundCIImage: background, cameraImage: ciImage)
            }
             outputImage = applyFilters(inputImage: ciImage)
        }
        else {
            outputImage = ciImage
        }
         
        if let outputImage {
            let pixelBuffer = getPixelBuffer(outputImage)
            if let pixelBuffer,
               let flutterTextureRegistry {
                nativeTexture?.updatePixelBuffer(with: pixelBuffer)
                nativeTexture?.textureFrameAvailable(registry: flutterTextureRegistry)
            }
        }
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
        //BGRA8888 with no transparency so need to skip alpha channel.
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
            
            // Create and return the CIImage
            return CIImage(cgImage: cgImage)
        }
    }
    
    //Non thread critical
    public func setBackgroundImageFrom(path:String){
        updateBackground(path)
    }
    
    //MARK: - Objc API used in modified fork of Camera Plugin
    
    @objc
    //No need to lock pixel buffer currently
    public func filter(_ buffer:CVPixelBuffer?) {
        guard let buf = buffer else { return  }
        let outputImage = CIImage(cvPixelBuffer: buf, options:[:])
        if let background = backgroundCIImage {
            scaledBackgroundCIImage = transformBackgroundToFit(backgroundCIImage: background, cameraImage: outputImage)
        }
        guard let filtered = applyFilters(inputImage: outputImage) else { return }
        ciContext.render(filtered, to: buf)
    }
    
    
    @objc
    @available(iOS 11.0, *)
    //For filtering the still image
    //photo?.normalisedData() performs any input transform, eg: rotation
    public func filter(asPhoto photo: AVCapturePhoto?) -> NSData? {
        
        var orientationMetadata:UInt32 = FilterConstants.defaultOrientationPortraitUp
        if let orientationInt = photo?.metadata[String(kCGImagePropertyOrientation)] as? UInt32 {
            orientationMetadata = orientationInt
        }
        
        guard let rawPhoto =  photo?.cgImageRepresentation() else { return nil }
        let rawCIImage  = CIImage(cgImage: rawPhoto)
        let cameraImage = rawCIImage.oriented(forExifOrientation: Int32(orientationMetadata))
        
        if let background = backgroundCIImage {
            scaledBackgroundCIImage = transformBackgroundToFit(backgroundCIImage: background, cameraImage: cameraImage)
        }
        guard let filtered = applyFilters(inputImage: cameraImage),
              let colourspace = CGColorSpace(name:CGColorSpace.sRGB)
        else { return nil }
        guard
            let data = ciContext.jpegRepresentation(of: filtered, colorSpace:colourspace)
        else { return nil }
        return data as NSData?
    }
    
    
    //MARK: - Apply filtering
    
    /// Filters and transforms for the input image which must be correctly rotated
    /// prior to application of filters
    func applyFilters(inputImage camImage: CIImage) -> CIImage? {
        
        if scaledBackgroundCIImage == nil {
            //Test background when there is no background image available
            let colourGen = CIFilter(name: "CIConstantColorGenerator")
            colourGen?.setValue(CIColor(red: 1.0, green: 0.0, blue: 0.0), forKey: "inputColor")
            scaledBackgroundCIImage = colourGen?.outputImage
        }
        
        guard let chromaFilter = self.chromaFilter else { return camImage }
        
        //Chroma
        chromaFilter.cameraImage = camImage
        chromaFilter.backgroundImage = scaledBackgroundCIImage
        
        //Apply and composite with the background image
        guard let chromaBlendedImage = chromaFilter.outputImage else { return camImage }
        
        return chromaBlendedImage
    }
    
    
    //MARK: - Background formatting
    
    //Camera image is a correctly oriented CI image from the camera, ie: if an AVPhotoResponse
    //it has already been rotated to align with the input background
    func transformBackgroundToFit(backgroundCIImage:CIImage, cameraImage:CIImage) -> CIImage?  {
        let scaledImage = scaleImage(fromImage: backgroundCIImage, into: cameraImage.getSize())
        let translatedImage = translateImage(fromImage:scaledImage, centeredBy:cameraImage.getSize())
        let croppedImage = cropImage(ciImage: translatedImage, to: cameraImage.getSize())
        return croppedImage
    }
    
    //MARK: Scale background
    
    /// - into image is only provided for calculating the  desired size of the scaled output
    func scaleImage(fromImage:CIImage, into targetDimensions:CGSize) -> CIImage? {
        let sourceDimensions = fromImage.getSize()
        let scale = calculateScale(input: sourceDimensions, toFitWithinHeightOf: targetDimensions)
        guard let scaleFilter = CIFilter(name: "CILanczosScaleTransform") else { return nil }
        scaleFilter.setValue(fromImage, forKey: kCIInputImageKey)
        scaleFilter.setValue(scale,   forKey: kCIInputScaleKey)
        scaleFilter.setValue(1.0,     forKey: kCIInputAspectRatioKey)
        return scaleFilter.outputImage
    }
    
    
    //Scale to fit the height
    //We will allow the background to misalign with the center at this point. We may need
    //a CIAffineTransform step for that if width input <> output.
    func calculateScale(input: CGSize, toFitWithinHeightOf: CGSize) -> CGFloat {
        return  toFitWithinHeightOf.height / input.height
    }
    
    //MARK: Translate background
    
    //Return the CGRect that is a window into the target size from the center
    func translateImage(fromImage:CIImage?, centeredBy targetSize:CGSize) -> CIImage? {
        guard let inputImage = fromImage else  { return nil }
        let offset = (inputImage.getSize().width - targetSize.width)/2
        let transform = CGAffineTransform(translationX: -offset, y: 0.0)
        return inputImage.transformed(by: transform)
    }
    
    //MARK: Crop background
    
    //Crop out from the center of the provided CIImage
    //Background must be translated first
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

