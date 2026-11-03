package dev.namelsking.argumentcat.mascot

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Small procedural 3D rig: shared meshes, two lights, no model or texture downloads. */
internal class CatRenderer : GLSurfaceView.Renderer {
    @Volatile var pose = CatPose()
    private var program = 0
    private var position = 0; private var normal = 0
    private var openPosition = 0; private var openNormal = 0; private var morphUniform = 0
    private var modelUniform = 0; private var normalUniform = 0; private var vpUniform = 0
    private var colorUniform = 0; private var glossUniform = 0; private var furUniform = 0; private var irisUniform = 0
    private var headInverseUniform = 0
    private lateinit var sphere: CatMesh
    private lateinit var cylinder: CatMesh
    private lateinit var ear: CatMesh
    private lateinit var skull: CatMesh
    private lateinit var upperMuzzle: CatMesh
    private lateinit var lowerMuzzle: CatMesh
    private lateinit var mouth: CatMesh
    private lateinit var nose: CatMesh
    private lateinit var furMarks: CatMesh
    private val projection = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val model = FloatArray(16); private val inverse = FloatArray(16); private val normals = FloatArray(16)
    private val root = FloatArray(16); private val head = FloatArray(16)
    private val headInverse = FloatArray(16)
    private val fur = floatArrayOf(.94f,.59f,.31f)
    private val lightFur = floatArrayOf(1f,.70f,.43f)
    private val cream = floatArrayOf(.99f,.90f,.77f)
    private val stripe = floatArrayOf(.39f,.20f,.105f)
    private val ink = floatArrayOf(.07f,.075f,.075f)
    private val pink = floatArrayOf(.88f,.46f,.50f)
    private val iris = floatArrayOf(.24f,.53f,.43f)
    private val mouthColor = floatArrayOf(.075f,.035f,.032f)
    private var angry = 0f; private var proud = 0f; private var think = 0f; private var listen = 0f
    private var jaw = 0f
    private var last = 0L
    private val started = SystemClock.elapsedRealtime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = makeProgram(VERTEX, FRAGMENT)
        position = glGetAttribLocation(program,"aPosition"); normal = glGetAttribLocation(program,"aNormal")
        openPosition = glGetAttribLocation(program,"aOpenPosition"); openNormal = glGetAttribLocation(program,"aOpenNormal")
        morphUniform = glGetUniformLocation(program,"uMorph")
        modelUniform = glGetUniformLocation(program,"uModel"); normalUniform = glGetUniformLocation(program,"uNormal")
        vpUniform = glGetUniformLocation(program,"uVP"); colorUniform = glGetUniformLocation(program,"uColor")
        glossUniform = glGetUniformLocation(program,"uGloss"); furUniform = glGetUniformLocation(program,"uFur")
        irisUniform = glGetUniformLocation(program,"uIris"); headInverseUniform = glGetUniformLocation(program,"uHeadInverse")
        sphere = CatMesh.sphere(); cylinder = CatMesh.cylinder(); ear = CatMesh.ear()
        skull = CatMesh.head(); upperMuzzle = CatMesh.upperMuzzle(); lowerMuzzle = CatMesh.lowerMuzzle()
        mouth = CatMesh.mouth(); nose = CatMesh.nose(); furMarks = CatMesh.furMarks()
        glEnable(GL_DEPTH_TEST)
        glClearColor(16/255f,20/255f,28/255f,1f)
        Matrix.setLookAtM(view,0,0f,.35f,5.8f,0f,.22f,0f,0f,1f,0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,21.5f,width.toFloat()/height.coerceAtLeast(1),.1f,30f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.elapsedRealtime()
        val t = (now-started)/1000f
        val dt = if(last==0L) .033f else ((now-last)/1000f).coerceIn(.001f,.08f); last=now
        val state=pose
        fun ease(value: Float,target: Float,speed: Float=7f)=value+(target-value)*(1-exp(-dt*speed))
        angry=ease(angry,if(state.emotion=="annoyed")1f else 0f)
        proud=ease(proud,if(state.emotion=="happy")1f else 0f)
        think=ease(think,if(state.activity=="thinking")1f else 0f)
        listen=ease(listen,if(state.activity=="listening")1f else 0f)
        jaw=ease(jaw,if(state.activity=="speaking")state.speech.coerceIn(0f,1f) else 0f,20f)
        val pondering=think*(1f-max(angry,proud))
        val breathe=sin(t*1.9f)*.018f
        val blinkPhase=t%5.7f
        val blink=when { blinkPhase<.11f -> blinkPhase/.11f; blinkPhase<.22f -> 1-(blinkPhase-.11f)/.11f; blinkPhase in 5.03f..5.20f -> sin((blinkPhase-5.03f)/.17f*PI.toFloat()); else -> 0f }
        val open=((1-blink)*(.96f-angry*.18f-proud*.16f)).coerceAtLeast(.035f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(program)
        glEnableVertexAttribArray(position);glEnableVertexAttribArray(normal)
        glUniformMatrix4fv(vpUniform,1,false,vp,0)
        Matrix.setIdentityM(root,0)
        // A floating head keeps expressions large without taking space from the chat.
        Matrix.translateM(root,0,0f,breathe,0f)
        Matrix.rotateM(root,0,sin(t*.62f)*1.4f,0f,1f,0f)
        System.arraycopy(root,0,head,0,16)
        Matrix.translateM(head,0,0f,proud*.025f,0f)
        Matrix.rotateM(head,0,-5f+sin(t*.65f)*3f+pondering*13f+angry*11f,0f,1f,0f)
        Matrix.rotateM(head,0,listen*(10f+sin(t*1.4f)*2f)-pondering*7f+sin(t*.85f)*1.4f,0f,0f,1f)
        Matrix.rotateM(head,0,proud*(-5f+sin(t*1.7f)*1.8f)+pondering*3f,1f,0f,0f)
        Matrix.invertM(headInverse,0,head,0)
        glUniformMatrix4fv(headInverseUniform,1,false,headInverse,0)
        draw(skull,head,V(0f,0f,0f),V(1f,1f,1f),fur)
        ball(head,-.53f,-.17f,.12f,.31f,.30f,.39f,lightFur)
        ball(head,.53f,-.17f,.12f,.31f,.30f,.39f,lightFur)
        for(side in listOf(-1f,1f)) {
            val earBase=FloatArray(16);System.arraycopy(head,0,earBase,0,16)
            Matrix.translateM(earBase,0,side*.53f,.52f,-.02f)
            val twitch=exp(-(((t+if(side>0)1.7f else 0f)%8.2f)-.25f).pow(2)/.012f)*9f
            Matrix.rotateM(earBase,0,side*(angry*19f-listen*8f+twitch),0f,0f,1f)
            Matrix.rotateM(earBase,0,angry*24f-listen*12f,1f,0f,0f)
            draw(ear,earBase,V(0f,0f,0f),V(-side*.90f,.85f,1f),fur)
            draw(ear,earBase,V(0f,.025f,.13f),V(-side*.58f,.58f,.40f),pink,14f)

            // Layered eyeball, iris, pupil and highlights all follow the head rig.
            val x=side*.30f; val gaze=sin(t*.48f)*.018f+pondering*.035f
            val eyeLift=.12f
            ball(head,x,.025f+eyeLift,.505f,.240f,.245f*open,.105f,floatArrayOf(.57f,.35f,.21f),16f)
            ball(head,x,.025f+eyeLift,.532f,.225f,.230f*open,.095f,floatArrayOf(.99f,.96f,.85f),65f)
            ball(head,x+gaze,.022f+eyeLift+pondering*.025f,.610f,.154f,.198f*open,.043f,iris,95f)
            ball(head,x+gaze,.022f+eyeLift+pondering*.025f,.646f,.075f+listen*.012f,.170f*open,.016f,ink,100f)
            if(open>.12f) {
                ball(head,x-.042f+gaze,.096f+eyeLift,.665f,.042f,.047f*open,.013f,floatArrayOf(1f,.99f,.94f),120f)
                ball(head,x+.045f+gaze,-.025f+eyeLift,.665f,.016f,.020f*open,.008f,floatArrayOf(.75f,.92f,.87f),100f)
            }
        }
        draw(furMarks,head,V(0f,0f,0f),V(1f,1f,1f),floatArrayOf(.48f,.26f,.13f))
        draw(mouth,head,V(0f,0f,0f),V(1f,1f,1f),mouthColor,8f)
        draw(upperMuzzle,head,V(0f,0f,0f),V(1f,1f,1f),fur)
        draw(lowerMuzzle,head,V(0f,0f,0f),V(1f,1f,1f),fur)
        for(side in listOf(-1f,1f)) {
            for(i in 0..2) {
                val p=CatMesh.muzzlePoint(side*(.11f+i*.037f),-.14f-(i%2)*.045f)
                ball(head,p.x,p.y,p.z+.002f,.007f,.006f,.004f,stripe)
            }
            // Slightly curved whiskers, with a subtle twitch during listening.
            for(i in -1..1) {
                val p=CatMesh.muzzlePoint(side*.20f,-.18f+i*.036f)
                val start=V(p.x,p.y,p.z+.003f)
                val end=V(side*(.85f+abs(i)*.04f),-.18f+i*.13f+sin(t*2+i)*listen*.018f,.51f)
                val mid=V(side*.55f,-.18f+i*.085f,.64f)
                link(head,start,mid,.005f,floatArrayOf(.62f,.58f,.48f));link(head,mid,end,.003f,cream)
            }
        }
        draw(nose,head,V(0f,0f,0f),V(1f,1f,1f),pink,36f)
        for(side in listOf(-1f,1f)) {
            val p=CatMesh.muzzlePoint(side*.043f,-.104f)
            ball(head,p.x,p.y,p.z+.025f,.010f,.005f,.005f,mouthColor,28f)
        }
        glDisableVertexAttribArray(position);glDisableVertexAttribArray(normal)
        glDisableVertexAttribArray(openPosition);glDisableVertexAttribArray(openNormal)
    }

    private fun ball(base: FloatArray,x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,color:FloatArray,gloss:Float=12f) =
        draw(sphere,base,V(x,y,z),V(sx,sy,sz),color,gloss)

    private fun draw(mesh: CatMesh,base:FloatArray,p:V,s:V,color:FloatArray,gloss:Float=12f,rotation:Float=0f,axis:V=V(0f,1f,0f)) {
        System.arraycopy(base,0,model,0,16)
        Matrix.translateM(model,0,p.x,p.y,p.z)
        if(rotation!=0f) Matrix.rotateM(model,0,rotation,axis.x,axis.y,axis.z)
        Matrix.scaleM(model,0,s.x,s.y,s.z)
        Matrix.invertM(inverse,0,model,0);Matrix.transposeM(normals,0,inverse,0)
        glUniformMatrix4fv(modelUniform,1,false,model,0);glUniformMatrix4fv(normalUniform,1,false,normals,0)
        glUniform3fv(colorUniform,1,color,0);glUniform1f(glossUniform,gloss)
        glUniform1f(furUniform,if(color===fur)1f else 0f)
        glUniform1f(irisUniform,0f)
        glUniform1f(morphUniform,if(mesh.morphing)jaw else 0f)
        mesh.draw(position,normal,openPosition,openNormal)
    }

    private fun link(base:FloatArray,a:V,b:V,r:Float,color:FloatArray) {
        val d=b-a;val length=sqrt(d.x*d.x+d.y*d.y+d.z*d.z)
        if(length<.0001f)return
        val axis=V(d.z,0f,-d.x)
        val angle=acos((d.y/length).coerceIn(-1f,1f))*180f/PI.toFloat()
        draw(cylinder,base,a,V(r,length,r),color,12f,angle,if(abs(axis.x)+abs(axis.z)<.0001f)V(1f,0f,0f) else axis)
        ball(base,a.x,a.y,a.z,r,r,r,color)
    }

    private fun makeProgram(vertex:String,fragment:String):Int {
        fun shader(type:Int,source:String):Int {
            val id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id)
            val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0){glGetShaderInfoLog(id)};return id
        }
        val v=shader(GL_VERTEX_SHADER,vertex);val f=shader(GL_FRAGMENT_SHADER,fragment)
        val p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p)
        val ok=IntArray(1);glGetProgramiv(p,GL_LINK_STATUS,ok,0);check(ok[0]!=0){glGetProgramInfoLog(p)}
        glDeleteShader(v);glDeleteShader(f);return p
    }

    companion object {
        private const val VERTEX="""
            attribute vec3 aPosition; attribute vec3 aNormal;
            attribute vec3 aOpenPosition; attribute vec3 aOpenNormal; uniform float uMorph;
            uniform mat4 uModel; uniform mat4 uNormal; uniform mat4 uVP; uniform mat4 uHeadInverse;
            varying vec3 vPosition; varying vec3 vNormal; varying vec3 vMeshPosition; varying vec3 vHeadPosition;
            void main(){ vec3 p=mix(aPosition,aOpenPosition,uMorph);
                vec4 world=uModel*vec4(p,1.0); vPosition=world.xyz; vMeshPosition=p;
                vHeadPosition=(uHeadInverse*uModel*vec4(aPosition,1.0)).xyz;
                vNormal=normalize(mat3(uNormal)*mix(aNormal,aOpenNormal,uMorph)); gl_Position=uVP*world; }
        """
        private const val FRAGMENT="""
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec3 vPosition; varying vec3 vNormal; varying vec3 vMeshPosition; varying vec3 vHeadPosition;
            uniform vec3 uColor; uniform float uGloss; uniform float uFur; uniform float uIris;
            float hash(vec3 p){p=fract(p*0.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
            float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
                return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
                    mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
            void main(){
                vec3 n=normalize(vNormal); vec3 eye=normalize(vec3(0.0,0.35,5.8)-vPosition);
                vec3 base=uColor;
                if(uFur>0.5){
                    vec3 p=vHeadPosition;
                    float fine=noise(p*vec3(150.0,650.0,150.0));
                    float mottling=noise(p*22.0);
                    vec2 patchPosition=vec2(p.x/0.32,(p.y+0.22)/0.23);
                    float patch=dot(patchPosition,patchPosition);
                    float cream=(1.0-smoothstep(0.72,1.05,patch))*smoothstep(0.22,0.48,p.z);
                    base=mix(base,vec3(0.99,0.90,0.77),cream);
                    base*=0.975+0.035*fine+0.025*mottling;
                }
                if(uIris>0.5){
                    float r=length(vMeshPosition.xy);float a=atan(vMeshPosition.y,vMeshPosition.x);
                    float fibers=sin(a*61.0+sin(r*11.0)*0.7);
                    base=mix(vec3(0.49,0.43,0.20),base,smoothstep(0.15,0.65,r));
                    base*=0.85+0.13*fibers;
                    base*=1.0-0.65*smoothstep(0.78,0.98,r);
                }
                vec3 key=normalize(vec3(-0.5,0.75,0.9)); vec3 fill=normalize(vec3(0.8,0.3,0.7));
                float k=max(dot(n,key),0.0); float f=max(dot(n,fill),0.0);
                float rim=pow(1.0-max(dot(n,eye),0.0),3.0)*max(dot(n,normalize(vec3(0.1,0.7,-1.0))),0.0);
                float spec=pow(max(dot(n,normalize(key+eye)),0.0),uGloss);
                vec3 color=base*(0.38+0.57*k+0.20*f);
                color+=vec3(0.25,0.32,0.30)*rim+vec3(1.0,0.94,0.84)*spec*(uFur>0.5?0.018:(uGloss>40.0?0.30:0.04));
                gl_FragColor=vec4(color,1.0);
            }
        """
    }
}
