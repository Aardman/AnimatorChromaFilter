package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLES30
import android.opengl.GLES30.GL_RED
import android.opengl.GLES30.GL_UNSIGNED_BYTE
import android.util.Size
import android.view.Surface
import com.aardman.animatorfilter.GLUtils.checkEglError
import com.aardman.animatorfilter.GLUtils.checkTexturePixels
import com.aardman.animatorfilter.GLUtils.checkVAOIsBound
import com.aardman.animatorfilter.GLUtils.checkVBOIsBound
import com.aardman.animatorfilter.GLUtils.padArrays
import com.aardman.animatorfilter.GLUtils.setupFramebuffer
import com.aardman.animatorfilter.GLUtils.setupShaderProgram
import com.aardman.animatorfilter.GLUtils.makeVAO
import com.aardman.animatorfilter.GLUtils.printActiveUniforms
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class GLFilterPipeline(private val outSurface: Surface, private val textureWidth:Int, private  val textureHeight:Int) {

	//EGL
	private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
	private var mEGLContext = EGL14.EGL_NO_CONTEXT
	private var mEGLSurface = EGL14.EGL_NO_SURFACE

	//Conversion
	private var yuvConversionProgram: Int = -1
	private var srcYTexture:Int = -1
	private var srcUTexture:Int = -1
	private var srcVTexture:Int = -1

	//Temporary sample images
	private var backgroundImg: Bitmap? = null
	private var cameraImg: Bitmap? = null

	//Filter
	private var inputImageTexture:Int = -1
	private var backgroundImageTexture: Int = -1
	private var filterProgram: Int = -1
	private var smoothing: Float = 0f //Set to a constant, no UI to change this
	private var filterParameters: FilterParameters = FilterParameters()

	//Display
	private var displayProgram: Int = -1

	//Test Quad
	private var testQuadProgram: Int  = -1

	//Globals
	private var uniforms: MutableMap<String, Int> = hashMapOf()

	//Main textures
	private var workingTexture1: Int = -1
	private var workingTexture2: Int = -1

	// 2D quad coordinates for the vertex shader, we use 2 triangles that will cover
	// the entire image
	val quadCoords = floatArrayOf(
		// 1st triangle
		0f, 0f,
		1f, 0f,
		0f, 1f,
		// 2nd triangle
		0f, 1f,
		1f, 0f,
		1f, 1f
	)

	private var texVBO = -1
	private var texVAO = -1

	//Framebuffers
	//Alternate for processing steps on textures
	private var workingFBO1: Int = -1
	private var workingFBO2: Int = -1

	init {
		eglSetup()
		makeCurrent()
		setupOpenGLObjects()
		makeCurrent()  //only one context set it up at the start
	}

	private fun eglSetup() {
		// Create EGL display that will output to the given outSurface
		mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
		if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
			throw RuntimeException("unable to get EGL14 display")
		}

		val version = IntArray(2)
		if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
			throw RuntimeException("unable to initialize EGL14")
		}

		// Configure EGL
		val attribList = intArrayOf(
			EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
			EGL14.EGL_RED_SIZE, 8,
			EGL14.EGL_GREEN_SIZE, 8,
			EGL14.EGL_BLUE_SIZE, 8,
			EGL14.EGL_ALPHA_SIZE, 8,
			EGL14.EGL_LEVEL, 0,
			EGL14.EGL_RENDERABLE_TYPE, /* EGL14.EGL_OPENGL_ES2_BIT,*/ EGLExt.EGL_OPENGL_ES3_BIT_KHR,
			EGL14.EGL_NONE // mark list termination
		)

		val configs = arrayOfNulls<EGLConfig>(1)
		val numConfig = IntArray(1)
		EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, 1, numConfig, 0)
		if (numConfig[0] == 0) {
			throw Exception("No EGL config was available")
		}

		// Configure context for OpenGL ES 3.0.
		val attrib_list = intArrayOf(
			EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
			EGL14.EGL_NONE
		)

		mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0)
		GLUtils.checkEglError("eglCreateContext")

		// Create a window surface, and attach it to the Surface we received.
		val surfaceAttribs = intArrayOf(
			EGL14.EGL_NONE
		)

		// create a new EGL window surface, we use the "outSurface" provided to us (by Flutter).
		mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], outSurface, surfaceAttribs, 0)
		GLUtils.checkEglError("eglCreateWindowSurface")

		// Disable depth testing for 2D rendering
		GLES30.glDisable(GLES30.GL_DEPTH_TEST)
	}

	private fun setupOpenGLObjects() {

		//After these first two steps texVBO and texVAO are setup and bound for the duration of the pipeline
		setupCoordsVBO()
		setupTexVAO(texVBO, quadCoords)

		//Textures created and workingTextures bound to Framebuffers for repeated updates
		setupTextures()
		setupFramebuffers()

		//Setup methods, mostly only load programs and identify uniforms for processing
		setupConverter()
		setupFilter()
		setupDisplayShader()
		setupTestQuadShader()
	}

	/**
	 * NB: The input UV planes are a byte short of the Y plane size / 2 out of the Android Camera
	 * Thus the last byte in each U V plane is missing
	 */
	private fun setupTextures(){
		// Create the texture that will hold the source image
		workingTexture1 = GLUtils.createTexture(textureWidth, textureHeight)
		workingTexture2 = GLUtils.createTexture(textureWidth, textureHeight)
		backgroundImageTexture = GLUtils.createTexture(textureWidth, textureHeight)

		//Monochrome textures, this uses 8 bit signed raw data from a byte array but will be interpreted correctly despite being signed from a ByteArray
		srcYTexture    = GLUtils.createTexture(textureWidth, textureHeight, GLES30.GL_R8, GL_RED, GL_UNSIGNED_BYTE )

		//TODO: It is incorrect to / dimensions by 2 as the textures will be too small  to accomodate the required data
		//Instead make the U and  V textures  the  same  size  as the  Y texture, then fill a subset of the texture based on
		//the aspect ratio of  the input
		srcUTexture    = GLUtils.createTexture(textureWidth , textureHeight, GLES30.GL_R8, GL_RED, GL_UNSIGNED_BYTE )
		srcVTexture    = GLUtils.createTexture(textureWidth, textureHeight, GLES30.GL_R8, GL_RED, GL_UNSIGNED_BYTE )
	}

	//Set up the framebuffer that will be used to render the results
	//Pre-conditions: Textures have been created
	private fun setupFramebuffers(){
		workingFBO1 = setupFramebuffer(workingTexture1)
		workingFBO2 = setupFramebuffer(workingTexture2)
	}

	//Create,load data and bind VBO
	//VBO is reused for each processing step and remains bound during all processing steps
	private fun setupCoordsVBO() {

		// Create buffer
		var quadCoordsBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
			put(quadCoords)
			position(0)
		}

		val quadCoordVBOs = IntArray(1)
		GLES30.glGenBuffers(1, quadCoordVBOs, 0)
		checkEglError("generate texCoord buffer")

		texVBO = quadCoordVBOs[0]

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texVBO)
		checkEglError("bind coord buffer")

		GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadCoordsBuffer.capacity() * 4, quadCoordsBuffer, GLES30.GL_STATIC_DRAW)

	}

	//Create VAO, bind it, then bind to VBO and enable the position attribute
	//VAO is reused for each processing step and remains bound during all processing steps
	private fun setupTexVAO(vboId: Int, vertexData: FloatArray)  {
		val vaoIds = IntArray(1)
		GLES30.glGenVertexArrays(1, vaoIds, 0) // Generate VAO ID

		texVAO = vaoIds[0]
		GLES30.glBindVertexArray(texVAO) // Bind the VAO,  remains bound for entire pipeline

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId) // Bind the VBO
		//GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexData.size * 4, FloatBuffer.wrap(vertexData), GLES30.GL_STATIC_DRAW) // Upload vertex data

		//position is always 0 in the single vertex shader we are using
		val positionAttributeLocation = 0
		glEnableVertexAttribArray(positionAttributeLocation) // Enable vertex attribute array
		glVertexAttribPointer(positionAttributeLocation, 2, GLES30.GL_FLOAT, false, 2 * 4, 0) // Specify vertex attribute pointer

	}

	private fun setupTestQuadShader() {
		this.testQuadProgram = setupShaderProgram(BaseVertexShader, SolidFragmentShader)

		//printActiveUniforms(testQuadProgram, "testQuadProgram")

		this.uniforms["r"] = GLES30.glGetUniformLocation(this.testQuadProgram, "r")
		this.uniforms["g"] = GLES30.glGetUniformLocation(this.testQuadProgram, "g")
		this.uniforms["b"] = GLES30.glGetUniformLocation(this.testQuadProgram, "b")
	}

	private fun setupConverter() {
		this.yuvConversionProgram = setupShaderProgram(VertexShaderSource, conversionShader) // conversionShader)

		//printActiveUniforms(yuvConversionProgram,"yuvConversionProgram")

		// ... other specific setups like uniforms
		// Find uniforms
		this.uniforms["yTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "yTexture")
		this.uniforms["vTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "vTexture")
		this.uniforms["uTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "uTexture")
	}

	//Setup the chroma filter
	private fun setupFilter(){
		this.filterProgram = setupShaderProgram(VertexShaderSource, chromaKeyFilter)

		 printActiveUniforms(filterProgram,"filterProgram")

		// Get vertex shader attributes
		//this.attributes["chromaTextureCoordinate"] = GLES30.glGetAttribLocation(this.filterProgram, "chromaTextureCoordinate")

		// ... other specific setups like uniforms
		// Find uniforms
		this.uniforms["inputImageTexture"] = GLES30.glGetUniformLocation(this.filterProgram, "inputImageTexture")
		this.uniforms["backgroundImageTexture"] = GLES30.glGetUniformLocation(this.filterProgram, "backgroundImageTexture")
		this.uniforms["thresholdSensitivity"] = GLES30.glGetUniformLocation(this.filterProgram, "thresholdSensitivity")
		this.uniforms["smoothing"] = GLES30.glGetUniformLocation(this.filterProgram, "backgroundImageTexture")
		this.uniforms["backgroundImageTexture"] = GLES30.glGetUniformLocation(this.filterProgram, "colorToReplace")
	}

	private fun setupDisplayShader() {
		this.displayProgram  = setupShaderProgram(BaseVertexShader, displayShader)

	    printActiveUniforms(displayProgram, "displayProgram")

		// Save uniform names for later use
		this.uniforms["workingTexture"] = GLES30.glGetUniformLocation(this.displayProgram, "workingTexture")
	}

	//Converts the srcYUV textures to RGB and stores in workingTexture
	private fun convertYUV(width: Int, height: Int) {

		// Bind the framebuffer where workingTexture is enabled
		GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, workingFBO1)

		GLES30.glUseProgram(this.yuvConversionProgram)

		// Activate and bind the Y texture
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.srcYTexture)
		GLES30.glUniform1i(this.uniforms["yTexture"]!!, 0) // Texture unit 0

	    // Activate and bind the U texture
		GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.srcUTexture)
		GLES30.glUniform1i(this.uniforms["uTexture"]!!, 1) // Texture unit 1

	    // Activate and bind the V texture
		GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.srcVTexture)
		GLES30.glUniform1i(this.uniforms["vTexture"]!!, 2) // Texture unit 2

		//Set the viewport
		GLES30.glViewport(0, 0, width, height)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		//Draw to the currently bound texture using the program
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		//Check for contents by reading pixels from the framebuffer
		val buffer = ByteBuffer.allocateDirect(textureWidth * textureHeight)
		buffer.order(ByteOrder.nativeOrder())
		GLES30.glReadPixels(0, 0, textureWidth, textureHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
	}

	//Applies the Chromakey filter to the input framebuffer
	private fun applyFilter(){
		// Bind the ouptut framebuffer bound to the output texture
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO2)

		//Use conversion program and set parameters
		GLES30.glUseProgram(this.filterProgram)
		GLES30.glUniform1i(this.uniforms["inputImageTexture"]!!, this.inputImageTexture)
		GLES30.glUniform1i(this.uniforms["backgroundImageTexture"]!!, this.backgroundImageTexture)
		GLES30.glUniform1f(this.uniforms["thresholdSensitivity"]!!, filterParameters.sensitivity)
		GLES30.glUniform1f(this.uniforms["smoothing"]!!, this.smoothing)

		val red = filterParameters.colorToReplace[0]
		val green  = filterParameters.colorToReplace[1]
		val blue = filterParameters.colorToReplace[2]
		GLES30.glUniform3f(this.uniforms["colorToReplace"]!!, red, green, blue)

		//Set the viewport
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		//Draw to the currently bound texture using the program
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
	}


	///Need to perform this off the main thread
	//May need to write FBO/texture to a buffer prior to saving
	private fun saveTextureToFile(){

	}

	//Uses same program as the test quad but renders it to the workingTexture in the framebuffer
	private fun populateTestTexture(){

		GLES30.glUseProgram(testQuadProgram)
		GLUtils.checkEglError("Use testQuadProgram")

		// Disable depth testing for 2D rendering
		GLES30.glDisable(GLES30.GL_DEPTH_TEST)

		// Bind the framebuffer where workingTexture is enabled
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO1)
		checkEglError("bind framebuffer")

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

		// Draw the solid debug quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
		checkEglError("glDrawArrays")

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
	}

	//Draw Methods

	//Display test quad used to validate the pipeline, VBO and VAO setup etc.
	private fun drawTestQuad() {

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

		GLES30.glUseProgram(testQuadProgram)
		GLUtils.checkEglError("Use testQuadProgram")

		// Draw the solid debug quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// Swap buffers to display the result
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")
	}

	//Display test quad used to validate the pipeline, VBO and VAO setup etc.
	private fun drawTestQuadToFramebuffer() {

		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO1)

		GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, workingTexture1);

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClearColor(1.0f, 0.5f, 0.5f, 0f)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
		GLES30.glUniform1f(uniforms["r"]!!, 0.5f)
		GLES30.glUniform1f(uniforms["g"]!!, 0.6f)
		GLES30.glUniform1f(uniforms["b"]!!, 0.7f)

		GLES30.glUseProgram(testQuadProgram)
		GLUtils.checkEglError("Use testQuadProgram")

		//Checkerboard resolution
