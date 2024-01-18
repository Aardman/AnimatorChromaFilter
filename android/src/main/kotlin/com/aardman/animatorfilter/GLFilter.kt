package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.view.Surface
import com.aardman.animatorfilter.GLUtils.checkEglError
import com.aardman.animatorfilter.GLUtils.createProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GLFilter(private val outSurface: Surface, private val textureWidth: Int, private val textureHeight: Int) {

    //EGL
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLContext = EGL14.EGL_NO_CONTEXT
    private var mEGLSurface = EGL14.EGL_NO_SURFACE

    //Test Quad - doesn't work on all android variants
    private var testQuadProgram: Int = -1
    private var testQuadvao: IntArray = IntArray(1)

    //Filter
    private var filterProgram: Int = -1
    private var filterProgramVAO: IntArray = IntArray(1)

    //Temporary sample images
    private var backgroundImg: Bitmap? = null
    private var cameraImg: Bitmap? = null

    //Textures
    private var backgroundTexture: Int = -1

    //Globals
    private var attributes: MutableMap<String, Int> = hashMapOf()
    private var uniforms: MutableMap<String, Int> = hashMapOf()

    //Assume all inputs and textures are the same sizes based on initialisation via camera frame
    private var width:Int = -1
    private var height:Int = -1

    // 2D quad coordinates for the vertex shader, we use 2 triangles that will cover
    // the entire view
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

    init {
        eglSetup()
        makeCurrent()
        setupOpenGLObjects()
    }

    private fun eglSetup() { // Create EGL display that will output to the given outSurface
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
            EGL14.EGL_COLOR_BUFFER_TYPE,
            EGL14.EGL_RGB_BUFFER,
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_LEVEL,
            0,
            EGL14.EGL_RENDERABLE_TYPE, /* EGL14.EGL_OPENGL_ES2_BIT,*/
            EGLExt.EGL_OPENGL_ES3_BIT_KHR,
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
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE
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

    private fun loadImageAndConvertToRGBABitmap(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun setupOpenGLObjects() {
        createTextures()
        setupUpGaussianFilterProgram()
    }

    private fun createTextures() {
        backgroundTexture = GLUtils.createTextureFromBitmap(backgroundImg, width, height)
    }

    private fun setupUpGaussianFilterProgram() {
        this.filterProgram = createProgram(VertexShaderSource, gaussianShader)
        checkEglError("Create filter program")
        // Get vertex shader attributes
        this.attributes["a_texCoord"] = GLES30.glGetAttribLocation(this.filterProgram, "a_texCoord")

        // Find uniforms
        this.uniforms["u_flipY"] = GLES30.glGetUniformLocation(this.filterProgram, "u_flipY")
        this.uniforms["u_image"] = GLES30.glGetUniformLocation(this.filterProgram, "u_image")
        this.uniforms["u_radius"] = GLES30.glGetUniformLocation(this.filterProgram, "u_radius")

        // Create a vertex array object (attribute state)
        GLES30.glGenVertexArrays(1, this.filterProgramVAO, 0)
        // and make it the one we're currently working with
        GLES30.glBindVertexArray(this.filterProgramVAO[0])

        val texCoordsBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texCoordsBuffer.put(texCoords)
        texCoordsBuffer.position(0)

        // Create a buffer to hold the texCoords
        val texCoordBuffer = IntArray(1)
        GLES30.glGenBuffers(1, texCoordBuffer, 0)
        // Bind it to ARRAY_BUFFER (used for Vertex attributes)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBuffer[0])
        // upload the text corrds into the buffer
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, texCoordsBuffer.capacity() * 4, texCoordsBuffer, GLES30.GL_STATIC_DRAW)
        // turn it "on"
        GLES30.glEnableVertexAttribArray(this.attributes["a_texCoord"]!!)
        // Describe how to pull data out of the buffer, take 2 items per iteration (x and y)
        GLES30.glVertexAttribPointer(this.attributes["a_texCoord"]!!, 2, GLES30.GL_FLOAT, false, 0, 0)
    }

    fun draw(radius: Float, flip: Boolean = false) {
        makeCurrent()

        // Tell it to use our program
        GLES30.glUseProgram(this.filterProgram)

        // set u_radius in fragment shader
        GLES30.glUniform1f(this.uniforms["u_radius"]!!, radius)

        GLES30.glUniform1f(this.uniforms["u_flipY"]!!, if (flip) -1f else 1f) // need to y flip for canvas

        // Tell the shader to get the texture from texture unit 0
        GLES30.glUniform1i(this.uniforms["u_image"]!!, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backgroundTexture)

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

    //API

    //Load background image
    //TODO: remove this double init when rendering from the camera
    public fun setBackgroundImage(bitmap: Bitmap) {
        if (backgroundImg != null)  {
            cameraImg =  bitmap
        }
        else {
            backgroundImg = bitmap
        }
    }

    //The main function executed on each image
    public fun render(yBytes: ByteArray, uBytes: ByteArray, vBytes: ByteArray, width: Int, height: Int, radius: Float, flip: Boolean = false) {
        makeCurrent()
        draw(1f, true)
    }

    //EGL Lifecycle

    private fun makeCurrent() {
        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
        GLUtils.checkEglError("eglMakeCurrent")
    }

    fun destroy() {

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
