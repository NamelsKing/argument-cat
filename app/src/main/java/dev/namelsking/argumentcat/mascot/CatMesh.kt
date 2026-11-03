package dev.namelsking.argumentcat.mascot

import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

internal data class V(val x: Float, val y: Float, val z: Float) {
    operator fun plus(v: V) = V(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: V) = V(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = V(x * s, y * s, z * s)
    fun cross(v: V) = V(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)
    fun unit(): V { val n = sqrt(x*x + y*y + z*z).coerceAtLeast(1e-12f); return V(x/n, y/n, z/n) }
}

internal class CatMesh(vertices: FloatArray, opened: FloatArray? = null) {
    private val buffer = upload(vertices)
    private val openBuffer = opened?.also { require(it.size == vertices.size) }?.let(::upload)
    val morphing get() = openBuffer != null
    private val count = vertices.size / 6
    private fun upload(vertices: FloatArray): Int {
        val bytes = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        bytes.put(vertices).position(0)
        val ids = IntArray(1); glGenBuffers(1, ids, 0)
        glBindBuffer(GL_ARRAY_BUFFER, ids[0])
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, bytes, GL_STATIC_DRAW)
        return ids[0]
    }
    fun draw(position: Int, normal: Int, openPosition: Int, openNormal: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer)
        glVertexAttribPointer(position, 3, GL_FLOAT, false, 24, 0)
        glVertexAttribPointer(normal, 3, GL_FLOAT, false, 24, 12)
        if (openBuffer != null) {
            glBindBuffer(GL_ARRAY_BUFFER, openBuffer)
            glEnableVertexAttribArray(openPosition); glEnableVertexAttribArray(openNormal)
            glVertexAttribPointer(openPosition, 3, GL_FLOAT, false, 24, 0)
            glVertexAttribPointer(openNormal, 3, GL_FLOAT, false, 24, 12)
        } else {
            glDisableVertexAttribArray(openPosition); glDisableVertexAttribArray(openNormal)
            glVertexAttrib3f(openPosition, 0f, 0f, 0f); glVertexAttrib3f(openNormal, 0f, 0f, 1f)
        }
        glDrawArrays(GL_TRIANGLES, 0, count)
    }

    companion object {
        private fun surface(columns: Int, rows: Int, radial: Boolean = false, point: (Float, Float) -> V): FloatArray {
            val out = ArrayList<Float>()
            fun vertex(u: Float, v: Float) {
                val p = point(u, v)
                val du = point((u + .0005f).coerceAtMost(1f), v) - point((u - .0005f).coerceAtLeast(0f), v)
                val dv = point(u, (v + .0005f).coerceAtMost(1f)) - point(u, (v - .0005f).coerceAtLeast(0f))
                var n = du.cross(dv)
                if (n.x*n.x + n.y*n.y + n.z*n.z < 1e-16f) n = if (radial) p else V(0f, 0f, 1f)
                if (if (radial) n.x*p.x + n.y*p.y + n.z*p.z < 0f else n.z < 0f) n = n * -1f
                n = n.unit()
                out.addAll(listOf(p.x, p.y, p.z, n.x, n.y, n.z))
            }
            for (y in 0 until rows) for (x in 0 until columns) {
                val a=x/columns.toFloat(); val b=(x+1)/columns.toFloat()
                val c=y/rows.toFloat(); val d=(y+1)/rows.toFloat()
                vertex(a,c); vertex(a,d); vertex(b,c)
                vertex(b,c); vertex(a,d); vertex(b,d)
            }
            return out.toFloatArray()
        }

        private fun headDepth(x: Float, y: Float): Float {
            return .60f * sqrt((1f-x*x/.64f-y*y/.4761f).coerceAtLeast(.015f))
        }

        fun head() = CatMesh(surface(56, 36, radial = true) { u, v ->
            val a=u*2f*PI.toFloat(); val b=v*PI.toFloat()
            V(sin(b)*cos(a)*.80f,cos(b)*.69f,sin(b)*sin(a)*.60f)
        })

        // The same edge defines upper skin, lower skin and the mouth cavity.
        // No independent painted smile can drift above the moving mouth.
        private fun lip(u: Float): V {
            val a=abs(u); val x=u*.21f
            val y=-.235f-.022f*sin(a*PI.toFloat())
            val envelope=sqrt((1f-u*u).coerceAtLeast(0f))
            return V(x,y,headDepth(x,y)+.003f+.025f*envelope)
        }

        private fun lowerLip(u: Float, jaw: Float): V {
            // A small V-shaped opening, rather than two broad horizontal lip rolls.
            val edge=lip(u); val envelope=(1f-abs(u)).coerceAtLeast(0f).pow(.8f)
            return V(edge.x,edge.y-(.002f+jaw*.16f)*envelope,edge.z-jaw*.014f*envelope)
        }

        private fun upperPoint(u: Float, v: Float): V {
            val edge=lip(u); val envelope=sqrt((1f-u*u).coerceAtLeast(0f))
            val y=edge.y+v*.19f*envelope
            val blend=(1f-v)*(1f-v)*(1f+2f*v)
            val arch=sin(v*PI.toFloat()).pow(2)
            val groove=.007f*exp(-u*u/.004f)*arch
            val z=headDepth(edge.x,y)+(.003f+.025f*envelope)*blend+.028f*envelope*arch-groove-.002f*v.pow(4)
            return V(edge.x,y,z)
        }

        fun upperMuzzle() = CatMesh(surface(48, 16) { s, v -> upperPoint(s*2f-1f,v) })

        fun muzzlePoint(x: Float, y: Float): V {
            val u=(x/.21f).coerceIn(-.99f,.99f);val edge=lip(u)
            val height=.19f*sqrt(1f-u*u)
            return upperPoint(u,((y-edge.y)/height).coerceIn(0f,1f))
        }

        fun lowerMuzzle(): CatMesh {
            fun shape(jaw: Float) = surface(48, 14) { s, v ->
                val u=s*2f-1f; val edge=lowerLip(u,jaw); val envelope=(1f-abs(u)).coerceAtLeast(0f)
                val x=edge.x*(1f-v*.50f)
                val y=edge.y-v*.10f*envelope
                val base=headDepth(x,y)
                val blend=(1f-v)*(1f-v)*(1f+2f*v)
                val depth=(edge.z-headDepth(edge.x,edge.y))*blend
                V(x,y,base+depth-.002f*v.pow(4))
            }
            return CatMesh(shape(0f),shape(1f))
        }

        fun mouth(): CatMesh {
            fun shape(jaw: Float) = surface(48, 8) { s, v ->
                val u=s*2f-1f; val top=lip(u); val bottom=lowerLip(u,jaw)
                val envelope=sqrt((1f-u*u).coerceAtLeast(0f))
                V(top.x,top.y*(1f-v)+bottom.y*v,
                    top.z*(1f-v)+bottom.z*v-.002f-.003f*sin(v*PI.toFloat())*envelope)
            }
            return CatMesh(shape(0f),shape(1f))
        }

        fun nose(): CatMesh {
            var points=listOf(V(-.073f,.026f,0f),V(-.060f,.042f,0f),V(0f,.033f,0f),
                V(.060f,.042f,0f),V(.073f,.026f,0f),V(.041f,-.018f,0f),V(0f,-.053f,0f),V(-.041f,-.018f,0f))
            repeat(2) {
                points=points.indices.flatMap { i ->
                    val a=points[i]; val b=points[(i+1)%points.size]
                    listOf(a*.75f+b*.25f,a*.25f+b*.75f)
                }
            }
            points=points.map { val p=muzzlePoint(it.x,it.y-.105f); V(p.x,p.y,p.z+.003f) }
            val center=V(0f,-.105f,points.maxOf { it.z }+.018f)
            val faces=points.indices.map { i ->
                var n=(points[i]-center).cross(points[(i+1)%points.size]-center).unit()
                if(n.z<0f)n=n*-1f
                n
            }
            val centerNormal=faces.reduce { a,b -> a+b }.unit()
            val out=ArrayList<Float>()
            for(i in points.indices) {
                val j=(i+1)%points.size
                val normals=listOf(centerNormal,(faces[(i+points.size-1)%points.size]+faces[i]).unit(),(faces[i]+faces[j]).unit())
                for((k,p) in listOf(center,points[i],points[j]).withIndex()) {
                    val n=normals[k]
                    out.addAll(listOf(p.x,p.y,p.z,n.x,n.y,n.z))
                }
            }
            return CatMesh(out.toFloatArray())
        }

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
            ribbon(listOf(V(0f,.57f,0f),V(0f,.43f,0f)),.025f)
            for(side in listOf(-1f,1f)) ribbon(listOf(V(side*.22f,.54f,0f),V(side*.15f,.43f,0f)),.020f)
            for(side in listOf(-1f,1f)) for(i in 0..2) {
                ribbon(listOf(V(side*.73f,.01f-i*.105f,0f),V(side*.63f,-.025f-i*.105f,0f)),.017f)
            }
            return CatMesh(out.toFloatArray())
        }
    }
}