//		var resolution:IntBuffer = IntBuffer.allocate(2)
//		resolution.put(intArrayOf(10,10))
//		GLES30.glUniform1iv(uniforms["iResolution"]!!, 2, resolution)

		//printPixels("testQuad pre draw",  textureWidth, textureHeight)

		// Draw the solid debug quad to the framebuffer using bound VAO
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		//Debugging framebuffer
//		val  whichBuffer  = IntArray(1)
//		GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, whichBuffer, 0)
//		val currentFramebuffer = whichBuffer[0]

		//printPixels("testQuad after draw",  textureWidth, textureHeight)

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

		//printPixels("testQuad after bind fb0",  textureWidth, textureHeight)

		displayOutputTexture(workingTexture1) //texture of fbo1
	}


	//Displays workingTexture to the screen
	private fun displayOutputTexture(textureId: Int) {

		// Bind the default framebuffer to render to the screen
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
		GLUtils.checkEglError("Binding FBO texture")

		// Clear the FBO Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClearColor(0f, 0.25f, 0.5f, 0f)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

		GLES30.glUseProgram(displayProgram)
		GLUtils.checkEglError("glUseProgram displayProgram")

		// Bind textureId currently attached to an FBO to a texture unit and set the corresponding uniform in the shader
		GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, workingTexture1)
		GLUtils.checkEglError("Bind texture attached to FBO")

		val textureUniformLocation = GLES20.glGetUniformLocation(displayProgram, "workingTexture")
		GLUtils.checkEglError("trying to get uniform location")
		GLES30.glUniform1i(textureUniformLocation, 1) // texture unit  1

		//Check for contents by reading pixels from the framebuffer
		printPixels("Display pre draw",  textureWidth, textureHeight)

		// Draw the textured quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		//Check for contents by reading pixels from the framebuffer
		printPixels("Display post draw",  textureWidth, textureHeight)

		//Unbind the texture
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

		// Swap buffers to display the result
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")

	}


	//Draw Methods for different examples

	//TODO: delete after pipeline tested
	//Gaussian example applied to background
	fun drawWithFilter(radius: Float, flip: Boolean = false) {

		// Tell it to use our program
		GLES30.glUseProgram(this.filterProgram)

		// set u_radius in fragment shader
		GLES30.glUniform1f(this.uniforms["u_radius"]!!, radius)
		GLES30.glUniform1f(this.uniforms["u_flipY"]!!, if (flip) -1f else 1f) // need to y flip for canvas

		// Tell the shader to get the texture from texture unit 0
		GLES30.glUniform1i(this.uniforms["u_image"]!!, 0)
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + 0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundImageTexture)

		// Unbind any output frame buffer that may be have bounded by other OpenGL programs (so we render to the default display)
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClearColor(0f, 0f, 0f, 0f)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		// Draw the rectangles we put in the vertex shader
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// This "draw" the result onto the surface we got from Flutter
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")
	}

	//Main draw method for chromakey processing
	fun draw (yBytes: ByteArray, uBytes: ByteArray, vBytes: ByteArray, width: Int, height: Int, radius: Float, flip: Boolean = false) {	// *** A ****

		// *** A ****
		//Load Y U V data ( into existing textures to use in image conversion

		//Pad U V if necessary, adding bytes at end to fill expected rowstride
		val (uBytesPadded, vBytesPadded) = padArrays(yBytes, uBytes, vBytes)

		GLUtils.updateYUVTextures(yBytes, srcYTexture, uBytesPadded, srcUTexture, vBytesPadded, srcVTexture, width, height)

		//TODO: delete these as they are just for debugging by viewing individual bitmaps in debugger
		var resultY = checkTexturePixels(srcYTexture, textureWidth, textureHeight, GLES30.GL_RED, yBytes)
		var resultU = checkTexturePixels(srcUTexture, textureWidth/2, textureHeight/2,   GLES30.GL_RED, uBytesPadded)
		var resultV = checkTexturePixels(srcVTexture, textureWidth/2, textureHeight/2,   GLES30.GL_RED, vBytesPadded)
		//print( resultY ?: "Y OK" + resultU  ?: "U OK" + resultV ?: "V OK")

		// *** B ****
		convertYUV(width, height)
		GLUtils.checkEglError("convertYUV")
		//The texture workingTexture now contains the results of the conversion
		val (bytes, buffer) = checkTexturePixels(workingTexture1, textureWidth, textureHeight, GLES30.GL_RGBA, null)
		print ("converted workingTexture1 sampled pixels from = " + bytes + " to "+ buffer)

		// *** C ****
		//applyFilter()

		//TODO Delete test code
		//Fill the working texture with solid red for a test rendering
		//populateTestTexture()
		//var resultTest = getBitmapFromFBO(textureWidth, textureHeight, workingFBO1)
		//print("")

		// *** D  ****
		//Draw the filterSrcTexture to the screen
		//displayOutputTexture()

		// *** E  ****
		//D: Output the changed texture to a file on a background thread
		saveTextureToFile()
	}

	//API

	//The main function executed on each camera frame
	public fun render(yBytes: ByteArray, uBytes: ByteArray, vBytes: ByteArray, width: Int, height: Int, radius: Float, flip: Boolean = false) {
		makeCurrent()
		//draw(yBytes,uBytes,vBytes, width, height, radius, flip)
		//drawWithFilter(1f, true)  //Test coherence with simple gaussian sample
		// drawTestQuad()  //draw red quad on screen
		 drawTestQuadToFramebuffer()
	}

	public fun updateParameters(filterParameters: FilterParameters){
		filterParameters.updateWith(filterParameters);
		if (filterParameters.backgroundImage != null) {
			val path = filterParameters.backgroundImage
			//create the bitmap and texture
			val bitmap = ImageProcessing.getBackground(path, Size(textureWidth, textureHeight), true);
			backgroundImg = bitmap
			backgroundImageTexture = GLUtils.createTextureFromBitmap(backgroundImg, textureWidth, textureHeight)
		}
	}


	public fun setBackgroundImage(bitmap: Bitmap) {
		if (backgroundImg != null)  {
			cameraImg =  bitmap
		}
		else {
			backgroundImg = bitmap
			backgroundImageTexture = GLUtils.createTextureFromBitmap(backgroundImg, textureWidth, textureHeight)
			//drawWithFilter(1f, true) //trigger initial draw when background is changed
		}
	}


	//TODO: Used only for testing/development

	fun  printPixels(label:String, textureWidth:Int,  textureHeight: Int) {
		val buffer = ByteBuffer.allocateDirect(textureWidth * textureHeight * 4)
		buffer.order(ByteOrder.nativeOrder())
		GLES30.glReadPixels(0, 0, textureWidth, textureHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
		for (i in 0 until 16 step 4) { // Just as an example, reading the first few pixels
			val r = buffer.get(i).toInt() and 0xFF
			val g = buffer.get(i + 1).toInt() and 0xFF
			val b = buffer.get(i + 2).toInt() and 0xFF
			val a = buffer.get(i + 3).toInt() and 0xFF
			println("Pixel $label: R=$r, G=$g, B=$b, A=$a")
		}
	}

	public fun setBackgroundDemo(){
		var bitmap  = ImageProcessing.getBackground("/data/user/0/com.aardman.animatorfilter_example/cache/tempfile.jpg",
													Size(720, 1280), true)
		setBackgroundImage(bitmap)
	}


	//EGL Lifecycle

	private fun makeCurrent() {
		EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
		GLUtils.checkEglError("eglMakeCurrent")
	}

	fun destroy() {

		// Unbind the framebuffer by binding the default framebuffer, '0'
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

		//Delete textures
		val texts = intArrayOf(this.workingTexture1, this.srcYTexture, this.srcUTexture, this.srcVTexture)
		GLES30.glDeleteTextures(texts.size, texts, 0)

		if (mEGLDisplay !== EGL14.EGL_NO_DISPLAY) {
			EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
			EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
			EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
			EGL14.eglReleaseThread()
			EGL14.eglTerminate(mEGLDisplay)
		}

		mEGLDisplay = EGL14.EGL_NO_DISPLAY
		mEGLContext = EGL14.EGL_NO_CONTEXT
		mEGLSurface = EGL14.EGL_NO_SURFACE
	}

}
