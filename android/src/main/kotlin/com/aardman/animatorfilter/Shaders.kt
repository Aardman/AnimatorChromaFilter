package com.aardman.animatorfilter

public val SingleTextureVertexShader = """#version 300 es
	// vertex value between 0-1
	layout(location =  0) in vec2 a_texCoord;

	uniform float u_flipY;
 
	out vec2 v_texCoord;
 
	void main() { 
		// convert from 0->1 to -1->+1 (clipspace)
		vec2 clipSpace = a_texCoord * 2.0; - 1.0;
  
		gl_Position = vec4(clipSpace * vec2(1, u_flipY), 0, 1);

		// pass the texCoord to the fragment shader straight through
		// The GPU will interpolate this value between points.
		v_texCoord = a_texCoord;
}	
"""

public val DualTextureVertexShader = """#version 300 es 

layout(location =  0) in vec4 position;
layout(location =  1) in vec4 inputTextureCoordinate;
layout(location =  2) in vec4 inputTextureCoordinate2;

out vec2 textureCoordinate;
out vec2 textureCoordinate2;

void main() {
    gl_Position = position;
    textureCoordinate = inputTextureCoordinate.xy;
    textureCoordinate2 = inputTextureCoordinate2.xy;
}	
"""

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

public val chromaKeyFilter  = """#version 300 es
            precision highp float; 
    
            layout(location =  0) in vec2 textureCoordinate;
            layout(location =  1) in vec2 textureCoordinate2;
            
            uniform float thresholdSensitivity;
            uniform float smoothing;
            uniform vec3 colorToReplace;
            uniform sampler2D inputImageTexture;
             uniform sampler2D backgroundImageTexture;
              
		     out vec4 outColour;

             void main()
             {
                 vec4 textureColor = texture(inputImageTexture, textureCoordinate);
                 vec4 textureColor2 = texture(backgroundImageTexture, textureCoordinate2);
                 
                 float maskY = 0.2989 * colorToReplace.r + 0.5866 * colorToReplace.g + 0.1145 * colorToReplace.b;
                 float maskCr = 0.7132 * (colorToReplace.r - maskY);
                 float maskCb = 0.5647 * (colorToReplace.b - maskY);
                 
                 float Y = 0.2989 * textureColor.r + 0.5866 * textureColor.g + 0.1145 * textureColor.b;
                 float Cr = 0.7132 * (textureColor.r - Y);
                 float Cb = 0.5647 * (textureColor.b - Y);
                 
                 float blendValue = 1.0 - smoothstep(thresholdSensitivity, thresholdSensitivity + smoothing, distance(vec2(Cr, Cb), vec2(maskCr, maskCb)));
                 outColour = mix(textureColor, textureColor2, blendValue);
             }
"""

//Shader converts data from input textures into RGB format
public val conversionShader = """#version 300 es
		precision mediump float;
 
		in vec2 v_texCoord;
		
		uniform sampler2D yTexture;
		uniform sampler2D uTexture;
		uniform sampler2D vTexture;
		
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

//Shader converts data from input textures into RGB format
public val conversionDebugShader = """#version 300 es
		precision mediump float;
 
		in vec2 v_texCoord;
		
		uniform sampler2D yTexture;
		uniform sampler2D uTexture;
		uniform sampler2D vTexture;
		
		out vec4 outColor;
		
		void main() {
			float y = texture(yTexture, v_texCoord).r;

			// Adjust the texture coordinates for chroma subsampling
			vec2 chromaTexCoord = v_texCoord / 2.0;

			float u = texture(uTexture, chromaTexCoord).r - 0.5;
			float v = texture(vTexture, chromaTexCoord).r - 0.5;
  
			outColor = vec4(r, u, v, 1.0);
		}
	"""



public val displayShader = """#version 300 es
		precision mediump float;
		
	    in vec2 v_texCoord;  

        uniform sampler2D workingTexture; 
		
		out vec4 outColor;
		
		void main() { 
		    vec4 texColor = texture(workingTexture, v_texCoord);
            outColor = vec4(texColor.r, texColor.g, texColor.b, 1.0); 
		}
	"""


//Just outputs red for every pixel
public var DebugFragmentShader = """#version 300 es
		precision mediump float;

        uniform vec2 iResolution;
     
        void main() {
          vec2 uv = vec2(gl_FragCoord.xy / iResolution.xy);
        
          //optionally fix aspect ratio
          uv.x *= iResolution.x / iResolution.y;
        
          //18x18 checkered background
          float gray = mix(0.8, 1.0, checker(uv, 18.0));
          
          gl_FragColor.rgb = vec3(gray);
          gl_FragColor.a = 1.0;
        }
        
	"""


public val BaseVertexShader = """#version 300 es
     layout(location =  0) in vec2 a_texCoord; // Assuming you are using this attribute for vertex positions

     void main() {
         // Convert from 0->1 to -1->+1 (clipspace)
         vec2 clipSpace = a_texCoord * 2.0 - 1.0;

         // Set the position
         gl_Position = vec4(clipSpace, 0.0, 1.0);
     }
"""

//
public var SolidFragmentShader = """#version 300 es
		precision mediump float;

        uniform float r;
        uniform float g;
        uniform float b;

		out vec4 outColour;
 		
		void main() {  
            outColour = vec4(r, g, b, 1.0);
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
