	package com.aardman.animatorfilter

	import android.graphics.Bitmap
	import android.opengl.EGL14
	import android.opengl.GLES30
	import android.util.Log
	import java.nio.ByteBuffer
	import java.nio.ByteOrder


	/**
	 * Helper functions should leave the openGL statemachine state as it was prior to calling
	 * the method ie: no side effects remain on the state such as bindings.
	 */
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

		fun setupFramebuffer(texture: Int): Int {

			// Create a new framebuffer object
			val frameBuffer = IntArray(1)
			GLES30.glGenFramebuffers(1, frameBuffer, 0)
			val newFrameBuffer = frameBuffer[0]

			// Check if the framebuffer was created successfully
			if (newFrameBuffer <= 0) {
				throw RuntimeException("Failed to create a new framebuffer object.")
			}

			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, newFrameBuffer)

			//load the texture to the framebuffer for use as a rendering target
			GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)

			// Check if the framebuffer is complete
			if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
				throw RuntimeException("Framebuffer is not complete.")
			}

			// Unbind the framebuffer
			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

			return newFrameBuffer
		}

		//TODO: check
		fun packYUV(inputArray: ByteArray, rowStride: Int, pixelStride: Int): ByteArray {
			val outputArray = ByteArray(inputArray.size / pixelStride)
			var outputIndex = 0

			for (i in inputArray.indices step rowStride) {
				for (j in i until i + rowStride step pixelStride) {
					outputArray[outputIndex++] = inputArray[j]
				}
			}

			return outputArray
		}

		fun updateTextureFromPlane(textureId: Int, planeData: ByteArray, width: Int, height: Int) {

			// Bind to the existing texture in OpenGL, using default texture unit 0
			// as we do not need more than one texture unit at a time
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
			checkEglError("bindTexture")

			// Update the texture with new planar data
			GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(planeData))
			checkEglError("load texture")
		}

		/*
		 * Android camera image U, V, planes are sampled with alternate pixels, hence the need to pack the data into a 1/2 size array before filling the textures.
		 */
		fun updateYUVTextures(yPlane: ByteArray, yTextureId: Int,
							  uPlane: ByteArray, uTextureId: Int,
							  vPlane: ByteArray, vTextureId: Int,
							  width: Int, height: Int)  {

			val sampledUPlane = packYUV(uPlane, width, 2)
			val sampledVPlane = packYUV(vPlane, width, 2)

			val yTexture = updateTextureFromPlane(yTextureId, yPlane, width, height)
			val uTexture = updateTextureFromPlane(uTextureId, sampledUPlane, width / 2, height / 2) // Assuming chroma planes are half the size of luma for YUV420_888 in Android
			val vTexture = updateTextureFromPlane(vTextureId, sampledVPlane, width / 2, height / 2)
		}

		fun createTexture(width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA, format: Int = GLES30.GL_RGBA, type: Int = GLES30.GL_UNSIGNED_BYTE): Int {
			val texture = IntArray(1)
			GLES30.glGenTextures(1, texture, 0)
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])

			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)

			// Initialize the texture with no data to set mip level
			val mipLevel = 0 // the largest mip
			val border = 0
			GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, null)

			GLES30.glGetError() // Check for OpenGL errors.

			return texture[0]
		}

		fun createTextureFromBitmap(data: Bitmap?, width: Int, height: Int, internalFormat: Int = GLES30.GL_RGBA, format: Int = GLES30.GL_RGBA, type: Int = GLES30.GL_UNSIGNED_BYTE): Int {
			val texture = IntArray(1)
			GLES30.glGenTextures(1, texture, 0)
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])

			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
			GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)

			// Upload the image into the texture.
			val mipLevel = 0 // the largest mip
			val border = 0

			if (data != null) {
				val buffer = ByteBuffer.allocate(data.byteCount)
				data.copyPixelsToBuffer(buffer)
				buffer.position(0)
				GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, buffer)
				checkEglError("uploading bitmap to new texture")
			} else {
				GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, mipLevel, internalFormat, width, height, border, format, type, null)
				GLES30.glGetError()
			}

			return texture[0]
		}

		fun checkEglError(msg: String) {
			val error = EGL14.eglGetError()
			if (error != EGL14.EGL_SUCCESS) {
				throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
			}
			//debug all openGL calls
	//		else {
	//			print("EGL " + msg + "\n")
	//		}
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
		fun makeVAO(): Int {
			val vao = IntArray(1)
			GLES30.glGenVertexArrays(1, vao, 0)
			checkEglError("generate vertex arrays")
			return vao[0]
		}

		fun getBitmapFromFBO(textureWidth: Int, textureHeight: Int, workingFBO: Int): Bitmap {
			// Bind the framebuffer to which the texture is attached
			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, workingFBO)

			// Prepare a buffer to store the pixels
			val buffer = ByteBuffer.allocateDirect(textureWidth * textureHeight * 4)
			buffer.order(ByteOrder.nativeOrder())

			// Read pixels from the framebuffer into the buffer
			GLES30.glReadPixels(0, 0, textureWidth, textureHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

			// Create a bitmap from the buffer
			val bitmap = Bitmap.createBitmap(textureWidth, textureHeight, Bitmap.Config.ARGB_8888)
			buffer.rewind() // Rewind the buffer to read from the beginning
			bitmap.copyPixelsFromBuffer(buffer)

			// Unbind the framebuffer returning to the calling stat
			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

			return bitmap
		}

		//Debug methods

		fun  checkVBOIsBound(vboId: Int): Boolean  {
			val buffer = IntArray(1)
			GLES30.glGetIntegerv(GLES30.GL_ARRAY_BUFFER_BINDING, buffer, 0)
			val currentVBO = buffer[0] // This will be the ID of the currently bound VBO, or 0 if none is bound.
			return currentVBO == vboId
		}

		fun  checkVAOIsBound(vaoId: Int): Boolean {
			val array = IntArray(1)
			GLES30.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING, array, 0)
			val currentVAO = array[0]
			return currentVAO == vaoId
		}

		fun checkTexturePixels(textureId: Int, textureWidth: Int, textureHeight: Int, format:Int = GLES30.GL_RGBA, bytesToCheck:ByteArray?):Pair<ByteArray, ByteArray>  {
			// Create a framebuffer and attach the texture to it
			val frameBuffer = IntArray(1)
			GLES30.glGenFramebuffers(1, frameBuffer, 0)
			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBuffer[0])

			GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
			GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
			GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textureId, 0)

			// Check for framebuffer completeness
			if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
				throw RuntimeException("Framebuffer not complete")
			}

			// Prepare a buffer to store the pixels
			val buffer = ByteBuffer.allocateDirect(textureWidth * textureHeight)
			buffer.order(ByteOrder.nativeOrder())

			// Read pixels from the framebuffer into the buffer
			GLES30.glReadPixels(0, 0, textureWidth, textureHeight, format, GLES30.GL_UNSIGNED_BYTE, buffer)

			GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
			GLES30.glDeleteFramebuffers(1, frameBuffer, 0)

			if(bytesToCheck == null) {
				val res = containsNonZeroValue(buffer)
				val outputArray = ByteArray(buffer.remaining())
				buffer.get(outputArray)
				return Pair(ByteArray(0),  outputArray)
			}
			else {
				val outputArray = ByteArray(buffer.remaining())
				buffer.get(outputArray)
				return Pair(bytesToCheck, outputArray)
			}
		}

		fun containsNonZeroValue(buffer: ByteBuffer): Boolean {
			while (buffer.hasRemaining()) {
				if (buffer.get().toInt() != 0) {
					return true
				}
			}
			return false
		}

		fun returnBufferSamples(byteArray: ByteArray, buffer: ByteBuffer): Pair<ByteArray, ByteArray> {
			val minLength = minOf(byteArray.size, buffer.remaining(), 50)
			val first50BytesArray = byteArray.copyOfRange(0, minLength)
			val first50BytesBuffer = ByteArray(minLength)
			buffer.rewind()
			buffer.get(first50BytesBuffer, 0, minLength)
			return Pair(first50BytesArray, first50BytesBuffer)
		}

		//Generic  helper functions
		public fun padByteArray(b1: ByteArray, padding: Int): ByteArray {
			val paddedArray = ByteArray(b1.size + padding)
			System.arraycopy(b1, 0, paddedArray, 0, b1.size)
			// The remaining bytes in paddedArray are automatically initialized to zero
			return paddedArray
		}

		/**
		 * Pad byte u and v byte arrays to 1/2 yByte array length if required padding
		 * with extra zero values if needed.
		 */
		public fun padArrays(yBytes: ByteArray, uBytes: ByteArray, vBytes: ByteArray): Pair<ByteArray, ByteArray>
		{
			val requiredUVSize = yBytes.size / 2
			var paddedUBytes: ByteArray? = null
			var paddedVBytes: ByteArray? = null

			if (uBytes.size < requiredUVSize) {
				paddedUBytes = padByteArray(uBytes, requiredUVSize - uBytes.size )
			}
			if (vBytes.size < requiredUVSize) {
				paddedVBytes = padByteArray(vBytes, requiredUVSize - vBytes.size )
			}

			val retUByte = if (paddedUBytes == null) uBytes else paddedUBytes
			val retVByte = if (paddedVBytes == null) vBytes else paddedVBytes

			return Pair(retUByte, retVByte)
		}


	}
