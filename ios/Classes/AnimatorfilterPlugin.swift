import Flutter
import UIKit

public class AnimatorfilterPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "animatorfilter", binaryMessenger: registrar.messenger())
    let instance = AnimatorfilterPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    case "initialise":
      AnimatorFilterController.initialize()
      result("iOS  initialised");
    case "update":
      result("iOS  updated");
    case "updateFilters":
      result("iOS  updateFilters");
    case "enableFilters":
      result("iOS  enableFilters");
    case "disableFilters":
      result("iOS  disableFilters");
    default:
      result(FlutterMethodNotImplemented)
    }
  } 

}
