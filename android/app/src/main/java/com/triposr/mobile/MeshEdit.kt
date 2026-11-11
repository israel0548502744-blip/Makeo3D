package com.triposr.mobile

/**
 * Touch-up of a finished mesh: rub parts away, flatten bumps and spikes, drop loose pieces.
 * Works on a copy; [result] gives the cleaned mesh. Each stroke can be undone.
 */
class MeshEditor(private val src: Mesh) {
    // Everything grows: closing a hole adds a vertex and triangles.
    /** Vertex positions as edited so far. */
    var positions: FloatArray = src.positions.copyOf(); private set
    var colors: FloatArray = src.colors.copyOf(); private set
    private var idx = src.indices.copyOf()
    private var uv = src.uvs?.copyOf()
    private var nv = src.vertexCount
    private var nt = src.triangleCount
    private var alive = BooleanArray(nt) { true }
    /** How many live triangles use each vertex; 0 = the vertex is gone. */
    private var degree = IntArray(nv).also { d -> for (v in idx) d[v]++ }
    /** The triangles still there. A new array after every change. */
    @Volatile var indices: IntArray = src.indices; private set

    private class Step(val killed: IntArray, val added: IntArray, val moved: IntArray, val old: FloatArray)
    private val steps = ArrayList<Step>()
    private var killed = IntList()
    private var moved = IntList()
    private var old = FloatList()
    private var touched = BooleanArray(nv)
    private var flag = BooleanArray(nv)

    val changed get() = steps.isNotEmpty()

    // Neighbours of each vertex (built when first needed): nList[nStart[v] until nStart[v + 1]].
    private var nStart = IntArray(0)
    private var nList = IntArray(0)

    private fun neighbours() {
        if (nStart.isNotEmpty()) return
        val start = IntArray(nv + 1)
        for (i in 0 until nt * 3) start[idx[i] + 1] += 2
        for (v in 0 until nv) start[v + 1] += start[v]
        val fill = start.copyOf(nv)
        val list = IntArray(nt * 6)
        for (t in 0 until nt) for (e in 0..2) {
            val v = idx[t * 3 + e]
            list[fill[v]++] = idx[t * 3 + (e + 1) % 3]
            list[fill[v]++] = idx[t * 3 + (e + 2) % 3]
        }
        nStart = start; nList = list
    }

    private fun room(verts: Int, tris: Int) {
        if (nv + verts > degree.size) {
            val n = (nv + verts) * 5 / 4 + 16
            positions = positions.copyOf(n * 3); colors = colors.copyOf(n * 3)
            degree = degree.copyOf(n); touched = touched.copyOf(n); flag = flag.copyOf(n)
        }
        if (nt + tris > alive.size) {
            val n = (nt + tris) * 5 / 4 + 16
            idx = idx.copyOf(n * 3); alive = alive.copyOf(n); uv = uv?.copyOf(n * 6)
        }
    }

