package dev.namelsking.argumentcat.mascot

import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal data class V(val x: Float, val y: Float, val z: Float) {
    operator fun minus(v: V) = V(x - v.x, y - v.y, z - v.z)
    fun cross(v: V) = V(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun unit(): V { val n = sqrt(x*x + y*y + z*z).coerceAtLeast(.00001f); return V(x/n, y/n, z/n) }
}

internal class CatMesh(vertices: FloatArray) {
    private val buffer: Int
    private val count = vertices.size / 6
    init {
        val bytes = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        val ids = IntArray(1); glGenBuffers(1, ids, 0); buffer = ids[0]
        glBindBuffer(GL_ARRAY_BUFFER, buffer)
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, bytes, GL_STATIC_DRAW)
    }
    fun draw(position: Int, normal: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer)
        glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0)
        glVertexAttribPointer(normal, 3, GL_FLOAT, false, 24, 12)
        glDrawArrays(GL_TRIANGLES, 0, count)
    }

    companion object {
        fun sphere(): CatMesh {
            val out = ArrayList<Float>()
            fun point(u: Float, v: Float): V {
                val a = u * PI.toFloat() * 2; val b = v * PI.toFloat()
                return V(sin(b)*cos(a), cos(b), sin(b)*sin(a))
            }
            fun add(v: V) { out.addAll(listOf(v.x,v.y,v.z,v.x,v.y,v.z)) }
            for (y in 0 until 20) for (x in 0 until 28) {
                val a=point(x/28f,y/20f); val b=point((x+1)/28f,y/20f)
                val c=point(x/28f,(y+1)/20f); val d=point((x+1)/28f,(y+1)/20f)
                listOf(a,c,b,b,c,d).forEach(::add)
            }
            return CatMesh(out.toFloatArray())
        }
        fun cylinder(): CatMesh {
            val out = ArrayList<Float>()
            fun add(a: Float, y: Float) { val x=cos(a);val z=sin(a);out.addAll(listOf(x,y,z,x,0f,z)) }
            for (i in 0 until 14) {
                val a=i*2*PI.toFloat()/14;val b=(i+1)*2*PI.toFloat()/14
                add(a,0f);add(a,1f);add(b,0f);add(b,0f);add(a,1f);add(b,1f)
            }
            return CatMesh(out.toFloatArray())
        }
        fun ear(): CatMesh {
            val out = ArrayList<Float>()
            val edge = ArrayList<V>()
            fun curve(a:V,b:V,c:V,d:V) {
                for(i in 0 until 7) {
                    val t=i/7f; val u=1-t
                    edge.add(V(u*u*u*a.x+3*u*u*t*b.x+3*u*t*t*c.x+t*t*t*d.x,
                        u*u*u*a.y+3*u*u*t*b.y+3*u*t*t*c.y+t*t*t*d.y,.035f))
                }
            }
            curve(V(-.30f,-.2f,0f),V(-.36f,.02f,0f),V(-.29f,.35f,0f),V(-.17f,.49f,0f))
            curve(V(-.17f,.49f,0f),V(-.14f,.55f,0f),V(-.06f,.55f,0f),V(-.02f,.49f,0f))
            curve(V(-.02f,.49f,0f),V(.10f,.28f,0f),V(.25f,-.02f,0f),V(.30f,-.2f,0f))
            curve(V(.30f,-.2f,0f),V(.12f,-.26f,0f),V(-.16f,-.26f,0f),V(-.30f,-.2f,0f))
            fun vertex(v:V,n:V) { out.addAll(listOf(v.x,v.y,v.z,n.x,n.y,n.z)) }
            for(i in edge.indices) {
                val a=edge[i]; val b=edge[(i+1)%edge.size]
                vertex(V(-.025f,.06f,.21f),V(0f,0f,1f))
                vertex(a,V(a.x*.9f,a.y*.7f,1f).unit());vertex(b,V(b.x*.9f,b.y*.7f,1f).unit())
                val backA=V(a.x,a.y,-.15f);val backB=V(b.x,b.y,-.15f)
                val side=V((a.x+b.x)/2,(a.y+b.y)/2,0f).unit()
                for(v in listOf(a,backA,b,b,backA,backB))vertex(v,side)
                for(v in listOf(V(0f,.06f,-.17f),backB,backA))vertex(v,V(0f,0f,-1f))
            }
            return CatMesh(out.toFloatArray())
        }
        fun furMarks(): CatMesh {
            val out=ArrayList<Float>()
            fun surface(x:Float,y:Float):V = V(x,y,.60f*sqrt((1-x*x/.64f-y*y/.4761f).coerceAtLeast(.025f))+.004f)
            fun vertex(v:V) {
                val n=V(v.x/.64f,v.y/.4761f,v.z/.36f).unit()
                out.addAll(listOf(v.x,v.y,v.z,n.x,n.y,n.z))
            }
            fun ribbon(points:List<V>,width:Float) {
                for(i in 0 until points.lastIndex) for(step in 0 until 12) {
                    val from=points[i]; val to=points[i+1]
                    fun at(t:Float)=V(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t,0f)
                    val a=at(step/12f); val b=at((step+1)/12f); val d=b-a
                    val length=sqrt(d.x*d.x+d.y*d.y);val dx=-d.y/length*width;val dy=d.x/length*width
                    val al=surface(a.x+dx,a.y+dy);val ar=surface(a.x-dx,a.y-dy)
                    val bl=surface(b.x+dx,b.y+dy);val br=surface(b.x-dx,b.y-dy)
                    listOf(al,ar,bl,bl,ar,br).forEach(::vertex)
                }
            }
            ribbon(listOf(V(-.33f,.44f,0f),V(-.18f,.33f,0f),V(0f,.51f,0f),V(.18f,.33f,0f),V(.33f,.44f,0f)),.020f)
            for(side in listOf(-1f,1f)) for(i in 0..2) {
                ribbon(listOf(V(side*.73f,.01f-i*.105f,0f),V(side*.63f,-.025f-i*.105f,0f)),.017f)
            }
            return CatMesh(out.toFloatArray())
        }
    }
}
