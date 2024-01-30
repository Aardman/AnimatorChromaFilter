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
    case "create":
        controller.initialize()
      result("iOS  initialised");
    case "initialise":
        controller.initialize()
      result("iOS  initialised");
    case "setBackroundImagePath":
        controller.initialize()
        result("iOS  setBackroundImagePath");
    case "update":
      result("iOS  update");
    case "updateParameters":
      result("iOS  updateParameters");
    case "enableFilters":
      result("iOS  enableFilters");
    case "disableFilters":
      result("iOS  disableFilters");
    case "dispose":
      result("iOS " + UIDevice.current.systemVersion)
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    default:
      result(FlutterMethodNotImplemented)
    }
  } 

}
