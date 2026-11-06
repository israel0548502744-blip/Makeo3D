package com.triposr.mobile

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** OpenGL ES 3 viewer: drag to rotate, pinch to zoom, two fingers to pan. */
class MeshView(context: Context) : GLSurfaceView(context) {
    private val renderer = MeshRenderer()
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            renderer.distance = (renderer.distance / d.scaleFactor).coerceIn(0.6f, 8f)
            requestRender()
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(false)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private var lastMesh: Mesh? = null

    fun setMesh(mesh: Mesh?) {
        if (mesh === lastMesh) return
        lastMesh = mesh
        queueEvent { renderer.upload(mesh) }
        requestRender()
    }

    fun setOptions(colors: Boolean, wireframe: Boolean, lighting: Boolean, grid: Boolean, autoRotate: Boolean) {
        renderer.colors = colors
        renderer.wireframe = wireframe
        renderer.lighting = lighting
        renderer.grid = grid
        renderer.autoRotate = autoRotate
        renderMode = if (autoRotate) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    fun resetView() {
        renderer.yaw = 30f; renderer.pitch = 15f; renderer.distance = 2.4f
        renderer.panX = 0f; renderer.panY = 0f
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        val x = (0 until e.pointerCount).map { e.getX(it) }.average().toFloat()
        val y = (0 until e.pointerCount).map { e.getY(it) }.average().toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> { lastX = x; lastY = y }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1) {
                    renderer.yaw += (x - lastX) * 0.4f
                    renderer.pitch = (renderer.pitch + (y - lastY) * 0.4f).coerceIn(-89f, 89f)
                } else {
                    val k = renderer.distance / height
                    renderer.panX += (x - lastX) * k
                    renderer.panY -= (y - lastY) * k
                }
                lastX = x; lastY = y
                requestRender()
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}

internal class MeshRenderer : GLSurfaceView.Renderer {
    @Volatile var yaw = 30f
    @Volatile var pitch = 15f
    @Volatile var distance = 2.4f
    @Volatile var panX = 0f
    @Volatile var panY = 0f
    @Volatile var colors = true
    @Volatile var wireframe = false
    @Volatile var lighting = true
    @Volatile var grid = true
    @Volatile var autoRotate = false

    private var program = 0
    private val vbo = IntArray(7) // pos, normal, color, tri idx, line idx, grid, uv
    private val tex = IntArray(1)
    private var textured = false
    private var indexCount = 0
    private var lineCount = 0
    private var gridCount = 0
    private var floorY = -0.5f
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var current: Mesh? = null

