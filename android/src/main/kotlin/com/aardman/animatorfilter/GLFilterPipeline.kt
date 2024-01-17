package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLES32.GL_DEBUG_TYPE_ERROR
import android.view.Surface
import com.aardman.animatorfilter.GLUtils.checkEglError
import com.aardman.animatorfilter.GLUtils.getBitmapFromFBO
import com.aardman.animatorfilter.GLUtils.getBitmapFromTexture
import com.aardman.animatorfilter.GLUtils.setupShaderProgram
import com.aardman.animatorfilter.GLUtils.setupVertexArrayForProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
	private var yuvConversionVAO = -1

	//Filter
	private var filterProgram: Int = -1
	private var filterVAO = -1

	//Display
	private var displayProgram: Int = -1
	private var displayVAO = -1

	//Test Quad
	private var testQuadProgram: Int  = -1
	private var testQuadVAO = -1

	//Globals
	private var attributes: MutableMap<String, Int> = hashMapOf()
	private var uniforms: MutableMap<String, Int> = hashMapOf()

	//Main textures
	private var workingTexture1: Int = -1
	private var workingTexture2: Int = -1
	private var backgroundTexture: Int = -1

	// 2D quad coordinates for the vertex shader, we use 2 triangles that will cover
	// the entire image
	val texCoords = floatArrayOf(
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

	//Framebuffer
	private var workingFBO1: Int = -1

	init {
		eglSetup()
		makeCurrent()
		setupOpenGLObjects()
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
	}

	private fun setupOpenGLObjects() {
		setupCoordsVBO()
		setupTextures()
		setupWorkingFBO1()
		setupConverter()
		//setupFilter()
		setupDisplayShader()
		setupTestQuadShader()
	}

	private fun setupTextures(){
		// Create the texture that will hold the source image
		workingTexture1 = GLUtils.createTexture(textureWidth, textureHeight)
		workingTexture2 = GLUtils.createTexture(textureWidth, textureHeight)
		backgroundTexture = GLUtils.createTexture(textureWidth, textureHeight)
		srcYTexture    = GLUtils.createTexture(textureWidth, textureHeight)
		srcUTexture    = GLUtils.createTexture(textureWidth/2, textureHeight/2)
		srcVTexture    = GLUtils.createTexture(textureWidth/2, textureHeight/2)
	}

	//Set up the framebuffer that will be used to render the results
	//Pre-conditions: Textures have been created
	private fun setupWorkingFBO1() {
		// Create a new framebuffer object
		val frameBuffer = IntArray(1)
		GLES30.glGenFramebuffers(1, frameBuffer, 0)
		workingFBO1 = frameBuffer[0]

		// Check if the framebuffer was created successfully
		if (workingFBO1 <= 0) {
			throw RuntimeException("Failed to create a new framebuffer object.")
		}

		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO1)

		//load the working texture to the framebuffer
		GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, workingTexture1, 0)

		// Check if the framebuffer is complete
		if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
			throw RuntimeException("Framebuffer is not complete.")
		}

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

	}

	//VBO is reused for each processing step
	private fun setupCoordsVBO() {

		// Create buffer
		var texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
			put(texCoords)
			position(0)
		}

		val texCoordBuffer = IntArray(1)
		GLES30.glGenBuffers(1, texCoordBuffer, 0)
		checkEglError("generate texCoord buffer")

		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBuffer[0])
		checkEglError("bind coord buffer")

		GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoordsBuffer.capacity() * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)

		texVBO = texCoordBuffer[0]
	}

	private fun setupConverter() {
		this.yuvConversionProgram = setupShaderProgram(VertexShaderSource, conversionShader)

		// Get vertex shader attributes
		this.attributes["c_texCoord"] = GLES30.glGetAttribLocation(this.yuvConversionProgram, "a_texCoord")

		// ... other specific setups like uniforms
		// Find uniforms
		this.uniforms["yTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "yTexture")
		this.uniforms["vTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "vTexture")
		this.uniforms["uTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "uTexture")

		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["c_texCoord"]!!)
		checkEglError("enableVertexAttribArray")
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["c_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
		checkEglError("glVertexAttribPointer")

	    yuvConversionVAO = GLUtils.setupVertexArrayForProgram(yuvConversionProgram)
		checkEglError("generate vertex arrays")
		GLES30.glBindVertexArray(yuvConversionVAO)

		//Enable related attributes, link with currently bound VAO
		GLES30.glEnableVertexAttribArray(this.attributes["c_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["c_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
	}

	private fun setupDisplayShader() {

		this.displayProgram  = setupShaderProgram(VertexShaderSource, displayShader)

		// Get vertex shader attributes, this is the same for all shaders
		this.attributes["d_texCoord"] = GLES30.glGetAttribLocation(this.displayProgram, "a_texCoord")

		// Find uniforms
		this.uniforms["workingTexture"] = GLES30.glGetUniformLocation(this.displayProgram, "workingTexture")

	    displayVAO = GLUtils.setupVertexArrayForProgram(displayProgram)

		checkEglError("generate vertex arrays")
		GLES30.glBindVertexArray(displayVAO)

		//Enable related attributes, link with currently bound VAO
		GLES30.glEnableVertexAttribArray(this.attributes["d_texCoord"]!!)

		//Bind the VBO
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texVBO)

		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["d_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)

		//Unbind VAO and VBO
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
		GLES30.glBindVertexArray(0)
	}

	private fun setupTestQuadShader() {
		this.testQuadProgram = setupShaderProgram(BaseVertexShader, RedFragmentShader)

		// ... other specific setups like uniforms
		// Get vertex shader attributes
		this.attributes["a_texCoord"] = GLES30.glGetAttribLocation(this.testQuadProgram, "a_texCoord")
		checkEglError("glGetAttribLocation a_texCoord")

		testQuadVAO = setupVertexArrayForProgram(testQuadProgram)
		checkEglError("generate vertex arrays")
		GLES30.glBindVertexArray(testQuadVAO)

		//Enable related attributes, link with currently bound VAO
		GLES30.glEnableVertexAttribArray(this.attributes["a_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)

		//Bind the VBO
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texVBO)

		GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)

		//Unbind VAO and VBO
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
		GLES30.glBindVertexArray(0)

	}

	//The main function executed on each image
	fun render(yBytes: ByteArray, uBytes:ByteArray, vBytes: ByteArray, width:Int, height:Int, radius: Float, flip: Boolean = false) {
		makeCurrent()

		displayTestQuad()

		// *** A ****
		//Load Y U V data into existing textures to use in image conversion
	    //GLUtils.updateTextures(yBytes,srcYTexture, uBytes, srcUTexture, vBytes, srcVTexture, width, height)

//		var resultY = getBitmapFromTexture(srcYTexture, textureWidth, textureHeight)
//		var resultU = getBitmapFromTexture(srcUTexture, textureWidth / 2, textureHeight /  2)
//		var resultV = getBitmapFromTexture(srcVTexture, textureWidth / 2, textureHeight /  2)

		// *** B ****
//		convertYUV(width, height)
//		GLUtils.checkEglError("convertYUV")
		//The texture workingTexture now contains the results of the conversion

		// *** C ****
		//applyFilters()

		// *** E ****

		//TODO Delete test code
		//Fill the working texture with solid red for a test rendering
		//populateTestTexture()
		//var resultTest = getBitmapFromFBO(textureWidth, textureHeight, workingFBO1)
		//print("")

		//Draw the filterSrcTexture to the screen
		//displayOutputTexture()

		//D: Output the changed texture to a file on a background thread
		//saveTextureToFile()


	}


	//Converts the srcYUV textures to RGB and stores in workingTexture
	private fun convertYUV(width: Int, height: Int) {

		// Bind the framebuffer where workingTexture is enabled
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO1)

		//Use conversion program and set parameters
		GLES30.glUseProgram(this.yuvConversionProgram)
		GLES30.glUniform1i(this.uniforms["yTexture"]!!, this.srcYTexture)
		GLES30.glUniform1i(this.uniforms["uTexture"]!!, this.srcUTexture)
		GLES30.glUniform1i(this.uniforms["vTexture"]!!, this.srcVTexture)

		//Set the viewport
		GLES30.glViewport(0, 0, width, height)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		//Bind the VAO
		GLES30.glBindVertexArray(yuvConversionVAO)

		//Draw to the currently bound texture using the program
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		//Unbind vao
		GLES30.glBindVertexArray(0)

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
	}

	//Displays workingTexture to the screen
	private fun displayOutputTexture() {

		// Bind the default framebuffer to render to the screen
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
		GLUtils.checkEglError("Binding FBO texture")

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
		GLES30.glUseProgram(displayProgram)
		GLUtils.checkEglError("glUseProgram displayProgram")

		// Bind workingTexture to a texture unit and set the corresponding uniform in the shader
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, workingTexture1)
		GLUtils.checkEglError("Bind FBO texture")
		GLES30.glUniform1i(uniforms["workingTexture"]!!, 0)  // Assuming a uniform for the texture in the shader

		// Bind the VAO that contains the vertex data for the quad
		GLES30.glBindVertexArray(displayVAO)

		// Draw the textured quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// Unbind the VAO
		GLES30.glBindVertexArray(0)

		//Unbind the texture
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

		// Swap buffers to display the result
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")

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

		// Bind the VAO that contains the vertex data for the quad
		GLES30.glBindVertexArray(testQuadVAO)
		checkEglError("bind VAO")

		// Draw the solid debug quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
		checkEglError("glDrawArrays")

		// Unbind the VAO
		GLES30.glBindVertexArray(0)
		checkEglError("unbind AO")

		// Unbind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

	}

	//Display test quad used to validate the pipeline
	private fun displayTestQuad() {
		// Disable depth testing for 2D rendering
		GLES30.glDisable(GLES30.GL_DEPTH_TEST)

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
		GLES30.glUseProgram(testQuadProgram)
		GLUtils.checkEglError("Use testQuadProgram")

		// Bind the VAO that contains the vertex data for the quad
		GLES30.glBindVertexArray(testQuadVAO)
		checkEglError("bind vertex array")

		// Draw the solid debug quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// Unbind the VAO
		GLES30.glBindVertexArray(0)

		// Swap buffers to display the result
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")
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
