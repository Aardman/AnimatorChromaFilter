package com.aardman.animatorfilter

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GLFilterPipeline(private val outSurface: Surface, private val textureWidth:Int, private  val textureHeight:Int) {

	private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
	private var mEGLContext = EGL14.EGL_NO_CONTEXT
	private var mEGLSurface = EGL14.EGL_NO_SURFACE

	//Conversion
	private var yuvConversionProgram: Int = -1
	private var srcYTexture:Int = -1
	private var srcUTexture:Int = -1
	private var srcVTexture:Int = -1

	private var filterSrcTexture: Int = -1

	//Filter
	private var gaussianProgram: Int = -1
	private var attributes: MutableMap<String, Int> = hashMapOf()
	private var uniforms: MutableMap<String, Int> = hashMapOf()
	private var vao: IntArray = IntArray(1)

	// texture coordinates for the vertex shader, we use 2 rectangles that will cover
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


	//Demo filter to be replaced with ChromaKey filter
	private val gaussianShader = """#version 300 es
		precision highp float;

		uniform sampler2D u_image;
		in vec2 v_texCoord;
		uniform float u_radius;
		out vec4 outColor;

		const float Directions = 16.0;
		const float Quality = 3.0;
		const float Pi = 6.28318530718; // pi * 2

		void main()
		{
			vec2 normRadius = u_radius / vec2(textureSize(u_image, 0));
			vec4 acc = texture(u_image, v_texCoord);
			for(float d = 0.0; d < Pi; d += Pi / Directions)
			{
				for(float i = 1.0 / Quality; i <= 1.0; i += 1.0 / Quality)
				{
					acc += texture(u_image, v_texCoord + vec2(cos(d), sin(d)) * normRadius * i);
				}
			}

			acc /= Quality * Directions;

			outColor =  acc;
		}
	"""

	//Shader converts data from input textures into RGB format
	private val conversionShader = """#version 300 es
		precision mediump float;

		uniform sampler2D yTexture;
		uniform sampler2D uTexture;
		uniform sampler2D vTexture;
		varying vec2 texCoord;

		void main() {
			float y = texture2D(yTexture, texCoord).r;
			float u = texture2D(uTexture, texCoord).r - 0.5;
			float v = texture2D(vTexture, texCoord).r - 0.5;
		
			float r = y + 1.403 * v;
			float g = y - 0.344 * u - 0.714 * v;
			float b = y + 1.770 * u;
		
			gl_FragColor = vec4(r, g, b, 1.0);
    }
	"""

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
		setupCoordinates()
		setupConverter()
		setupFilter()
		setupTextures()
	}

	private fun setupTextures(){
		// Create the texture that will hold the source image
		filterSrcTexture = GLUtils.createTexture(textureWidth, textureHeight)
	}

	private fun setupCoordinates(){
		this.vao = setupVertexArray(texCoords)
	}

	//We only need one vertex array in this case as texCoords are the same for each step
	//of a filtering pipeline
	private fun setupVertexArray(texCoords: FloatArray): IntArray {
		val vao = IntArray(1)
		GLES30.glGenVertexArrays(1, vao, 0)
		GLES30.glBindVertexArray(vao[0])

		val texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
			put(texCoords)
			position(0)
		}

		val texCoordBuffer = IntArray(1)
		GLES30.glGenBuffers(1, texCoordBuffer, 0)
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBuffer[0])
		GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoordsBuffer.capacity() * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)

		val texCoordLocation = GLES30.glGetAttribLocation(gaussianProgram, "a_texCoord")
		GLES30.glEnableVertexAttribArray(texCoordLocation)
		GLES30.glVertexAttribPointer(texCoordLocation, 2, GLES30.GL_FLOAT, false, 0, 0)

		return vao
	}

	private fun setupShaderProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
		// Create and compile shaders, link program, etc.
		var  program  = GLUtils.createProgram(
			vertexShaderSource, fragmentShaderSource
		)
		return program
	}

	private fun setupFilter() {
		this.gaussianProgram = setupShaderProgram(GLUtils.VertexShaderSource, gaussianShader)

		// ... other specific setups like uniforms
		// Get vertex shader attributes
	    this.attributes["a_texCoord"] = GLES30.glGetAttribLocation(this.gaussianProgram, "a_texCoord")
		// Find uniforms
		this.uniforms["u_flipY"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_flipY")
		this.uniforms["u_image"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_image")
		this.uniforms["u_radius"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_radius")

		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["a_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
	}

	private fun setupConverter() {
		this.yuvConversionProgram = setupShaderProgram(GLUtils.VertexShaderSource, conversionShader)

		// Get vertex shader attributes
		this.attributes["a_texCoord"] = GLES30.glGetAttribLocation(this.yuvConversionProgram, "a_texCoord")

		// Find uniforms
		// ... other specific setups like uniforms
		// Find uniforms
		this.uniforms["yTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "yTexture")
		this.uniforms["vTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "vTexture")
		this.uniforms["uTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "uTexture")

		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["a_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
	}

//	private fun setupFilterProgram() {
//
//		// create the program
//		this.gaussianProgram = GLUtils.createProgram(
//			GLUtils.VertexShaderSource,
//			gaussianShader
//		)
//
//		// Get vertex shader attributes
//		this.attributes["a_texCoord"] = GLES30.glGetAttribLocation(this.gaussianProgram, "a_texCoord")
//
//		// Find uniforms
//		this.uniforms["u_flipY"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_flipY")
//		this.uniforms["u_image"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_image")
//		this.uniforms["u_radius"] = GLES30.glGetUniformLocation(this.gaussianProgram, "u_radius")
//
//		// Create a vertex array object (attribute state)
//		GLES30.glGenVertexArrays(1, this.vao, 0)
//		// and make it the one we're currently working with
//		GLES30.glBindVertexArray(this.vao[0])
//
//		// provide texture coordinates to the vertex shader, we use 2 rectangles that will cover
//		// the entire image
//		val texCoords = floatArrayOf(
//			// 1st triangle
//			0f, 0f,
//			1f, 0f,
//			0f, 1f,
//			// 2nd triangle
//			0f, 1f,
//			1f, 0f,
//			1f, 1f
//		)
//
//		val texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
//		texCoordsBuffer.put(texCoords)
//		texCoordsBuffer.position(0)
//
//		// Create a buffer to hold the texCoords
//		val texCoordBuffer = IntArray(1)
//		GLES30.glGenBuffers(1, texCoordBuffer, 0)
//		// Bind it to ARRAY_BUFFER (used for Vertex attributes)
//		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBuffer[0])
//		// upload the text corrds into the buffer
//		GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoordsBuffer.capacity() * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)
//		// turn it "on"
//		GLES30.glEnableVertexAttribArray(this.attributes["a_texCoord"]!!)
//		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
//		GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
//	}


	//The main function executed on each image
	//A Load the source textures
	//B Convert them to an RGB texture for input to filtering
	//C Run the filter on the RGB texture
	//D Output the resulting texture to a file in a new thread
	fun render(yBytes: ByteArray, uBytes:ByteArray, vBytes: ByteArray, width:Int, height:Int, radius: Float, flip: Boolean = false) {
		makeCurrent()
		
		//A: New code, load Y U V data into textures to use in image conversion
		//TODO Should these  all be  the same size, or are u and v smaller than y?
 		val (yTxt,uTxt,vTxt) = GLUtils.setupTextures(yBytes,uBytes,vBytes, width, height)
		this.srcYTexture = yTxt
		this.srcUTexture = uTxt
		this.srcVTexture = vTxt
	     
		//B: Run conversion shader with these textures as the inputs to generate the output
		//texture filterSrcTexture  
		GLES30.glUseProgram(this.yuvConversionProgram)

 
		//C Apply the filter shader/s
	
		// Tell it to use our program
		GLES30.glUseProgram(this.gaussianProgram)
	
		// Set u_radius in the fragment shader
		GLES30.glUniform1f(this.uniforms["u_radius"]!!, radius)
	
		GLES30.glUniform1f(this.uniforms["u_flipY"]!!, if (flip) -1f else 1f) // Need to y flip for canvas
	
		// Tell the shader to get the texture from filterSrcTexture now in RGB format
		GLES30.glUniform1i(this.uniforms["u_image"]!!, 0)
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + 0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, filterSrcTexture)
	
		// Unbind any output frame buffer that may have been bound by other OpenGL programs (so we render to the default display)
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
	
		GLES30.glViewport(0, 0, width, height)
		GLES30.glClearColor(0f, 0f, 0f, 0f)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
	
		// Draw the rectangles we put in the vertex shader
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
	
		// This "draws" the result onto the surface we got from Flutter
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")
 
		//D: Output the changed texture to a file on a background thread


	}

	fun destroy() {

		//Delete textures
		val texts = intArrayOf(this.filterSrcTexture, this.srcYTexture, this.srcUTexture, this.srcVTexture)
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

	private fun makeCurrent() {
		EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
		GLUtils.checkEglError("eglMakeCurrent")
	}
}
 