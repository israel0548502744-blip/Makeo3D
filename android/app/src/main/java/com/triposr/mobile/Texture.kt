package com.triposr.mobile

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Square ARGB texture; row 0 is v = 0 (top of the image, as in glTF). */
class Texture(val size: Int, val pixels: IntArray) {

    /** RGB PNG (no filtering), used inside GLB exports. */
    fun toPng(): ByteArray {
        val raw = ByteArray(size * (size * 3 + 1))
        var o = 0
        for (y in 0 until size) {
            raw[o++] = 0
            for (x in 0 until size) {
                val c = pixels[y * size + x]
                raw[o++] = (c shr 16).toByte(); raw[o++] = (c shr 8).toByte(); raw[o++] = c.toByte()
            }
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val crc = CRC32()
            val t = type.toByteArray(Charsets.US_ASCII)
            crc.update(t); crc.update(data)
            out.write(ByteBuffer.allocate(4).putInt(data.size).array())
            out.write(t); out.write(data)
            out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(size).putInt(size).put(8).put(2).put(0).put(0).put(0).array())
        val z = ByteArrayOutputStream()
        DeflaterOutputStream(z, Deflater(6), 1 shl 16).use { it.write(raw) }
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    fun save(file: File) {
        DataOutputStream(DeflaterOutputStream(file.outputStream().buffered(1 shl 16), Deflater(1), 1 shl 16)).use { out ->
            out.writeInt(MAGIC); out.writeInt(size)
            val row = ByteBuffer.allocate(size * 4)
            for (y in 0 until size) {
                row.clear(); row.asIntBuffer().put(pixels, y * size, size)
                out.write(row.array())
            }
        }
    }

    companion object {
        private const val MAGIC = 0x54455831 // "TEX1"

        fun load(file: File): Texture? = runCatching {
            DataInputStream(InflaterInputStream(file.inputStream().buffered(1 shl 16), java.util.zip.Inflater(), 1 shl 16)).use { inp ->
                require(inp.readInt() == MAGIC)
                val size = inp.readInt()
                val px = IntArray(size * size)
                val row = ByteArray(size * 4)
                for (y in 0 until size) {
                    inp.readFully(row)
                    ByteBuffer.wrap(row).asIntBuffer().get(px, y * size, size)
                }
                Texture(size, px)
            }
        }.getOrNull()
    }
}

/**
 * Texture baking, like TripoSR's `--bake-texture`: instead of one color per vertex, every
 * triangle gets its own patch of a texture and the NeRF color is sampled at each texel.
 *
 * Surface Nets emits each quad as two consecutive triangles sharing a diagonal, so the atlas is
 * a grid of square cells, one per quad: corners p (0,0), q (1,0), r (0,1), u (1,1), with the
 * shared edge q-r on the cell's diagonal. A lone triangle uses half a cell. Cells keep one texel
 * of margin so bilinear filtering never reads a neighbouring cell.
 */
class TextureAtlas private constructor(
    /** Triangle indices, rotated so the first corner of each cell's first triangle is p. */
    val indices: IntArray,
    /** Per-corner UVs aligned with [indices]. */
    val uvs: FloatArray,
    val size: Int,
    private val cell: Int,
    private val grid: Int,
    /** 4 vertex ids per cell (p, q, r, u); u = -1 for a single triangle. */
    private val corners: IntArray,
) {
    val cellCount get() = corners.size / 4
    val texelCount get() = cellCount * cell * cell
    /** Texels along a cell's side (two of them are margin). */
    val cellSize get() = cell

    /**
     * Texel number [i] as a blend of three vertices: their ids into [ids] and weights into [w].
     * Returns the pixel index it fills. Same texels and order as [texel].
     */
    fun texelAt(i: Int, ids: IntArray, w: FloatArray): Int {
        val per = cell * cell
        val c = i / per; val local = i % per
        val tx = local % cell; val ty = local / cell
        val span = (cell - 2).toFloat()
        var a = ((tx + 0.5f - 1f) / span).coerceIn(0f, 1f)
        var b = ((ty + 0.5f - 1f) / span).coerceIn(0f, 1f)
        val p = corners[c * 4]; val q = corners[c * 4 + 1]; val r = corners[c * 4 + 2]; val u = corners[c * 4 + 3]
        if (a + b <= 1f || u < 0) {
            if (a + b > 1f) { val s = a + b; a /= s; b /= s }
            ids[0] = p; ids[1] = q; ids[2] = r
            w[0] = 1f - a - b; w[1] = a; w[2] = b
        } else {
            ids[0] = u; ids[1] = r; ids[2] = q
            w[0] = a + b - 1f; w[1] = 1f - a; w[2] = 1f - b
        }
        val x0 = c % grid * cell; val y0 = c / grid * cell
        return (y0 + ty) * size + x0 + tx
    }

    /** Model-space point for texel number [i] (cell-major order). Returns the pixel index it fills. */
    fun texel(i: Int, positions: FloatArray, out: FloatArray): Int {
        val per = cell * cell
        val c = i / per; val local = i % per
        val tx = local % cell; val ty = local / cell
        val span = (cell - 2).toFloat()
        var a = ((tx + 0.5f - 1f) / span).coerceIn(0f, 1f)
        var b = ((ty + 0.5f - 1f) / span).coerceIn(0f, 1f)
        val p = corners[c * 4]; val q = corners[c * 4 + 1]; val r = corners[c * 4 + 2]; val u = corners[c * 4 + 3]
        if (a + b <= 1f || u < 0) {
            if (a + b > 1f) { val s = a + b; a /= s; b /= s }
            for (k in 0..2) {
                val pk = positions[p * 3 + k]
                out[k] = pk + a * (positions[q * 3 + k] - pk) + b * (positions[r * 3 + k] - pk)
            }
        } else {
            for (k in 0..2) {
                val uk = positions[u * 3 + k]
                out[k] = uk + (1f - a) * (positions[r * 3 + k] - uk) + (1f - b) * (positions[q * 3 + k] - uk)
            }
        }
        val x0 = c % grid * cell; val y0 = c / grid * cell
        return (y0 + ty) * size + x0 + tx
    }

    companion object {
        /** Lays out [mesh] in a [size]² atlas, or returns null when cells would be too small. */
        fun build(mesh: Mesh, size: Int): TextureAtlas? {
            val idx = mesh.indices.copyOf()
            val nt = mesh.triangleCount
            val corners = IntList()
            val firstTri = IntList() // first triangle of each cell; the second (if any) follows it
            var t = 0
            while (t < nt) {
                val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
                var paired = false
                if (t + 1 < nt) {
                    val o = intArrayOf(idx[t * 3 + 3], idx[t * 3 + 4], idx[t * 3 + 5])
                    val inA = booleanArrayOf(a in o, b in o, c in o)
                    if (inA.count { it } == 2 && o.distinct().size == 3) {
                        // Rotate the first triangle so its unshared vertex comes first (keeps winding).
                        val tri = intArrayOf(a, b, c)
                        val s = inA.indexOfFirst { !it }
                        val p = tri[s]; val q = tri[(s + 1) % 3]; val r = tri[(s + 2) % 3]
                        idx[t * 3] = p; idx[t * 3 + 1] = q; idx[t * 3 + 2] = r
                        val u = o.first { it != q && it != r }
                        corners.add(p); corners.add(q); corners.add(r); corners.add(u)
                        firstTri.add(t)
                        paired = true
                    }
                }
                if (paired) t += 2
                else {
                    corners.add(a); corners.add(b); corners.add(c); corners.add(-1)
                    firstTri.add(t)
                    t += 1
                }
            }
            val cells = firstTri.size
            if (cells == 0) return null
            val grid = ceil(sqrt(cells.toDouble())).toInt()
            val cell = size / grid
            if (cell < 4) return null

            val cs = corners.toArray()
            val uvs = FloatArray(idx.size * 2)
            val ft = firstTri.toArray()
            for (ci in 0 until cells) {
                val x0 = ci % grid * cell; val y0 = ci / grid * cell
                fun uv(cx: Int, cy: Int, out: Int) {
                    uvs[out * 2] = (x0 + 1 + cx * (cell - 2)).toFloat() / size
                    uvs[out * 2 + 1] = (y0 + 1 + cy * (cell - 2)).toFloat() / size
                }
                val p = cs[ci * 4]; val q = cs[ci * 4 + 1]; val r = cs[ci * 4 + 2]; val u = cs[ci * 4 + 3]
                val t0 = ft[ci]
                val tris = if (u >= 0) intArrayOf(t0, t0 + 1) else intArrayOf(t0)
                for (tt in tris) for (k in 0..2) {
                    val corner = tt * 3 + k
                    when (idx[corner]) {
                        p -> uv(0, 0, corner)
                        q -> uv(1, 0, corner)
                        r -> uv(0, 1, corner)
                        else -> uv(1, 1, corner)
                    }
                }
            }
            return TextureAtlas(idx, uvs, size, cell, grid, cs)
        }
    }
}

/** Vertex-per-corner arrays for a textured mesh (glTF and OpenGL need one UV per vertex). */
class TexturedArrays(val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray)

fun Mesh.texturedArrays(): TexturedArrays? {
    val uv = uvs ?: return null
    texture ?: return null
    // An open-addressing table of (vertex, uv) -> new index: a boxed HashMap of a dense model's
    // ~2 million corners alone took over 100 MB of the app's memory.
    var cap = 16
    while (cap < indices.size * 2) cap = cap shl 1
    val keys = LongArray(cap) { -1L }; val vals = IntArray(cap)
    var count = 0
    val pos = FloatList(); val nor = FloatList(); val tex = FloatList()
    val out = IntArray(indices.size)
    for (i in indices.indices) {
        val v = indices[i]
        val qu = (uv[i * 2] * 16384f).roundToInt().toLong()
        val qv = (uv[i * 2 + 1] * 16384f).roundToInt().toLong()
        val key = (v.toLong() shl 30) or (qu shl 15) or qv
        var h = ((key * -0x61c8864680b583ebL) ushr 20).toInt() and (cap - 1)
        while (keys[h] != -1L && keys[h] != key) h = (h + 1) and (cap - 1)
        if (keys[h] == -1L) {
            keys[h] = key; vals[h] = count++
            for (k in 0..2) { pos.add(positions[v * 3 + k]); nor.add(normals[v * 3 + k]) }
            tex.add(uv[i * 2]); tex.add(uv[i * 2 + 1])
        }
        out[i] = vals[h]
    }
    return TexturedArrays(pos.toArray(), nor.toArray(), tex.toArray(), out)
}