    /**
     * Closes the holes left by the triangles in [gone], so the object behaves as if it was solid:
     * each hole's rim is joined to its middle through rings of new vertices, and the patch is
     * relaxed into a smooth skin, like the face left by a clean cut. Returns the triangles added.
     */
    private fun close(gone: IntArray): IntArray {
        fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()
        val cut = HashSet<Long>(gone.size * 4)
        for (t in gone) for (e in 0..2) cut.add(key(idx[t * 3 + e], idx[t * 3 + (e + 1) % 3]))
        // A rim edge: the triangle on its other side is still there.
        val rimT = IntList(); val rimE = IntList()
        val parent = HashMap<Int, Int>()
        fun find(x: Int): Int { var r = x; while (true) { val p = parent[r] ?: r; if (p == r) break; r = p }; return r }
        for (t in gone) for (e in 0..2) {
            val a = idx[t * 3 + e]; val b = idx[t * 3 + (e + 1) % 3]
            if (key(b, a) in cut || degree[a] == 0 || degree[b] == 0) continue
            rimT.add(t); rimE.add(e)
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        val ts = rimT.toArray(); val es = rimE.toArray()
        if (ts.isEmpty()) return IntArray(0)

        // One patch per rim. Its middle is the average of the rim; [rings] steps lead there, about
        // as far apart as the rim's own vertices, so the new triangles match the old in size.
        class Rim(val verts: ArrayList<Int> = ArrayList(), val sum: FloatArray = FloatArray(6)) {
            var rings = 1; var middle = -1
        }
        val rims = HashMap<Int, Rim>()
        val next = HashMap<Int, Int>(); val prev = HashMap<Int, Int>()
        for (i in ts.indices) {
            val a = idx[ts[i] * 3 + es[i]]; val b = idx[ts[i] * 3 + (es[i] + 1) % 3]
            next[a] = b; prev[b] = a
            val r = rims.getOrPut(find(a)) { Rim() }
            r.verts.add(a)
            for (c in 0..2) { r.sum[c] += positions[a * 3 + c]; r.sum[3 + c] += colors[a * 3 + c] }
        }
        var moreV = 0; var moreT = 0
        for (r in rims.values) {
            val n = r.verts.size
            if (n < 3) continue
            r.rings = (n / 6).coerceIn(1, 16)
            moreV += n * (r.rings - 1) + 1; moreT += n * (2 * r.rings - 1)
        }
        room(moreV, moreT)
        // chain[a]: the first of a's ring vertices (ring 1 .. rings - 1); ring 0 is a itself.
        val chain = HashMap<Int, Int>()
        for (r in rims.values) {
            val n = r.verts.size
            if (n < 3) continue
            val k = r.rings
            for (a in r.verts) {
                chain[a] = nv
                for (ring in 1 until k) {
                    val f = ring.toFloat() / k
                    for (c in 0..2) {
                        positions[nv * 3 + c] = positions[a * 3 + c] + f * (r.sum[c] / n - positions[a * 3 + c])
                        colors[nv * 3 + c] = colors[a * 3 + c] + f * (r.sum[3 + c] / n - colors[a * 3 + c])
                    }
                    degree[nv] = 0; nv++
                }
            }
            r.middle = nv
            for (c in 0..2) { positions[nv * 3 + c] = r.sum[c] / n; colors[nv * 3 + c] = r.sum[3 + c] / n }
            degree[nv] = 0; nv++
        }
        val added = IntList()
        fun tri(a: Int, b: Int, c: Int, from: Int, e: Int) {
            idx[nt * 3] = a; idx[nt * 3 + 1] = b; idx[nt * 3 + 2] = c
            uv?.let { u ->
                // The rim's own place in the texture, so the patch takes the color around it.
                val f = (e + 1) % 3
                u[nt * 6] = u[from * 6 + e * 2]; u[nt * 6 + 1] = u[from * 6 + e * 2 + 1]
                u[nt * 6 + 2] = u[from * 6 + f * 2]; u[nt * 6 + 3] = u[from * 6 + f * 2 + 1]
                u[nt * 6 + 4] = u[nt * 6]; u[nt * 6 + 5] = u[nt * 6 + 1]
            }
            alive[nt] = true
            degree[a]++; degree[b]++; degree[c]++
            added.add(nt); nt++
        }
        for (i in ts.indices) {
            val t = ts[i]; val e = es[i]
            val a = idx[t * 3 + e]; val b = idx[t * 3 + (e + 1) % 3]
            val r = rims[find(a)]!!
            if (r.middle < 0) continue
            val ca = chain[a]!!; val cb = chain[b]!!
            fun at(v: Int, c: Int, ring: Int) = if (ring == 0) v else c + ring - 1
            for (ring in 0 until r.rings - 1) {
                tri(at(a, ca, ring), at(b, cb, ring), at(b, cb, ring + 1), t, e)
                tri(at(a, ca, ring), at(b, cb, ring + 1), at(a, ca, ring + 1), t, e)
            }
            tri(at(a, ca, r.rings - 1), at(b, cb, r.rings - 1), r.middle, t, e)
        }
        // Relax the patch (the rim stays): every new vertex moves to the middle of its four
        // neighbours, which pulls the skin taut like a soap film.
        for (r in rims.values) {
            if (r.middle < 0 || r.rings < 2) continue
            val k = r.rings; val n = r.verts.size
            val tmp = FloatArray(n * (k - 1) * 3)
            repeat(k * 6) {
                var mx = 0f; var my = 0f; var mz = 0f
                for ((j, a) in r.verts.withIndex()) {
                    val ca = chain[a]!!
                    val cn = chain[next[a] ?: a] ?: ca; val cp = chain[prev[a] ?: a] ?: ca
                    for (ring in 1 until k) {
                        val inner = if (ring == k - 1) r.middle else ca + ring
                        val outer = if (ring == 1) a else ca + ring - 2
                        val o = (j * (k - 1) + ring - 1) * 3
                        for (c in 0..2) tmp[o + c] = (positions[inner * 3 + c] + positions[outer * 3 + c] +
                            positions[(cn + ring - 1) * 3 + c] + positions[(cp + ring - 1) * 3 + c]) / 4f
                    }
                    val last = (ca + k - 2) * 3
                    mx += positions[last]; my += positions[last + 1]; mz += positions[last + 2]
                }
                for ((j, a) in r.verts.withIndex()) {
                    val ca = chain[a]!!
                    for (ring in 1 until k) System.arraycopy(tmp, (j * (k - 1) + ring - 1) * 3, positions, (ca + ring - 1) * 3, 3)
                }
                positions[r.middle * 3] = mx / n; positions[r.middle * 3 + 1] = my / n; positions[r.middle * 3 + 2] = mz / n
            }
        }
        nStart = IntArray(0)  // the neighbours changed
        return added.toArray()
    }

    /**
     * Vertices under a round brush at ([x], [y]) with radius [r], all in view pixels, for the
     * camera [mvp]. Unless [through], only the surface facing the camera: what lies behind stays.
     */
    fun pick(mvp: FloatArray, w: Int, h: Int, x: Float, y: Float, r: Float, through: Boolean): IntArray {
        val hit = IntList(); val depth = FloatList()
        var near = Float.MAX_VALUE
        val r2 = r * r
        for (v in 0 until nv) {
            if (degree[v] == 0) continue
            val px = positions[v * 3]; val py = positions[v * 3 + 1]; val pz = positions[v * 3 + 2]
            val cw = mvp[3] * px + mvp[7] * py + mvp[11] * pz + mvp[15]
            if (cw <= 0f) continue
            val sx = ((mvp[0] * px + mvp[4] * py + mvp[8] * pz + mvp[12]) / cw * 0.5f + 0.5f) * w
            val sy = (0.5f - (mvp[1] * px + mvp[5] * py + mvp[9] * pz + mvp[13]) / cw * 0.5f) * h
            val dx = sx - x; val dy = sy - y
            if (dx * dx + dy * dy > r2) continue
            hit.add(v); depth.add(cw)
            if (cw < near) near = cw
        }
        val all = hit.toArray()
        if (through || all.isEmpty()) return all
        // One brush width deep, measured where the nearest hit is (the view angle is 40°).
        val reach = maxOf(r * 0.728f * near / h * 1.2f, 0.01f)
        val d = depth.toArray()
        val out = IntList()
        for (i in all.indices) if (d[i] <= near + reach) out.add(all[i])
        return out.toArray()
    }

    /** Removes every triangle that touches one of [verts]. */
    fun erase(verts: IntArray) {
        if (verts.isEmpty()) return
        for (v in verts) flag[v] = true
        var any = false
        for (t in 0 until nt) {
            if (!alive[t]) continue
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            if (flag[a] || flag[b] || flag[c]) { kill(t); any = true }
        }
        for (v in verts) flag[v] = false
        if (any) rebuild()
    }

    private fun kill(t: Int) {
        alive[t] = false; killed.add(t)
        for (e in 0..2) degree[idx[t * 3 + e]]--
    }

    /** Pulls [verts] toward the middle of their neighbours: bumps and spikes sink into the surface. */
    fun smooth(verts: IntArray) {
        if (verts.isEmpty()) return
        neighbours()
        for (v in verts) if (!touched[v]) {
            touched[v] = true; moved.add(v)
            old.add(positions[v * 3]); old.add(positions[v * 3 + 1]); old.add(positions[v * 3 + 2])
        }
        val next = FloatArray(verts.size * 3)
        repeat(3) {
            for ((i, v) in verts.withIndex()) {
                var sx = 0f; var sy = 0f; var sz = 0f
                val from = nStart[v]; val to = nStart[v + 1]
                for (k in from until to) { val n = nList[k]; sx += positions[n * 3]; sy += positions[n * 3 + 1]; sz += positions[n * 3 + 2] }
                val c = (to - from).toFloat()
                if (c == 0f) { next[i * 3] = positions[v * 3]; next[i * 3 + 1] = positions[v * 3 + 1]; next[i * 3 + 2] = positions[v * 3 + 2]; continue }
                next[i * 3] = positions[v * 3] + 0.6f * (sx / c - positions[v * 3])
                next[i * 3 + 1] = positions[v * 3 + 1] + 0.6f * (sy / c - positions[v * 3 + 1])
                next[i * 3 + 2] = positions[v * 3 + 2] + 0.6f * (sz / c - positions[v * 3 + 2])
            }
            for ((i, v) in verts.withIndex()) { positions[v * 3] = next[i * 3]; positions[v * 3 + 1] = next[i * 3 + 1]; positions[v * 3 + 2] = next[i * 3 + 2] }
        }
    }

    /** Closes the stroke in progress so that [undo] takes it back as one. */
    fun endStroke() {
        if (killed.size == 0 && moved.size == 0) return
        val m = moved.toArray()
        for (v in m) touched[v] = false
        val gone = killed.toArray()
        val added = if (gone.isNotEmpty()) close(gone) else IntArray(0)
        if (added.isNotEmpty()) rebuild()
        steps.add(Step(gone, added, m, old.toArray()))
        if (steps.size > 30) steps.removeAt(0)
        killed = IntList(); moved = IntList(); old = FloatList()
    }

    /** Keeps the biggest connected piece and removes the rest. Returns how many pieces went. */
    fun removeLoose(): Int {
        endStroke()
        val parent = IntArray(nv) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (t in 0 until nt) if (alive[t]) {
            val a = find(idx[t * 3]); val b = find(idx[t * 3 + 1]); val c = find(idx[t * 3 + 2])
            parent[b] = a; parent[c] = a
        }
        val size = HashMap<Int, Int>()
        for (t in 0 until nt) if (alive[t]) size.merge(find(idx[t * 3]), 1, Int::plus)
        val keep = size.maxByOrNull { it.value }?.key ?: return 0
        for (t in 0 until nt) if (alive[t] && find(idx[t * 3]) != keep) kill(t)
        if (killed.size > 0) rebuild()
        endStroke()
        return size.size - 1
    }

    /** Takes back the last stroke. False when there is nothing to take back. */
    fun undo(): Boolean {
        endStroke()
        if (steps.isEmpty()) return false
        val s = steps.removeAt(steps.size - 1)
        for (t in s.added) { alive[t] = false; for (e in 0..2) degree[idx[t * 3 + e]]-- }
        for (t in s.killed) { alive[t] = true; for (e in 0..2) degree[idx[t * 3 + e]]++ }
        for ((i, v) in s.moved.withIndex()) { positions[v * 3] = s.old[i * 3]; positions[v * 3 + 1] = s.old[i * 3 + 1]; positions[v * 3 + 2] = s.old[i * 3 + 2] }
        rebuild()
        return true
    }

    private fun rebuild() {
        var live = 0
        for (t in 0 until nt) if (alive[t]) live++
        val out = IntArray(live * 3)
        var k = 0
        for (t in 0 until nt) if (alive[t]) { out[k++] = idx[t * 3]; out[k++] = idx[t * 3 + 1]; out[k++] = idx[t * 3 + 2] }
        indices = out
    }

    /** Normals for [positions] as they are now. */
    fun normals(): FloatArray = MeshTools.normals(positions, indices)

    /** The cleaned mesh: unused vertices dropped, colors and texture carried over. */
    fun result(): Mesh {
        endStroke()
        val map = IntArray(nv) { -1 }
        var n = 0
        for (v in 0 until nv) if (degree[v] > 0) map[v] = n++
        val pos = FloatArray(n * 3); val col = FloatArray(n * 3)
        for (v in 0 until nv) {
            val m = map[v]; if (m < 0) continue
            for (c in 0..2) { pos[m * 3 + c] = positions[v * 3 + c]; col[m * 3 + c] = colors[v * 3 + c] }
        }
        var live = 0
        for (t in 0 until nt) if (alive[t]) live++
        val tri = IntArray(live * 3)
        val uvs = uv?.let { FloatArray(live * 6) }
        var k = 0
        for (t in 0 until nt) if (alive[t]) {
            for (e in 0..2) tri[k * 3 + e] = map[idx[t * 3 + e]]
            uvs?.let { System.arraycopy(uv!!, t * 6, it, k * 6, 6) }
            k++
        }
        return Mesh(pos, MeshTools.normals(pos, tri), col, tri).also {
            it.uvs = uvs; it.texture = if (uvs != null) src.texture else null; it.sizeMm = src.sizeMm
        }
    }
}
