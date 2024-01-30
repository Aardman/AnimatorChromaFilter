import Flutter
import UIKit

public class AnimatorfilterPlugin: NSObject, FlutterPlugin {
    
    private var controller = AnimatorFilterController()
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "animatorfilter", binaryMessenger: registrar.messenger())
        let instance = AnimatorfilterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
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
          controller.initialize()
          let data:[String: Any] = ["result": "iOS initialised"]
          result(data);
    }
    
    func handleSetBackgroundImagePath(_ call: FlutterMethodCall, result: @escaping FlutterResult){
          controller.initialize()
          let data:[String: Any] = ["result": "iOS  setBackroundImagePath"]
          result(data);
    }
    //just pass back the input bgra8888 image data to display in widget
    func handleUpdate(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        if let arguments = call.arguments as? NSDictionary, 
            let imageBytes = arguments["imageData"] {
            let data:[String: Any] = ["imageBytes": imageBytes]
            DispatchQueue.main.asyncAfter(deadline: .now(), execute: {
                result(data);
            })
        }
        else{
            result(["error", "no data"])
        }
    }
    
    func handleUpdateParameters(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        let data:[String: Any] = ["result": "iOS  updateParameters"]
        result(data);
    }
    
    func handleEnable(_ call: FlutterMethodCall, result: @escaping FlutterResult){ 
        let data:[String: Any] = ["result": "iOS  enableFilters"]
        result(data);
    }
    
    func handleDisable(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        let data:[String: Any] = ["result": "iOS  disableFilters"]
        result(data);
    }
    
    func handleDispose(_ call: FlutterMethodCall, result: @escaping FlutterResult){
        let data:[String: Any] = ["result":"iOS " + UIDevice.current.systemVersion]
        result(data);
    }
     
                                      
}
                                      
