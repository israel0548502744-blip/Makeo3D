package com.triposr.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.opengl.GLUtils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A short vertical video of the model turning once, with a "Made with Makeo 3D" mark, for
 * sharing on TikTok, Instagram or WhatsApp. Drawn with the viewer's own renderer into the
 * encoder's input surface (no pixel copies), H.264 in MP4.
 */
object TurntableVideo {
    private const val W = 720
    private const val H = 1280
    private const val FPS = 30
    private const val SECONDS = 6

    fun render(ctx: Context, mesh: Mesh, out: File, progress: (Float) -> Unit) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val input = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()

        fun drain(end: Boolean) {
            if (end) codec.signalEndOfInputStream()
            while (true) {
                val i = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    i >= 0 -> {
                        val buf = codec.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) muxer.writeSampleData(track, buf, info)
                        codec.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)
        val configs = arrayOfNulls<EGLConfig>(1)
        EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1,
            EGL14.EGL_NONE), 0, configs, 0, 1, IntArray(1), 0)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreateWindowSurface(display, configs[0], input, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, context)
        try {
            val r = MeshRenderer()
            r.onSurfaceCreated(null, null)
            r.upload(mesh)
            r.onSurfaceChanged(null, W, H)
            r.distance = 2.9f; r.pitch = 12f
            val mark = Watermark(ctx)
            val frames = FPS * SECONDS
            for (f in 0 until frames) {
                r.yaw = 30f + 360f * f / frames
                r.onDrawFrame(null)
                mark.draw()
                EGLExt.eglPresentationTimeANDROID(display, surface, f * 1_000_000_000L / FPS)
                EGL14.eglSwapBuffers(display, surface)
                drain(false)
                progress((f + 1f) / frames)
            }
            drain(true)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            codec.stop(); codec.release(); input.release()
            runCatching { muxer.stop() }; muxer.release()
        }
    }

    /** The "Made with Makeo 3D" mark at the bottom, drawn as a textured quad over each frame. */
    private class Watermark(ctx: Context) {
        private val program: Int
        private val tex = IntArray(1)
        private val quad: java.nio.FloatBuffer

        init {
            val bmp = Bitmap.createBitmap(W, 160, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val font = runCatching { Typeface.createFromAsset(ctx.assets, "fonts/Rubik-Bold.ttf") }.getOrDefault(Typeface.DEFAULT_BOLD)
            val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 52f; typeface = font; textAlign = Paint.Align.CENTER }
            val small = Paint(big).apply { textSize = 30f; color = Color.argb(200, 220, 210, 255) }
            c.drawText("Makeo 3D", W / 2f, 70f, big)
            c.drawText(tr("מתמונה אחת למודל תלת־ממד, ישירות בטלפון"), W / 2f, 120f, small)
            GLES30.glGenTextures(1, tex, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            // Bottom strip of the frame: x, y, u, v (the bitmap's first row is its top).
            val y0 = -1f; val y1 = -1f + 2f * 160 / H
            val v = floatArrayOf(-1f, y0, 0f, 1f, 1f, y0, 1f, 1f, -1f, y1, 0f, 0f, 1f, y1, 1f, 0f)
            quad = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).also { it.position(0) }
            program = link(
                "#version 300 es\nlayout(location=0) in vec2 aPos; layout(location=1) in vec2 aUv; out vec2 vUv;\n" +
                    "void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }",
                "#version 300 es\nprecision mediump float; in vec2 vUv; uniform sampler2D uTex; out vec4 o;\n" +
                    "void main() { o = texture(uTex, vUv); }")
        }

        fun draw() {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glUseProgram(program)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glDisableVertexAttribArray(2); GLES30.glDisableVertexAttribArray(3)
            quad.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, quad)
            quad.position(2)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, quad)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uTex"), 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        }

        private fun link(vs: String, fs: String): Int {
            fun compile(type: Int, src: String) = GLES30.glCreateShader(type).also {
                GLES30.glShaderSource(it, src); GLES30.glCompileShader(it)
            }
            return GLES30.glCreateProgram().also {
                GLES30.glAttachShader(it, compile(GLES30.GL_VERTEX_SHADER, vs))
                GLES30.glAttachShader(it, compile(GLES30.GL_FRAGMENT_SHADER, fs))
                GLES30.glLinkProgram(it)
            }
        }
    }
}
