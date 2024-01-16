package com.aardman.animatorfilter

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.view.Surface
import com.aardman.animatorfilter.GLUtils.checkEglError
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

	//Main texture
	private var workingTexture: Int = -1

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

	//Framebuffer
	private var resultsFBO: Int = -1

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
		setupTestQuadShader()
	}

	//VBO is reused for each processing step
	private fun setupCoordsVBO() {
		// Other buffer setup code remains the same...
		val texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
			put(texCoords)
			position(0)
		}

		val texCoordBuffer = IntArray(1)
		GLES30.glGenBuffers(1, texCoordBuffer, 0)
		checkEglError("generate texCoord buffer")
		GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBuffer[0])
		checkEglError("bind coord buffer")
		GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoordsBuffer.capacity() * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)
	}

	private fun setupTestQuadShader() {
		this.testQuadProgram = setupShaderProgram(TestVertexShader, RedFragmentShader)

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
		GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
	}

	//The main function executed on each image
	fun render(yBytes: ByteArray, uBytes:ByteArray, vBytes: ByteArray, width:Int, height:Int, radius: Float, flip: Boolean = false) {
		makeCurrent()
		displayTestQuad()
	}

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

public val TestVertexShader = """#version 300 es
     in vec2 a_texCoord; // Assuming you are using this attribute for vertex positions

     void main() {
         // Convert from 0->1 to -1->+1 (clipspace)
         vec2 clipSpace = a_texCoord * 2.0 - 1.0;

         // Set the position
         gl_Position = vec4(clipSpace, 0.0, 1.0);
     }
"""


//Just outputs red for every pixel
public var RedFragmentShader = """#version 300 es
		precision mediump float;
		  
		out vec4 outColor;
		
		void main() {  
            outColor = vec4(1f, 0f, 0f, 1.0);
		}
	"""
