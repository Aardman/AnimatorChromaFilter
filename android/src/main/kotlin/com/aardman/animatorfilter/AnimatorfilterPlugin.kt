package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.view.Surface 

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.view.TextureRegistry
import java.nio.ByteBuffer
import kotlin.experimental.and

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

      //TODO implement update
      "update" -> {
        if (filterPipeline != null) {
          
          // Get the image param
          //val image: ByteArray = call.argument("imagedata")!!
          val imagedata = call.argument<ByteArray>("imagedata") 
          val imagewidth = call.argument<Int>("width")
          val imageheight = call.argument<Int>("height")
           
          if (image != null) { 
 
            val radius : Float = 0.9f
      
            //Filterchain processes this as a bitmap
            filterPipline!!.update(imagedata, width, height, radius, true)
  
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
        filterPipline?.destroy()
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

  imageWidth  = width
  imageHeight = height

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
