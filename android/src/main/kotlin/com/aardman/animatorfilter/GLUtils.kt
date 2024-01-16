package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES30
import android.util.Log
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

object GLUtils {

	fun createProgram(vertexSource: String, fragmentSource: String): Int {
		val vertexShader = buildShader(GLES30.GL_VERTEX_SHADER, vertexSource)
		if (vertexShader == 0) {
			return 0
		}

		val fragmentShader = buildShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
		if (fragmentShader == 0) {
			return 0
		}

		val program = GLES30.glCreateProgram()
		if (program == 0) {
			return 0
		}
		checkEglError("create program ${fragmentSource.substring(0..40)}")

		GLES30.glAttachShader(program, vertexShader)
		GLES30.glAttachShader(program, fragmentShader)
		GLES30.glLinkProgram(program)

		return program
	}

	 fun setupShaderProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
		// Create and compile shaders, link program, etc.
		val program  = GLUtils.createProgram(
			vertexShaderSource, fragmentShaderSource
		)
        checkEglError("setUpShaderProgram")
		return program
	}

	fun updateTextureFromPlane(textureId: Int, planeData: ByteArray, width: Int, height: Int) {
		// Bind to the existing texture in OpenGL
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

		// Update the texture with new plane data
		GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(planeData))
	}

	fun updateTextures(yPlane: ByteArray, yTextureId: Int,
					   uPlane: ByteArray, uTextureId: Int,
					   vPlane: ByteArray, vTextureId: Int,
					   width: Int, height: Int)  {
		val yTexture = updateTextureFromPlane(yTextureId, yPlane, width, height)
		val uTexture = updateTextureFromPlane(uTextureId,uPlane, width / 2, height / 2) // Assuming chroma planes are half the size of luma for YUV420_888 in Android
		val vTexture = updateTextureFromPlane(vTextureId,vPlane, width / 2, height / 2)
 	}

	fun createTexture(width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA, format: Int = GLES30.GL_RGBA, type: Int = GLES30.GL_UNSIGNED_BYTE): Int {
		val texture = IntArray(1)
		GLES30.glGenTextures(1, texture, 0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
	
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
	
		// Initialize the texture with no data.
		val mipLevel = 0 // the largest mip
		val border = 0
		GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, null)
	
		GLES30.glGetError() // Check for OpenGL errors.
	
		return texture[0]
	}

	fun checkEglError(msg: String) {
		val error = EGL14.eglGetError()
		if (error != EGL14.EGL_SUCCESS) {
			throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
		}
		else {
			print(msg + "\n")
		}
	}

	private fun buildShader(type: Int, shaderSource: String): Int {
		val shader = GLES30.glCreateShader(type)
		if (shader == 0) {
			return 0
		}

		GLES30.glShaderSource(shader, shaderSource)
		GLES30.glCompileShader(shader)
		checkEglError("compile shader")

		val status = IntArray(1)
		GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
		if (status[0] == 0) {
			Log.e("CPXGLUtils", GLES30.glGetShaderInfoLog(shader))
			GLES30.glDeleteShader(shader)
			return 0
		}

		return shader
	}

	// Sets up a vertex array for a given shader program and attribute name
	//NB: helper functions should not change OpenGL state for clarity
    fun setupVertexArrayForProgram(programId: Int): Int {
		val vao = IntArray(1)
		GLES30.glGenVertexArrays(1, vao, 0)
		checkEglError("generate vertex arrays")
		return vao[0]
	}

}
