package com.aardman.animatorfilter

public val VertexShaderSource = """#version 300 es
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


//Demo filter to be replaced with ChromaKey filter
public val gaussianShader = """#version 300 es
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
public val conversionShader = """#version 300 es
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

public val displayShader = """#version 300 es
		precision mediump float;
		
		uniform sampler2D workingTexture; 
		in vec2 v_texCoord;
		
		out vec4 outColor;
		
		void main() { 
		    vec4 texColor = texture(workingTexture, v_texCoord);
            outColor = vec4(texColor.r, texColor.g, texColor.b, 1.0);
		}
	"""


//Just outputs red for every pixel
public var DebugFragmentShader = """#version 300 es
		precision mediump float;
		  
		out vec4 outColor;
		
		void main() {  
            outColor = vec4(1f, 0f, 0f, 1.0);
		}
	"""


public val BaseVertexShader = """#version 300 es
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
