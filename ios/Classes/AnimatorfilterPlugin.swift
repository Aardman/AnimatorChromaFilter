import Flutter
import UIKit

public class AnimatorfilterPlugin: NSObject, FlutterPlugin {
    
    private var pipeline:FilterPipeline?
    private var flutterTextureRegistry:FlutterTextureRegistry?
    private var nativeTexture:NativeTexture?
    private static var instance:AnimatorfilterPlugin?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "animatorfilter", binaryMessenger: registrar.messenger())
        instance = AnimatorfilterPlugin()
        if let instance{
            instance.flutterTextureRegistry = registrar.textures()
            registrar.addMethodCallDelegate(instance, channel: channel)
            instance.pipeline = FilterPipeline(filterParameters: FilterParameters(), flutterTextureRegistry: registrar.textures())
        }
    }
    
    //This needs to be called when before any camera frames are recieved in handleUpdate
    public func createNativeTexture(width:Int, height:Int) {
        if  let flutterTextureRegistry {
            nativeTexture = NativeTexture(registry: flutterTextureRegistry, width: width, height: height)
        }
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "create":handleCreate(call, result:result)
        case "initialise":handleCreate(call, result:result)
        case "setBackgroundImagePath":handleSetBackgroundImagePath(call, result:result)
        case "update":handleUpdate(call, result:result)
        case "updateParameters":handleUpdateParameters(call, result:result)
        case "enableFilters":handleEnable(call, result:result)
        case "disableFilters":handleDisable(call, result:result)
        case "dispose":handleDispose(call, result: result)
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    //Handlers
    func handleCreate(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        
        guard let arguments = call.arguments as? NSDictionary,
              let w = arguments["width"] as? Int,
              let h = arguments["height"] as? Int else { result(["textureId", -1]);  return}
        
        AnimatorfilterPlugin.instance?.createNativeTexture(width: w, height: h)
        
        guard let textureId =  AnimatorfilterPlugin.instance?.nativeTexture?.textureId else { result(["textureId", -1]);  return}
        pipeline?.nativeTexture = AnimatorfilterPlugin.instance?.nativeTexture
        let data:[String: Int64] = ["textureId": textureId]
        result(data)
        
    }
    
    //return true if successful
    func handleSetBackgroundImagePath(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        if let arguments = call.arguments as? NSDictionary,
           let imgPath = arguments["imgPath"]  as? String {
            AnimatorfilterPlugin.instance?.pipeline?.setBackgroundImageFrom(path: imgPath)
            let data:[String: Any] = ["result": true]
            result(data);
        }
        else{
            result(["result", "false"])
        }
    }
    
    //just write the input bgra8888 image data to the native texture to display in widget
    func handleUpdate(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        if let arguments = call.arguments as? NSDictionary,
           let flutterData = arguments["imageData"] as? FlutterStandardTypedData,
           let texture = AnimatorfilterPlugin.instance?.nativeTexture {
            let rawImageData = flutterData.data as NSData
            AnimatorfilterPlugin.instance?.pipeline?.update(rawImageData as Data, texture:texture)
            result(["result": true]);
        }
        else{
            result(["error", false])
        }
    }
    
    func handleUpdateParameters(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        if let arguments = call.arguments as? NSDictionary {
            let params = parseParams(arguments)
            pipeline?.filterParameters  = params
            let data:[String: Any] = ["result": true]
            result( ["result": true]);
        }
        else{
            result(["result", "false"])
        }
        let data:[String: Any] = ["result": "true"]
        result(data);
    }
    
    func parseParams(_ arguments: NSDictionary) -> FilterParameters{
        var result = FilterParameters()
        if let colour = arguments["colour"]  as? [Int],
           let sensitivity = arguments["sensitivity"]  as? Float {
            let red = Float(colour[0]/255)
            let green = Float(colour[1]/255)
            let blue  = Float(colour[2]/255)
            result = FilterParameters(
                red:red,
                green:green,
                blue:blue,
                threshold: sensitivity
            )
        }
        return result
    }
    
    func handleEnable(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        AnimatorfilterPlugin.instance?.pipeline?.filtersEnabled = true
        let data:[String: Any] = ["result": "iOS  enableFilters"]
        result( ["result": true]);
    }
    
    func handleDisable(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        AnimatorfilterPlugin.instance?.pipeline?.filtersEnabled = false
        let data:[String: Any] = ["result": "iOS  disableFilters"]
        result( ["result": true]);
    }
    
    func handleDispose(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        let data:[String: Any] = ["result":"iOS " + UIDevice.current.systemVersion]
        result(data);
    }
    
}

