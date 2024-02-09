# animatorfilter

Flutter filter plugin for filtering images from an image stream provided by the Flutter Camera plug-in

## Platforms

This plug-in currently supports iOS platforms, but not Android.

The example folder contains a simple app that will display the camera preview with chromakey filtering enabled to show a demo background image where the chromakey colour is present in the input images.

##Example Application 

The example app features a demonstration of the filter plugin, in a real app this will be provided by the hosting application. 

The **PreviewPage** Widget encapsulates the setting up and display of the camera preview in the main class. 

This code is not intended as production code, but as a guide to basic setup and control of the filter plugin

## Usage

Add this repo to your pubspec.yaml file and run **flutter pub get**

The plug-in provides a widget that provides a camera preview 

**FilteredPreview**

This takes a **FilterPreviewController** as a constructor argument, this handles filtering images. The constructor argument should be a concrete subclass of **FilterPreviewController**

**FilterPreviewController** is an abstract class implemented by a platform specific class such as 

**FilterPreviewControllerIOS** Implemented

**FilterPreviewControllerAndroid** Not currently implemented

**FilterPreviewController** provides common functions to all platform controllers and defines two abstract methods update and processStillFrame that need to be implemented in a concrete platform specific subclass such as **FilterPreviewControllerIOS**

The following sequence of calls are required to use **FilterPreviewController**

###Initialization and format changes

	Future<void> initialize(double width, double height) async

The initialize method must be supplied by the width and height that are the size of the images being captured. This method calls the native code to provide a textureId (integer) that reflects the native component that will be linked to the **FlutterTexture** widget that is hosted by **FilteredPreview**

This method must be called every time the device has a format change, eg: when it is rotated from portrait to landscape. This will reconstruct the native framebuffer linked to the **FlutterTexture** that is used by the **Texture** widget so that the correct image formatting will happen.

###Setting the image for chromakeying 

The following **FilterPreviewController** method is used to set the background image 

	Future<void> setBackgroundImagePath(String backgroundImagePath) async
	
The background image will be revealed in areas that match the colour parameter to be set (see Setting and Updating parameters)

The background image would ideally be identical in size and orientation as the images to be processed by the camera, however in the iOS platform implementation any image which is incorrectly sized will be scaled and cropped automatically to fit the texture that is used in processing.

###Setting and updating parameters

If no parameters are set then the filtering operation will apply default values with green as the chromakey colour. 

To set parameters or to update them, use the updateFilters method of **FilterPreviewController**

	Future<void> updateFilters(Object params) async 
	
The params argument is structured as follows with a key-value pairing method to minimize API complexity.

 		{ 
     		'colour', [int, int, int], flutter 
     		'threshold', float,
      		'smoothing', float
      }
   
  R, G, B values need to be suppliled as an int[2] array with values ranging from 0 - 255
  
## Enabling and disabling filtering 

When streaming images, filtering is turned off by default. In this mode the image preview provided in the texture is similar to the CameraPreview provided by the Flutter Camera plug-in

Filtering can be turned on and off using the following methods 

	Future<void> enableFilters() async 
	Future<void> disableFilters() async   
  
## Providing streamed images from the camera

Images from the Flutter camera plugin should be provided in the following formats 

iOS - BGRA888
Android - YUV420 (not currently implemented)

To display images in the preview call

	Future<void> update(CameraImage cameraImage) async
	
This will expect that the size of the image data in CameraImage is identical to that provided in the arguments to **initialize**


##Processing stills 

This expects images in the same formats as for update,  in this case this method should be called to return image data that has been filtered. The returned data will be in JPEG encoded format for easier use as it can simply be saved to a file or used for further display or processing. 

	Future<Uint8List> processStillFrame(CameraImage cameraImage) async 
	
This feature is currently not yet fully exposed in the API as it may not be required, however it is implemented in the native code on iOS and can be exposed if required. 