    fun upload(mesh: Mesh?) {
        current = mesh
        indexCount = 0
        lineCount = 0
        textured = false
        if (mesh == null || program == 0) return
        fun fbuf(a: FloatArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).position(0)
        fun ibuf(a: IntArray) = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(a).position(0)
        // A baked texture needs one vertex per (vertex, UV) pair; otherwise draw the shared vertices.
        val t = mesh.texturedArrays()
        val indices = t?.indices ?: mesh.indices
        val arrays = if (t != null) listOf(0 to t.positions, 1 to t.normals, 6 to t.uvs)
            else listOf(0 to mesh.positions, 1 to mesh.normals, 2 to mesh.colors)
        for ((slot, data) in arrays) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[slot])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, fbuf(data), GLES30.GL_STATIC_DRAW)
        }
        if (t != null) {
            val texture = mesh.texture!!
            val rgba = ByteBuffer.allocateDirect(texture.pixels.size * 4).order(ByteOrder.nativeOrder())
            for (c in texture.pixels) { rgba.put((c shr 16).toByte()); rgba.put((c shr 8).toByte()); rgba.put(c.toByte()); rgba.put(-1) }
            rgba.position(0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, texture.size, texture.size, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgba)
            // No mipmaps: lower levels would blend neighbouring atlas cells.
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            textured = true
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[3])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, ibuf(indices), GLES30.GL_STATIC_DRAW)
        indexCount = indices.size

        val lines = IntArray(indices.size * 2)
        for (tri in 0 until indices.size / 3) for (e in 0..2) {
            lines[tri * 6 + e * 2] = indices[tri * 3 + e]
            lines[tri * 6 + e * 2 + 1] = indices[tri * 3 + (e + 1) % 3]
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[4])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, lines.size * 4, ibuf(lines), GLES30.GL_STATIC_DRAW)
        lineCount = lines.size

        floorY = Float.MAX_VALUE
        for (v in 0 until mesh.vertexCount) floorY = minOf(floorY, mesh.positions[v * 3 + 1])
        buildGrid()
    }

    private fun buildGrid() {
        val g = ArrayList<Float>()
        val n = 10; val s = 0.1f
        for (i in -n..n) {
            g += listOf(i * s, floorY, -n * s, i * s, floorY, n * s)
            g += listOf(-n * s, floorY, i * s, n * s, floorY, i * s)
        }
        gridCount = g.size / 3
        val arr = g.toFloatArray()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[5])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, arr.size * 4,
            ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr).position(0),
            GLES30.GL_STATIC_DRAW)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.035f, 0.024f, 0.09f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        program = link(
            """#version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNormal;
            layout(location=2) in vec3 aColor;
            layout(location=3) in vec2 aUv;
            uniform mat4 uMvp; uniform mat4 uModel;
            out vec3 vNormal; out vec3 vColor; out vec2 vUv;
            void main() {
                vNormal = mat3(uModel) * aNormal;
                vColor = aColor;
                vUv = aUv;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }""",
            """#version 300 es
            precision mediump float;
            in vec3 vNormal; in vec3 vColor; in highp vec2 vUv;
            uniform int uFlat; uniform vec4 uFlatColor; uniform int uUseColor; uniform int uLight;
            uniform int uTextured; uniform sampler2D uTex;
            out vec4 fragColor;
            void main() {
                if (uFlat == 1) { fragColor = uFlatColor; return; }
                vec3 base = uUseColor == 0 ? vec3(0.82, 0.82, 0.86)
                    : uTextured == 1 ? texture(uTex, vUv).rgb : vColor;
                if (uLight == 0) { fragColor = vec4(base, 1.0); return; }
                vec3 n = normalize(vNormal);
                if (!gl_FrontFacing) n = -n;
                float key = max(dot(n, normalize(vec3(0.4, 0.8, 1.0))), 0.0);
                float rim = max(dot(n, normalize(vec3(-0.6, 0.2, -0.8))), 0.0);
                vec3 c = base * (0.38 + 0.62 * key) + vec3(0.45, 0.3, 1.0) * 0.18 * rim;
                fragColor = vec4(c, 1.0);
            }""")
        GLES30.glGenBuffers(vbo.size, vbo, 0)
        GLES30.glGenTextures(1, tex, 0)
        upload(current) // the GL context is new, re-upload the mesh if we already have one
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proj, 0, 40f, width.toFloat() / height, 0.05f, 50f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (indexCount == 0) return
        if (autoRotate) yaw += 0.4f
        Matrix.setLookAtM(view, 0, panX, panY + 0.1f, distance, panX, panY, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp.copyOf(), 0)

        GLES30.glUseProgram(program)
        fun loc(n: String) = GLES30.glGetUniformLocation(program, n)
        GLES30.glUniformMatrix4fv(loc("uMvp"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(loc("uModel"), 1, false, model, 0)

        if (grid) {
            GLES30.glUniform1i(loc("uFlat"), 1)
            GLES30.glDisableVertexAttribArray(1); GLES30.glDisableVertexAttribArray(2); GLES30.glDisableVertexAttribArray(3)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[5])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glUniform4f(loc("uFlatColor"), 0.62f, 0.5f, 1f, 0.22f)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridCount)
        }

        GLES30.glUniform1i(loc("uFlat"), 0)
        GLES30.glUniform1i(loc("uUseColor"), if (colors) 1 else 0)
        GLES30.glUniform1i(loc("uLight"), if (lighting) 1 else 0)
        GLES30.glUniform1i(loc("uTextured"), if (textured) 1 else 0)
        for (slot in 0..1) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[slot])
            GLES30.glEnableVertexAttribArray(slot)
            GLES30.glVertexAttribPointer(slot, 3, GLES30.GL_FLOAT, false, 0, 0)
        }
        if (textured) {
            GLES30.glDisableVertexAttribArray(2)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[6])
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glUniform1i(loc("uTex"), 0)
        } else {
            GLES30.glDisableVertexAttribArray(3)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[2])
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 0, 0)
        }
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1f, 1f)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[3])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        if (wireframe) {
            GLES30.glUniform1i(loc("uFlat"), 1)
            GLES30.glUniform4f(loc("uFlatColor"), 0.55f, 0.4f, 1f, 0.55f)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vbo[4])
            GLES30.glDrawElements(GLES30.GL_LINES, lineCount, GLES30.GL_UNSIGNED_INT, 0)
        }
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        return p
    }
}
