package com.triposr.mobile

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Triangle mesh with per-vertex normals and colors (all flat arrays, xyz / rgb interleaved per vertex). */
class Mesh(
    val positions: FloatArray,
    val normals: FloatArray,
    var colors: FloatArray,
    val indices: IntArray,
) {
    /** Per-corner UVs (aligned with [indices]) and the baked texture; null without texture baking. */
    var uvs: FloatArray? = null
    var texture: Texture? = null

    /** Real size (longest side, mm) for parts designed to measure, e.g. a keychain; null = scale to the default. */
    var sizeMm: Float? = null

    /** Top-view height grid of 2D-designed parts (templates, QR), for SVG cutter export; null otherwise. */
    var plan: Plan? = null

    /** Copies the texture of [from] when the triangles are unchanged (same indices). */
    fun withTextureOf(from: Mesh): Mesh {
        if (indices === from.indices || indices.contentEquals(from.indices)) { uvs = from.uvs; texture = from.texture }
        return this
    }

    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3

    /**
     * TripoSR's model space has +z up. Rotate into the usual "+y up, facing the camera"
     * frame, same as TripoSR's gradio demo: (x, y, z) -> (-y, z, -x).
     */
    fun toYUp(): Mesh {
        fun rot(a: FloatArray): FloatArray {
            val out = FloatArray(a.size)
            for (v in 0 until a.size / 3) {
                val x = a[v * 3]; val y = a[v * 3 + 1]; val z = a[v * 3 + 2]
                out[v * 3] = -y; out[v * 3 + 1] = z; out[v * 3 + 2] = -x
            }
            return out
        }
        return Mesh(rot(positions), rot(normals), colors, indices).withTextureOf(this)
    }

    /**
     * OBJ + MTL + PNG in one ZIP (OBJ cannot embed a texture). Same layout Blender and MeshLab
     * expect: model.obj references model.mtl, which references texture.png.
     */
    fun writeObjZip(out: OutputStream) {
        val t = texturedArrays() ?: throw IllegalStateException("no texture")
        val zip = java.util.zip.ZipOutputStream(out)
        zip.putNextEntry(java.util.zip.ZipEntry("model.obj"))
        val w = zip.bufferedWriter()
        w.write("# TripoSR mobile\nmtllib model.mtl\nusemtl baked\n")
        val n = t.positions.size / 3
        for (v in 0 until n) w.write("v ${t.positions[v * 3]} ${t.positions[v * 3 + 1]} ${t.positions[v * 3 + 2]}\n")
        // OBJ's v axis points up, glTF's (and our atlas') points down.
        for (v in 0 until n) w.write("vt ${t.uvs[v * 2]} ${1f - t.uvs[v * 2 + 1]}\n")
        for (v in 0 until n) w.write("vn ${t.normals[v * 3]} ${t.normals[v * 3 + 1]} ${t.normals[v * 3 + 2]}\n")
        for (k in 0 until t.indices.size / 3) {
            val a = t.indices[k * 3] + 1; val b = t.indices[k * 3 + 1] + 1; val c = t.indices[k * 3 + 2] + 1
            w.write("f ${a}/${a}/${a} ${b}/${b}/${b} ${c}/${c}/${c}\n")
        }
        w.flush()
        zip.closeEntry()
        zip.putNextEntry(java.util.zip.ZipEntry("model.mtl"))
        zip.write("newmtl baked\nKa 1 1 1\nKd 1 1 1\nKs 0 0 0\nillum 1\nmap_Kd texture.png\n".toByteArray())
        zip.closeEntry()
        zip.putNextEntry(java.util.zip.ZipEntry("texture.png"))
        zip.write(texture!!.toPng())
        zip.closeEntry()
        zip.finish()
        out.flush()
    }

    fun writeObj(out: OutputStream) {
        val w = out.bufferedWriter()
        w.write("# TripoSR mobile\n")
        for (v in 0 until vertexCount) {
            w.write("v ${positions[v * 3]} ${positions[v * 3 + 1]} ${positions[v * 3 + 2]} " +
                "${colors[v * 3]} ${colors[v * 3 + 1]} ${colors[v * 3 + 2]}\n")
        }
        for (v in 0 until vertexCount) {
            w.write("vn ${normals[v * 3]} ${normals[v * 3 + 1]} ${normals[v * 3 + 2]}\n")
        }
        for (t in 0 until triangleCount) {
            val a = indices[t * 3] + 1; val b = indices[t * 3 + 1] + 1; val c = indices[t * 3 + 2] + 1
            w.write("f ${a}//${a} ${b}//${b} ${c}//${c}\n")
        }
        w.flush()
    }

    /**
     * Binary glTF 2.0. With a baked texture: POSITION, NORMAL, TEXCOORD_0 and an embedded PNG;
     * otherwise POSITION, NORMAL and COLOR_0. Indices are uint32.
     */
    fun writeGlb(out: OutputStream) {
        val tex = texturedArrays()
        val pos = tex?.positions ?: positions
        val nor = tex?.normals ?: normals
        val idx = tex?.indices ?: indices
        val png = if (tex != null) texture!!.toPng() else null
        val n = pos.size / 3
        val vecBytes = n * 12
        val thirdBytes = if (tex != null) n * 8 else vecBytes
        val idxBytes = idx.size * 4
        val pngOffset = vecBytes * 2 + thirdBytes + idxBytes
        val pngBytes = png?.size ?: 0
        val binSize = (pngOffset + pngBytes + 3) / 4 * 4
        val bin = ByteBuffer.allocate(binSize).order(ByteOrder.LITTLE_ENDIAN)
        // glTF COLOR_0 is linear while TripoSR colors are sRGB like the input photo (a texture is
        // declared sRGB by glTF, so it is written as is).
        bin.asFloatBuffer().put(pos).put(nor).put(tex?.uvs ?: FloatArray(colors.size) { srgbToLinear(colors[it]) })
        bin.position(vecBytes * 2 + thirdBytes)
        bin.asIntBuffer().put(idx)
        if (png != null) { bin.position(pngOffset); bin.put(png) }

        val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in 0 until n) for (c in 0..2) {
            min[c] = minOf(min[c], pos[v * 3 + c]); max[c] = maxOf(max[c], pos[v * 3 + c])
        }
        val pbr = if (tex != null) """"baseColorTexture":{"index":0},""" else ""
        val third = if (tex != null) """"TEXCOORD_0":2""" else """"COLOR_0":2"""
        val textures = if (tex != null) """
             "samplers":[{"magFilter":9729,"minFilter":9729,"wrapS":33071,"wrapT":33071}],
             "images":[{"bufferView":4,"mimeType":"image/png"}],
             "textures":[{"sampler":0,"source":0}],""" else ""
        val imageView = if (tex != null) """,{"buffer":0,"byteOffset":$pngOffset,"byteLength":$pngBytes}""" else ""
        val json = """
            {"asset":{"version":"2.0","generator":"TripoSR mobile"},
             "scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],$textures
             "materials":[{"pbrMetallicRoughness":{${pbr}"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1},"doubleSided":true}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,$third},"indices":3,"material":0}]}],
             "buffers":[{"byteLength":$binSize}],
             "bufferViews":[
               {"buffer":0,"byteOffset":0,"byteLength":$vecBytes,"target":34962},
               {"buffer":0,"byteOffset":$vecBytes,"byteLength":$vecBytes,"target":34962},
               {"buffer":0,"byteOffset":${vecBytes * 2},"byteLength":$thirdBytes,"target":34962},
               {"buffer":0,"byteOffset":${vecBytes * 2 + thirdBytes},"byteLength":$idxBytes,"target":34963}$imageView],
             "accessors":[
               {"bufferView":0,"componentType":5126,"count":$n,"type":"VEC3","min":[${min.joinToString(",")}],"max":[${max.joinToString(",")}]},
               {"bufferView":1,"componentType":5126,"count":$n,"type":"VEC3"},
               {"bufferView":2,"componentType":5126,"count":$n,"type":"${if (tex != null) "VEC2" else "VEC3"}"},
               {"bufferView":3,"componentType":5125,"count":${idx.size},"type":"SCALAR"}]}
        """.trimIndent().replace("\n", "").toByteArray(Charsets.UTF_8)
        val jsonPadded = (json.size + 3) / 4 * 4
        val total = 12 + 8 + jsonPadded + 8 + binSize

        val header = ByteBuffer.allocate(12 + 8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46546C67).putInt(2).putInt(total)   // "glTF", version, length
        header.putInt(jsonPadded).putInt(0x4E4F534A)        // JSON chunk
        out.write(header.array())
        out.write(json)
        repeat(jsonPadded - json.size) { out.write(' '.code) }
        val binHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        binHeader.putInt(binSize).putInt(0x004E4942)  // BIN chunk
        out.write(binHeader.array())
        out.write(bin.array())
        out.flush()
    }
}

