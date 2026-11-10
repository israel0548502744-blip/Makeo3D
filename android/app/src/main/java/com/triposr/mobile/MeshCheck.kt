package com.triposr.mobile

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Looks at a finished mesh the way a slicer would, before it is shown or saved: is there any
 * geometry, is it closed, do the faces point outward, is the size sensible. Used for every print
 * product ([Creations]) and by the JVM quality tests.
 */
object MeshCheck {
    class Result(
        val vertices: Int, val triangles: Int,
        val openEdges: Int,       // edges with one triangle: a hole in the surface
        val nonManifold: Int,     // edges shared by more than two triangles
        val misoriented: Int,     // edges whose two triangles face opposite ways
        val degenerate: Int,      // triangles with no area
        val parts: Int,           // separate closed pieces
        val volumeMm3: Double,    // positive when the faces point outward
        val sizeMm: FloatArray,   // width, depth, height
        val finite: Boolean,
    ) {
        /** What makes the mesh unusable, as text for the log; empty when it is fine. */
        val problems: List<String> get() = buildList {
            if (triangles == 0) add("no triangles")
            if (!finite) add("coordinates are not numbers")
            if (openEdges > 0) add("$openEdges open edges")
            if (misoriented > 0) add("$misoriented edges with flipped faces")
            if (triangles > 0 && volumeMm3 <= 0.0) add("volume %.1f mm3 (inside out or flat)".format(volumeMm3))
            if (triangles > 0 && (sizeMm.max() < MIN_MM || sizeMm.max() > MAX_MM)) add("size %.1f mm".format(sizeMm.max()))
            if (triangles > 0 && sizeMm.min() < 0.3f) add("thickness %.2f mm".format(sizeMm.min()))
        }
        val ok get() = problems.isEmpty()
        override fun toString() = "$triangles triangles, $parts part(s), %.0f×%.0f×%.0f mm, open=$openEdges nonManifold=$nonManifold flipped=$misoriented degenerate=$degenerate, volume %.1f cm3"
            .format(sizeMm[0], sizeMm[1], sizeMm[2], volumeMm3 / 1000)
    }

    const val MIN_MM = 5f
    const val MAX_MM = 400f

    /** [mesh] is in the app's viewer space (Y up); [Mesh.sizeMm] is its longest side in millimetres. */
    fun inspect(mesh: Mesh): Result {
        val p = mesh.positions; val idx = mesh.indices
        val nv = mesh.vertexCount; val nt = mesh.triangleCount
        val finite = p.all { it.isFinite() }
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until nv) for (c in 0..2) { lo[c] = min(lo[c], p[v * 3 + c]); hi[c] = max(hi[c], p[v * 3 + c]) }
        val longest = if (nv == 0) 1f else max(hi[0] - lo[0], max(hi[1] - lo[1], hi[2] - lo[2])).coerceAtLeast(1e-9f)
        val mm = (mesh.sizeMm ?: Printability.TARGET_MM) / longest
        val size = if (nv == 0) FloatArray(3) else floatArrayOf((hi[0] - lo[0]) * mm, (hi[2] - lo[2]) * mm, (hi[1] - lo[1]) * mm)
        if (nt == 0 || !finite) return Result(nv, nt, 0, 0, 0, 0, 0, 0.0, size, finite)

        // Vertices at the same place count as one, as in an STL file.
        val ids = HashMap<Long, Int>(nv * 2)
        val q = 2_000_000 / longest  // grid far finer than any feature
        val same = IntArray(nv) { v ->
            val key = ((p[v * 3] - lo[0]) * q).toLong() * 4_000_003L * 4_000_003L +
                ((p[v * 3 + 1] - lo[1]) * q).toLong() * 4_000_003L + ((p[v * 3 + 2] - lo[2]) * q).toLong()
            ids.getOrPut(key) { ids.size }
        }
        val n = ids.size.toLong()
        val edges = HashMap<Long, IntArray>(nt * 3)  // undirected edge -> [forward uses, backward uses]
        val parent = IntArray(ids.size) { it }
        fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        var degenerate = 0; var volume = 0.0
        for (t in 0 until nt) {
            val a = same[idx[t * 3]]; val b = same[idx[t * 3 + 1]]; val c = same[idx[t * 3 + 2]]
            if (a == b || b == c || a == c) { degenerate++; continue }
            for ((u, v) in arrayOf(a to b, b to c, c to a)) {
                val e = edges.getOrPut(min(u, v) * n + max(u, v)) { IntArray(2) }
                e[if (u < v) 0 else 1]++
                parent[find(u)] = find(v)
            }
            val i = idx[t * 3] * 3; val j = idx[t * 3 + 1] * 3; val k = idx[t * 3 + 2] * 3
            volume += (p[i].toDouble() * (p[j + 1] * p[k + 2] - p[j + 2] * p[k + 1]) -
                p[i + 1] * (p[j] * p[k + 2] - p[j + 2] * p[k]) + p[i + 2] * (p[j] * p[k + 1] - p[j + 1] * p[k])) / 6.0
        }
        var open = 0; var nonManifold = 0; var flipped = 0
        for (e in edges.values) when {
            e[0] + e[1] == 1 -> open++
            e[0] + e[1] > 2 -> nonManifold++
            e[0] != 1 -> flipped++
        }
        val used = BooleanArray(ids.size)
        for (t in 0 until nt) used[find(same[idx[t * 3]])] = true
        return Result(nv, nt, open, nonManifold, flipped, degenerate, used.count { it }, volume * mm.toDouble() * mm * mm, size, true)
    }

    /** Throws with a message for the user when [mesh] is not something to show or print. */
    fun verify(mesh: Mesh, what: String): Result {
        val r = inspect(mesh)
        CrashLog.log("Check $what: $r")
        if (r.ok) return r
        CrashLog.log("Check $what FAILED: ${r.problems.joinToString("; ")}")
        throw IllegalStateException(when {
            r.triangles == 0 || !r.finite -> tr("לא נוצרה צורה. נסה תמונה או טקסט אחרים.")
            r.sizeMm.max() < MIN_MM || r.sizeMm.max() > MAX_MM || r.sizeMm.min() < 0.3f -> tr("הצורה שנוצרה קטנה או גדולה מדי להדפסה. שנה את המידות ונסה שוב.")
            else -> tr("הצורה שנוצרה לא תקינה להדפסה (לא סגורה). נסה תמונה פשוטה יותר או מידות אחרות.")
        })
    }
}
