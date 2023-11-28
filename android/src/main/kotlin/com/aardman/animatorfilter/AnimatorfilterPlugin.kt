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
  private var gaussianBlur: GaussianBlur? = null


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

        createFilter(call, result)
      }

      "draw" -> {
        if (gaussianBlur != null) {
          // Get the radius param
          val radius: Double = call.argument("radius")!!

          gaussianBlur!!.draw(radius.toFloat(), true)
          result.success(null)
        } else {
          result.error("NOT_INITIALIZED", "Filter not initialized", null)
        }
      }

      "dispose" -> {
        gaussianBlur?.destroy()
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
 

//TODO Extract correctly formatted data from the camera (via method channel)
private fun createFilter(call: MethodCall,  result: Result) {
  // Get request params
  val width: Int = call.argument("width")!!
  val height: Int = call.argument("height")!!
  val srcImage = call.argument("img") as? ByteArray

  // our response will be a dictionary
  val reply: MutableMap<String, Any> = HashMap()

  if (srcImage != null) {

    //TODO Convert input bytes ready for passing into the filter chain as a correctly formatted bitmap
    // Convert input image to bitmap
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(srcImage))

    // Create a Surface for our filter to draw on, it is backed by a texture we get from Flutter
    flutterSurfaceTexture = pluginBinding!!.textureRegistry.createSurfaceTexture()
    val nativeSurfaceTexture = flutterSurfaceTexture!!.surfaceTexture()
    nativeSurfaceTexture.setDefaultBufferSize(width, height)
    val nativeSurface = Surface(nativeSurfaceTexture)

    // create our filter and tell it to draw to the surface we just created (which is backed
    // by the flutter texture)
    gaussianBlur = GaussianBlur(nativeSurface, bmp)
  }

  // Return the flutter texture id to Flutter land, the "Texture" widget in our app will
  // display it
  reply["textureId"] = flutterSurfaceTexture?.id() ?: -1
  result.success(reply)
}
 

}