private fun srgbToLinear(c: Float): Float {
    val v = c.coerceIn(0f, 1f)
    return if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
}

/**
 * Naive Surface Nets isosurface extraction (one vertex per surface cell, one quad per
 * sign-changing grid edge). Gives watertight meshes like marching cubes, without the
 * 256-entry lookup tables.
 *
 * [field] holds res^3 samples, index = (i * res + j) * res + k for the grid point
 * (x_i, y_j, z_k), each axis spanning [-radius, radius]. Inside is field > 0.
 * Triangles are wound counter-clockwise when seen from outside.
 */
object SurfaceNets {
    private val CORNERS = arrayOf(
        intArrayOf(0, 0, 0), intArrayOf(1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(1, 1, 0),
        intArrayOf(0, 0, 1), intArrayOf(1, 0, 1), intArrayOf(0, 1, 1), intArrayOf(1, 1, 1),
    )
    private val EDGES = arrayOf(
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7), // along x
        intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7), // along y
        intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7), // along z
    )

    fun extract(field: FloatArray, res: Int, radius: Float): Mesh {
        val cells = res - 1
        val step = 2f * radius / cells
        val cellVertex = IntArray(cells * cells * cells) { -1 }
        val pos = FloatList()
        fun g(i: Int, j: Int, k: Int) = field[(i * res + j) * res + k]
        fun c(i: Int, j: Int, k: Int) = (i * cells + j) * cells + k

        val v = FloatArray(8)
        for (i in 0 until cells) for (j in 0 until cells) for (k in 0 until cells) {
            var mask = 0
            for (n in 0..7) {
                val o = CORNERS[n]
                v[n] = g(i + o[0], j + o[1], k + o[2])
                if (v[n] > 0f) mask = mask or (1 shl n)
            }
            if (mask == 0 || mask == 0xFF) continue
            var sx = 0f; var sy = 0f; var sz = 0f; var cnt = 0
            for (e in EDGES) {
                val a = v[e[0]]; val b = v[e[1]]
                if ((a > 0f) == (b > 0f)) continue
                val t = a / (a - b)
                val oa = CORNERS[e[0]]; val ob = CORNERS[e[1]]
                sx += oa[0] + t * (ob[0] - oa[0])
                sy += oa[1] + t * (ob[1] - oa[1])
                sz += oa[2] + t * (ob[2] - oa[2])
                cnt++
            }
            cellVertex[c(i, j, k)] = pos.size / 3
            pos.add(-radius + (i + sx / cnt) * step)
            pos.add(-radius + (j + sy / cnt) * step)
            pos.add(-radius + (k + sz / cnt) * step)
        }

        val idx = IntList()
        fun quad(a: Int, b: Int, cc: Int, d: Int, flip: Boolean) {
            if (!flip) { idx.add(a); idx.add(b); idx.add(cc); idx.add(a); idx.add(cc); idx.add(d) }
            else { idx.add(a); idx.add(cc); idx.add(b); idx.add(a); idx.add(d); idx.add(cc) }
        }
        // For a grid edge p -> p + e_axis, the four cells around it are listed counter-clockwise
        // seen from +axis, so the quad faces +axis. Flip when the inside is at p + e_axis.
        for (i in 0 until res) for (j in 0 until res) for (k in 0 until res) {
            val f0 = g(i, j, k) > 0f
            if (i < cells && j in 1 until cells && k in 1 until cells && f0 != (g(i + 1, j, k) > 0f)) {
                quad(cellVertex[c(i, j - 1, k - 1)], cellVertex[c(i, j, k - 1)],
                    cellVertex[c(i, j, k)], cellVertex[c(i, j - 1, k)], !f0)
            }
            if (j < cells && i in 1 until cells && k in 1 until cells && f0 != (g(i, j + 1, k) > 0f)) {
                quad(cellVertex[c(i - 1, j, k - 1)], cellVertex[c(i - 1, j, k)],
                    cellVertex[c(i, j, k)], cellVertex[c(i, j, k - 1)], !f0)
            }
            if (k < cells && i in 1 until cells && j in 1 until cells && f0 != (g(i, j, k + 1) > 0f)) {
                quad(cellVertex[c(i - 1, j - 1, k)], cellVertex[c(i, j - 1, k)],
                    cellVertex[c(i, j, k)], cellVertex[c(i - 1, j, k)], !f0)
            }
        }

        val positions = pos.toArray()
        val indices = idx.toArray()
        return Mesh(positions, MeshTools.normals(positions, indices), FloatArray(positions.size) { 0.7f }, indices)
    }
}

/** Growable primitive arrays (avoid boxing millions of values). */
class FloatList {
    private var data = FloatArray(1 shl 16)
    var size = 0; private set
    fun add(x: Float) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = x }
    operator fun get(i: Int) = data[i]
    operator fun set(i: Int, x: Float) { data[i] = x }
    fun toArray() = data.copyOf(size)
}

class IntList {
    private var data = IntArray(1 shl 16)
    var size = 0; private set
    fun add(x: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = x }
    fun toArray() = data.copyOf(size)
}

/** A height grid seen from above: [w]×[h] cells of [cellMm], row 0 at the back (top of an SVG). */
class Plan(val w: Int, val h: Int, val cellMm: Float, val heights: FloatArray)
