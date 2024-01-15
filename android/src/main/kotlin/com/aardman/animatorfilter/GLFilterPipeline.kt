package com.aardman.animatorfilter

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLES30.glBindVertexArray
import android.view.Surface
import com.aardman.animatorfilter.GLUtils.setupShaderProgram

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

	//Globals
	private var attributes: MutableMap<String, Int> = hashMapOf()
	private var uniforms: MutableMap<String, Int> = hashMapOf()

	//Main texture
	private var workingTexture: Int = -1

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

	//Framebuffer
	private var resultsFBO: Int = -1

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
		in vec2 v_texCoord;
		
		out vec4 outColor;
		
		void main() {
			float y = texture(yTexture, v_texCoord).r;

			// Adjust the texture coordinates for chroma subsampling
			vec2 chromaTexCoord = v_texCoord / 2.0;

			float u = texture(uTexture, chromaTexCoord).r - 0.5;
			float v = texture(vTexture, chromaTexCoord).r - 0.5;

			float r = y + 1.403 * v;
			float g = y - 0.344 * u - 0.714 * v;
			float b = y + 1.770 * u;
		 
			outColor = vec4(r, g, b, 1.0);
		}
	"""

	private val displayShader = """#version 300 es
		precision mediump float;
		
		uniform sampler2D displayTexture; 
		in vec2 v_texCoord;
		
		out vec4 outColor;
		
		void main() { 
		    vec4 texColor = texture(displayTexture, v_texCoord);
        	outColor = vec4(texColor.r, texColor.g, texColor.b, 1.0);
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
		setupTextures()
		setupResultsFBO()
	    setupConverter()
		setupFilter()
		setupDisplayShader()
	}

	private fun setupTextures(){
		// Create the texture that will hold the source image
		workingTexture = GLUtils.createTexture(textureWidth, textureHeight)
		srcYTexture    = GLUtils.createTexture(textureWidth, textureHeight)
		srcUTexture    = GLUtils.createTexture(textureWidth/2, textureHeight/2)
		srcVTexture    = GLUtils.createTexture(textureWidth/2, textureHeight/2)
	}

	//Set up the framebuffer that will be used to render the results
	//Pre-conditions: Textures have been created
	private fun setupResultsFBO() {
		// Create a new framebuffer object
		val frameBuffer = IntArray(1)
		GLES30.glGenFramebuffers(1, frameBuffer, 0)
		resultsFBO = frameBuffer[0]

		// Check if the framebuffer was created successfully
		if (resultsFBO <= 0) {
			throw RuntimeException("Failed to create a new framebuffer object.")
		}

		//load the working texture to the framebuffer
		GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, workingTexture, 0)

		// Bind the framebuffer
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resultsFBO)
	}

	private fun setupConverter() {
		this.yuvConversionProgram = setupShaderProgram(GLUtils.VertexShaderSource, conversionShader)

		// Get vertex shader attributes
		this.attributes["c_texCoord"] = GLES30.glGetAttribLocation(this.yuvConversionProgram, "a_texCoord")

		// ... other specific setups like uniforms
		// Find uniforms
		this.uniforms["yTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "yTexture")
		this.uniforms["vTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "vTexture")
		this.uniforms["uTexture"] = GLES30.glGetUniformLocation(this.yuvConversionProgram, "uTexture")

		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["c_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["c_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)

		this.yuvConversionVAO = GLUtils.setupVertexArrayForProgram(yuvConversionProgram, "a_texCoords", texCoords)
	}

	private fun setupDisplayShader() {
		this.displayProgram  = setupShaderProgram(GLUtils.VertexShaderSource, displayShader)
		// Get vertex shader attributes, this is the same for all shaders
		this.attributes["d_texCoord"] = GLES30.glGetAttribLocation(this.displayProgram, "a_texCoord")
		// Find uniforms
		this.uniforms["workingTexture"] = GLES30.glGetUniformLocation(this.displayProgram, "displayTexture")
		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["d_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["d_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)

		this.displayVAO = GLUtils.setupVertexArrayForProgram(displayProgram, "a_texCoords", texCoords)
	}

	private fun setupFilter() {
		this.filterProgram = setupShaderProgram(GLUtils.VertexShaderSource, gaussianShader)

		// ... other specific setups like uniforms
		// Get vertex shader attributes
	    this.attributes["f_texCoord"] = GLES30.glGetAttribLocation(this.filterProgram, "a_texCoord")
		// Find uniforms
		this.uniforms["u_flipY"] = GLES30.glGetUniformLocation(this.filterProgram, "u_flipY")
		this.uniforms["u_image"] = GLES30.glGetUniformLocation(this.filterProgram, "u_image")
		this.uniforms["u_radius"] = GLES30.glGetUniformLocation(this.filterProgram, "u_radius")

		//Enable related attributes (might be in a more generic location, but this sequence is required
		GLES30.glEnableVertexAttribArray(this.attributes["f_texCoord"]!!)
		// Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
		GLES30.glVertexAttribPointer(this.attributes["f_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)

		this.filterVAO = GLUtils.setupVertexArrayForProgram(filterProgram,"a_texCoords", texCoords)
	}

	//The main function executed on each image
	//A Load the source textures
	//B Convert them to an RGB texture for input to filtering
	//C Run the filters on the RGB texture
	//D Output the resulting texture to a file in a new thread
	fun render(yBytes: ByteArray, uBytes:ByteArray, vBytes: ByteArray, width:Int, height:Int, radius: Float, flip: Boolean = false) {

		makeCurrent()

		// *** A ****
 	    //Load Y U V data into existing textures to use in image conversion
		GLUtils.updateTextures(yBytes,srcYTexture, uBytes, srcUTexture, vBytes, srcVTexture, width, height)

		// *** B ****
	    convertYUV(width, height)
		GLUtils.checkEglError("convertYUV")
		//The texture workingTexture now contains the results of the conversion

		// *** C ****
		//applyFilters()

		// *** E ****
		//Draw the filterSrcTexture to the screen
		displayOutputTexture()

		//D: Output the changed texture to a file on a background thread
        //saveTextureToFile()
	}

	//Converts the srcYUV textures to RGB and stores in workingTexture
	private fun convertYUV(width: Int, height: Int) {

		//Use conversion program and set parameters
		GLES30.glUseProgram(this.yuvConversionProgram)
		GLES30.glUniform1i(this.uniforms["yTexture"]!!, this.srcYTexture)
		GLES30.glUniform1i(this.uniforms["uTexture"]!!, this.srcUTexture)
		GLES30.glUniform1i(this.uniforms["vTexture"]!!, this.srcVTexture)

		//Set the viewport
		GLES30.glViewport(0, 0, width, height)
		GLES30.glClearColor(1f, 0f, 0f, 0f)
		GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

		//Bind the VAO
		glBindVertexArray(yuvConversionVAO)

		//Draw to the currently bound texture using the program
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		//Unbind vao
		glBindVertexArray(0)
	}

	//Displays workingTexture to the screen
	private fun displayOutputTexture() {

		// Bind the default framebuffer to render to the screen
		GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

		// Set up the viewport, shader program, and other state as needed for rendering
		GLES30.glViewport(0, 0, textureWidth, textureHeight)
		GLES30.glUseProgram(displayProgram)

		// Bind workingTexture to a texture unit and set the corresponding uniform in the shader
		GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, workingTexture)
		GLES30.glUniform1i(uniforms["workingTexture"]!!, 0)  // Assuming a uniform for the texture in the shader

		// Bind the VAO that contains the vertex data for the quad
		GLES30.glBindVertexArray(displayVAO)

		// Draw the textured quad to the screen
		GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)

		// Unbind the VAO
		GLES30.glBindVertexArray(0)

		// Swap buffers to display the result
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
		GLUtils.checkEglError("eglSwapBuffers")

	}

	private fun applyFilters(){
		/*
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

     */
	}


	private fun saveTextureToFile() {
		//TBD
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
		val texts = intArrayOf(this.workingTexture, this.srcYTexture, this.srcUTexture, this.srcVTexture)
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
 