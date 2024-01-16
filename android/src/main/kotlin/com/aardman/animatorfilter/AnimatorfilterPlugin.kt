package com.aardman.animatorfilter

import android.view.Surface

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.view.TextureRegistry

/** AnimatorfilterPlugin */
class AnimatorfilterPlugin: FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel

  //Shared texture that backs the preview
  private var flutterSurfaceTexture: TextureRegistry.SurfaceTextureEntry? =  null

  private var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
  private var filterPipeline: GLFilterPipeline? = null

  private var imageWidth: Int  = 0
  private var imageHeight: Int = 0

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "animatorfilter")
    channel.setMethodCallHandler(this)
    this.pluginBinding = flutterPluginBinding
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    
    when(call.method) {

      "create" -> {
        if(pluginBinding == null){
          result.error("NOT_READY",  "pluginbinding is null", null)
          return
        }

        //Create with width and height parameters from the call
        createFilter(call, result)
      }

      //TODO implement enableFilters
      "enableFilters" -> {
        if(pluginBinding == null){
          result.error("NOT_READY",  "pluginbinding is null", null)
          return
        }

        //Create with width and height parameters from the call
        //enableFilters(call, result)
      }

      //TODO implement disableFilters
      "disableFilters" -> {
        if(pluginBinding == null){
          result.error("NOT_READY",  "pluginbinding is null", null)
          return
        }

        //Create with width and height parameters from the call
        //disableFilters(call, result)
      }

      /* call passes
       data = {
        "isInitialising": true,
        "backgroundPath": fullPath,
        "colour": [0.0, 255.0, 0.0],
        "sensitivity": 0.45,
       } */
      //TODO implement updateFilters
      "updateFilters" -> {
        if(pluginBinding == null){
          result.error("NOT_READY",  "pluginbinding is null", null)
          return
        }

        //Create with width and height parameters from the call
        //updateFilters(call, result)
      }

      "update" -> {
        if (filterPipeline != null) {
          
          // Get the image planes from the YUV420 input
          val y = call.argument<ByteArray>("Y")
          val u = call.argument<ByteArray>("U")
          val v = call.argument<ByteArray>("V")
          val width = call.argument<Int>("width")!!
          val height = call.argument<Int>("height")!!
           
          if (y != null && u != null && v !== null) {
 
            val radius : Float = 0.9f
      
            //Filterchain processes this as a bitmap
            filterPipeline!!.render(y, u, v, width, height, radius, true)
  
            result.success(null)
          }
          else  {
            result.error("FAILED UPDATE", "Cannot extract a ByteArray from the image parameter", null)
          }
         
        } else {
          result.error("FAILED UPDATE", "Image update unsuccessful", null)
        }
      }
 
      "dispose" -> {
        filterPipeline?.destroy()
        if (flutterSurfaceTexture != null) {
          flutterSurfaceTexture!!.release()
        }
      }

      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }

      else -> {
       result.notImplemented()
      }
    }
  }

 
  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    this.pluginBinding = null
  }  

//Create the textures that will be used to communicate between openGL and 
//the client application for the flutter plugin
private fun createFilter(call: MethodCall,  result: Result) {
  // Get request params
  val width: Int = call.argument("width")!!
  val height: Int = call.argument("height")!!

  this.imageWidth  = width
  this.imageHeight = height

  // our response will be a dictionary
  val reply: MutableMap<String, Any> = HashMap()

 // Create a Surface for our filter to draw on, it is backed by a texture we get from Flutter
 flutterSurfaceTexture = pluginBinding!!.textureRegistry.createSurfaceTexture()
 val nativeSurfaceTexture = flutterSurfaceTexture!!.surfaceTexture()
 nativeSurfaceTexture.setDefaultBufferSize(width, height)
 val nativeSurface = Surface(nativeSurfaceTexture)

 // create our filter ready to the surface we just created (which is backed
 // by the flutter texture
 filterPipeline = GLFilterPipeline(nativeSurface, width, height);

 reply["textureId"] = flutterSurfaceTexture?.id() ?: -1
 result.success(reply)

}
 

}
