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
  private var gaussianBlur: GaussianBlur? = null

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
        if (gaussianBlur != null) {
          
          // Get the image param
          //val image: ByteArray = call.argument("image")!!
          val image = call.argument<ByteArray>("image") 
  
          if (image != null) { 

            val bmp  = convertByteArrayToBitmap(image, imageWidth, imageHeight)
      
            val radius : Float = 0.9f
      
            //Filterchain processes this as a bitmap
            gaussianBlur!!.update(bmp, radius, true)

            bmp.recycle()

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
 

//Convert unsigned data from Dart to signed data 
fun convertByteArrayToBitmap(image: ByteArray, imageWidth: Int, imageHeight: Int): Bitmap {
    val argb = IntArray(imageWidth * imageHeight)

    for (i in 0 until imageWidth * imageHeight) {
        val a = (image[4 * i] and 0xFF.toByte()).toInt()      // Alpha
        val r = (image[4 * i + 1] and 0xFF.toByte()).toInt()  // Red
        val g = (image[4 * i + 2] and 0xFF.toByte()).toInt()  // Green
        val b = (image[4 * i + 3] and 0xFF.toByte()).toInt()  // Blue

        argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    return Bitmap.createBitmap(argb, imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
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
 gaussianBlur = GaussianBlur(nativeSurface, width, height);

 reply["textureId"] = flutterSurfaceTexture?.id() ?: -1
 result.success(reply)

}
 

}
