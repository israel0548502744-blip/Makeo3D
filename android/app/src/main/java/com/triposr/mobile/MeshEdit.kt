package com.triposr.mobile

/**
 * Touch-up of a finished mesh: rub parts away, flatten bumps and spikes, drop loose pieces.
 * Works on a copy; [result] gives the cleaned mesh. Each stroke can be undone.
 */
class MeshEditor(private val src: Mesh) {
    /** Vertex positions as edited so far (changed in place by [smooth]). */
    val positions: FloatArray = src.positions.copyOf()
    private val idx = src.indices
    private val alive = BooleanArray(src.triangleCount) { true }
    /** How many live triangles use each vertex; 0 = the vertex is gone. */
    private val degree = IntArray(src.vertexCount).also { d -> for (v in idx) d[v]++ }
    /** The triangles still there. A new array after every change. */
    @Volatile var indices: IntArray = idx; private set

    private class Step(val killed: IntArray, val moved: IntArray, val old: FloatArray)
    private val steps = ArrayList<Step>()
    private var killed = IntList()
    private var moved = IntList()
    private var old = FloatList()
    private val touched = BooleanArray(src.vertexCount)
    private val flag = BooleanArray(src.vertexCount)

    val changed get() = steps.isNotEmpty()

    // Neighbours of each vertex (built when first needed): nList[nStart[v] until nStart[v + 1]].
    private var nStart = IntArray(0)
    private var nList = IntArray(0)

    private fun neighbours() {
        if (nStart.isNotEmpty()) return
        val n = src.vertexCount
        val start = IntArray(n + 1)
        for (v in idx) start[v + 1] += 2
        for (v in 0 until n) start[v + 1] += start[v]
        val fill = start.copyOf(n)
        val list = IntArray(idx.size * 2)
        for (t in 0 until idx.size / 3) for (e in 0..2) {
            val v = idx[t * 3 + e]
            list[fill[v]++] = idx[t * 3 + (e + 1) % 3]
            list[fill[v]++] = idx[t * 3 + (e + 2) % 3]
        }
        nStart = start; nList = list
    }

    /**
     * Vertices under a round brush at ([x], [y]) with radius [r], all in view pixels, for the
     * camera [mvp]. Unless [through], only the surface facing the camera: what lies behind stays.
     */
    fun pick(mvp: FloatArray, w: Int, h: Int, x: Float, y: Float, r: Float, through: Boolean): IntArray {
        val hit = IntList(); val depth = FloatList()
        var near = Float.MAX_VALUE
        val r2 = r * r
        for (v in degree.indices) {
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
        for (t in alive.indices) {
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
        steps.add(Step(killed.toArray(), m, old.toArray()))
        if (steps.size > 30) steps.removeAt(0)
        killed = IntList(); moved = IntList(); old = FloatList()
    }

    /** Keeps the biggest connected piece and removes the rest. Returns how many pieces went. */
    fun removeLoose(): Int {
        endStroke()
        val parent = IntArray(src.vertexCount) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (t in alive.indices) if (alive[t]) {
            val a = find(idx[t * 3]); val b = find(idx[t * 3 + 1]); val c = find(idx[t * 3 + 2])
            parent[b] = a; parent[c] = a
        }
        val size = HashMap<Int, Int>()
        for (t in alive.indices) if (alive[t]) size.merge(find(idx[t * 3]), 1, Int::plus)
        val keep = size.maxByOrNull { it.value }?.key ?: return 0
        for (t in alive.indices) if (alive[t] && find(idx[t * 3]) != keep) kill(t)
        if (killed.size > 0) rebuild()
        endStroke()
        return size.size - 1
    }

    /** Takes back the last stroke. False when there is nothing to take back. */
    fun undo(): Boolean {
        endStroke()
        if (steps.isEmpty()) return false
        val s = steps.removeAt(steps.size - 1)
        for (t in s.killed) { alive[t] = true; for (e in 0..2) degree[idx[t * 3 + e]]++ }
        for ((i, v) in s.moved.withIndex()) { positions[v * 3] = s.old[i * 3]; positions[v * 3 + 1] = s.old[i * 3 + 1]; positions[v * 3 + 2] = s.old[i * 3 + 2] }
        rebuild()
        return true
    }

    private fun rebuild() {
        val out = IntArray(alive.count { it } * 3)
        var k = 0
        for (t in alive.indices) if (alive[t]) { out[k++] = idx[t * 3]; out[k++] = idx[t * 3 + 1]; out[k++] = idx[t * 3 + 2] }
        indices = out
    }

    /** Normals for [positions] as they are now. */
    fun normals(): FloatArray = MeshTools.normals(positions, idx)

    /** The cleaned mesh: unused vertices dropped, colors and texture carried over. */
    fun result(): Mesh {
        endStroke()
        val map = IntArray(src.vertexCount) { -1 }
        var n = 0
        for (v in degree.indices) if (degree[v] > 0) map[v] = n++
        val pos = FloatArray(n * 3); val col = FloatArray(n * 3)
        for (v in map.indices) {
            val m = map[v]; if (m < 0) continue
            for (c in 0..2) { pos[m * 3 + c] = positions[v * 3 + c]; col[m * 3 + c] = src.colors[v * 3 + c] }
        }
        val live = alive.count { it }
        val tri = IntArray(live * 3)
        val uv = src.uvs?.let { FloatArray(live * 6) }
        var k = 0
        for (t in alive.indices) if (alive[t]) {
            for (e in 0..2) tri[k * 3 + e] = map[idx[t * 3 + e]]
            uv?.let { System.arraycopy(src.uvs!!, t * 6, it, k * 6, 6) }
            k++
        }
        return Mesh(pos, MeshTools.normals(pos, tri), col, tri).also {
            it.uvs = uv; it.texture = if (uv != null) src.texture else null; it.sizeMm = src.sizeMm
        }
    }
}
