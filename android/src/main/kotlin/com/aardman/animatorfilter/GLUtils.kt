package com.aardman.animatorfilter

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES30
import android.util.Log
import java.nio.Buffer
import java.nio.ByteBuffer

object GLUtils {


	var VertexShaderSource = """#version 300 es
	// vertex value between 0-1
	in vec2 a_texCoord;

	uniform float u_flipY;

	// Used to pass the texture coordinates to the fragment shader
	out vec2 v_texCoord;

	// all shaders have a main function
	void main() {
		// convert from 0->1 to 0->2
		vec2 zeroToTwo = a_texCoord * 2.0;

		// convert from 0->2 to -1->+1 (clipspace)
		vec2 clipSpace = zeroToTwo - 1.0;

		gl_Position = vec4(clipSpace * vec2(1, u_flipY), 0, 1);

		// pass the texCoord to the fragment shader
		// The GPU will interpolate this value between points.
		v_texCoord = a_texCoord;
	}	"""

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

		GLES30.glAttachShader(program, vertexShader)
		GLES30.glAttachShader(program, fragmentShader)
		GLES30.glLinkProgram(program)

		return program
	}

	fun createTextureFromPlane(planeData: ByteArray, width: Int, height: Int): Int {
		val textureHandle = IntArray(1)
	
		// Generate a texture ID
		GLES20.glGenTextures(1, textureHandle, 0)
		val textureId = textureHandle[0]
	
		// Bind to the texture in OpenGL
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
	
		// Set filtering
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
	
		// Load the plane data into the texture
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(planeData))
	
		return textureId
	}
	
	fun setupTextures(yPlane: ByteArray, uPlane: ByteArray, vPlane: ByteArray, width: Int, height: Int): Triple<Int, Int, Int> {
		val yTexture = createTextureFromPlane(yPlane, width, height)
		val uTexture = createTextureFromPlane(uPlane, width / 2, height / 2) // Assuming chroma planes are half the size of luma
		val vTexture = createTextureFromPlane(vPlane, width / 2, height / 2)
	
		return Triple(yTexture, uTexture, vTexture)
	} 

	fun createTextureWithBitmap(data: Bitmap?, width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA, format: Int = GLES30.GL_RGBA, type: Int = GLES30.GL_UNSIGNED_BYTE): Int {
		val texture = IntArray(1)
		GLES30.glGenTextures(1, texture, 0)
		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])

		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
		GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)

		uploadBitmapToTexture(texture[0], data,width,height,internalFormat,format,type)

		return texture[0]
	}

	fun uploadBitmapToTexture(textureId: Int, data: Bitmap?, width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA, format: Int = GLES30.GL_RGBA, type: Int = GLES30.GL_UNSIGNED_BYTE) {

		GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

		// Upload the image into the texture.
		val mipLevel = 0 // the largest mip
		val border = 0

		if (data != null) {
			val buffer = ByteBuffer.allocate(data.byteCount)
			data.copyPixelsToBuffer(buffer)
			buffer.position(0)
			GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, buffer)
		} else {
			GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, null)
			GLES30.glGetError()
		}
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
	}

	private fun buildShader(type: Int, shaderSource: String): Int {
		val shader = GLES30.glCreateShader(type)
		if (shader == 0) {
			return 0
		}

		GLES30.glShaderSource(shader, shaderSource)
		GLES30.glCompileShader(shader)

		val status = IntArray(1)
		GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
		if (status[0] == 0) {
			Log.e("CPXGLUtils", GLES30.glGetShaderInfoLog(shader))
			GLES30.glDeleteShader(shader)
			return 0
		}

		return shader
	}
}
