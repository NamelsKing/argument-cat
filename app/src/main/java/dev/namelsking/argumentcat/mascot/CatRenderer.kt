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
    private var modelUniform = 0; private var normalUniform = 0; private var vpUniform = 0
    private var colorUniform = 0; private var glossUniform = 0; private var furUniform = 0
    private lateinit var sphere: CatMesh
    private lateinit var cylinder: CatMesh
    private lateinit var ear: CatMesh
    private lateinit var furMarks: CatMesh
    private val projection = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val model = FloatArray(16); private val inverse = FloatArray(16); private val normals = FloatArray(16)
    private val root = FloatArray(16); private val head = FloatArray(16)
    private val fur = floatArrayOf(.88f,.46f,.20f)
    private val lightFur = floatArrayOf(.98f,.62f,.30f)
    private val cream = floatArrayOf(.99f,.86f,.65f)
    private val stripe = floatArrayOf(.39f,.20f,.105f)
    private val ink = floatArrayOf(.07f,.075f,.075f)
    private val pink = floatArrayOf(.77f,.34f,.35f)
    private var angry = 0f; private var proud = 0f; private var think = 0f; private var listen = 0f
    private var jaw = 0f
    private var last = 0L
    private val started = SystemClock.elapsedRealtime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = makeProgram(VERTEX, FRAGMENT)
        position = glGetAttribLocation(program,"aPosition"); normal = glGetAttribLocation(program,"aNormal")
        modelUniform = glGetUniformLocation(program,"uModel"); normalUniform = glGetUniformLocation(program,"uNormal")
        vpUniform = glGetUniformLocation(program,"uVP"); colorUniform = glGetUniformLocation(program,"uColor")
        glossUniform = glGetUniformLocation(program,"uGloss"); furUniform = glGetUniformLocation(program,"uFur")
        sphere = CatMesh.sphere(); cylinder = CatMesh.cylinder(); ear = CatMesh.ear(); furMarks = CatMesh.furMarks()
        glEnable(GL_DEPTH_TEST)
        glClearColor(16/255f,20/255f,28/255f,1f)
        Matrix.setLookAtM(view,0,0f,.35f,5.8f,0f,.13f,0f,0f,1f,0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,29f,width.toFloat()/height.coerceAtLeast(1),.1f,30f)
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
        val open=((1-blink)*(.96f-angry*.30f-proud*.24f)).coerceAtLeast(.035f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(program)
        glEnableVertexAttribArray(position);glEnableVertexAttribArray(normal)
        glUniformMatrix4fv(vpUniform,1,false,vp,0)
        Matrix.setIdentityM(root,0)
        ball(root,0f,-1.12f,0f,.87f,.025f,.49f,floatArrayOf(.027f,.04f,.052f),1f)

        // Curved tail behind the silhouette. Irritation produces a sharper, faster flick.
        val tail=ArrayList<V>()
        for(i in 0..13) {
            val u=i/13f; val a=u*3.7f
            tail.add(V(.39f+.63f*sin(a*.66f)+sin(t*(1.3f+angry*4f)+u*2)*u*.055f,
                -.94f+u*.92f,-.30f-.12f*cos(a)))
        }
        for(i in 0 until tail.lastIndex) link(root,tail[i],tail[i+1],.115f-i*.0032f,if(i in listOf(3,7,11))stripe else fur)
        ball(root,tail.last().x,tail.last().y,tail.last().z,.075f,.075f,.075f,cream)

        Matrix.translateM(root,0,0f,breathe,0f)
        Matrix.rotateM(root,0,sin(t*.62f)*1.4f,0f,1f,0f)
        ball(root,0f,-.55f,-.13f,.54f,.62f+proud*.025f,.39f,fur)
        ball(root,-.34f,-.85f,-.11f,.30f,.29f,.35f,fur)
        ball(root,.34f,-.85f,-.11f,.30f,.29f,.35f,fur)
        ball(root,0f,-.56f,.20f,.34f,.45f,.19f,cream)
        for(side in listOf(-1f,1f)) {
            val paw=V(side*(.37f-angry*.62f),-.97f+pondering*(if(side<0).90f else .02f)+angry*(if(side<0).565f else .485f)+proud*.025f*sin(t*3f+side),.34f+pondering*.17f+angry*(if(side<0).23f else .19f))
            link(root,V(side*.39f,-.27f,.03f),paw,.15f,lightFur)
            ball(root,paw.x,paw.y,paw.z,.185f,.15f,.21f,lightFur)
            for(toe in -1..1) ball(root,paw.x+toe*.075f,paw.y-.01f,paw.z+.13f,.057f,.055f,.075f,lightFur)
        }
        // Collar and a small tag give the bust a consistent character silhouette.
        ball(root,0f,-.14f,.02f,.46f,.105f,.38f,floatArrayOf(.08f,.37f,.34f),28f)
        ball(root,0f,-.26f,.385f,.075f,.09f,.035f,floatArrayOf(.91f,.65f,.24f),55f)

        System.arraycopy(root,0,head,0,16)
        Matrix.translateM(head,0,0f,.40f+proud*.025f,0f)
        Matrix.rotateM(head,0,-5f+sin(t*.65f)*3f+pondering*13f+angry*11f,0f,1f,0f)
        Matrix.rotateM(head,0,listen*(10f+sin(t*1.4f)*2f)-pondering*7f+sin(t*.85f)*1.4f,0f,0f,1f)
        Matrix.rotateM(head,0,proud*(-5f+sin(t*1.7f)*1.8f)+pondering*3f,1f,0f,0f)
        ball(head,0f,0f,0f,.80f,.69f,.60f,fur)
        ball(head,-.54f,-.19f,.12f,.30f,.29f,.39f,lightFur)
        ball(head,.54f,-.19f,.12f,.30f,.29f,.39f,lightFur)
        for(side in listOf(-1f,1f)) {
            val earBase=FloatArray(16);System.arraycopy(head,0,earBase,0,16)
            Matrix.translateM(earBase,0,side*.53f,.52f,-.02f)
            val twitch=exp(-(((t+if(side>0)1.7f else 0f)%8.2f)-.25f).pow(2)/.012f)*9f
            Matrix.rotateM(earBase,0,side*(angry*19f-listen*8f+twitch),0f,0f,1f)
            Matrix.rotateM(earBase,0,angry*24f-listen*12f,1f,0f,0f)
            draw(ear,earBase,V(0f,0f,0f),V(-side,1f,1f),fur)
            draw(ear,earBase,V(0f,.025f,.13f),V(-side*.64f,.69f,.40f),pink,14f)

            // Layered eyeball, iris, pupil and highlights all follow the head rig.
            val x=side*.315f; val gaze=sin(t*.48f)*.018f+pondering*.035f
            ball(head,x,.065f,.505f,.211f,.218f*open,.105f,floatArrayOf(.50f,.27f,.13f),16f)
            ball(head,x,.065f,.532f,.194f,.200f*open,.095f,floatArrayOf(.93f,.90f,.77f),65f)
            ball(head,x+gaze,.062f+pondering*.025f,.610f,.129f,.173f*open,.043f,floatArrayOf(.30f,.54f,.37f),95f)
            ball(head,x+gaze,.062f+pondering*.025f,.646f,.039f+listen*.012f,.145f*open,.016f,ink,100f)
            if(open>.12f) {
                ball(head,x-.042f+gaze,.126f,.665f,.036f,.042f*open,.013f,floatArrayOf(1f,.99f,.94f),120f)
                ball(head,x+.04f+gaze,.015f,.665f,.014f,.017f*open,.008f,floatArrayOf(.75f,.92f,.87f),100f)
            }
            val innerY=.32f-angry*.13f+proud*.025f
            link(head,V(side*.17f,innerY,.545f),V(side*.48f,.32f+angry*.07f,.455f),.011f,stripe)
        }
        draw(furMarks,head,V(0f,0f,0f),V(1f,1f,1f),floatArrayOf(.48f,.26f,.13f))

        val mouth=.025f+jaw*.16f
        if(jaw>.04f) ball(head,0f,-.275f,.567f,.17f,mouth+.025f,.105f,ink,8f)
        ball(head,0f,-.36f-jaw*.10f,.525f,.26f,.12f,.145f,cream)
        for(side in listOf(-1f,1f)) {
            ball(head,side*.17f,-.175f,.50f,.265f,.18f,.185f,cream)
            for(i in 0..2) ball(head,side*(.19f+i*.053f),-.15f-(i%2)*.055f,.678f-i*.007f,.012f,.011f,.008f,stripe)
            // Slightly curved whiskers, with a subtle twitch during listening.
            for(i in -1..1) {
                val start=V(side*.28f,-.20f+i*.045f,.66f)
                val end=V(side*(.86f+abs(i)*.04f),-.20f+i*.15f+sin(t*2+i)*listen*.018f,.51f)
                val mid=V(side*.57f,-.20f+i*.10f,.64f)
                link(head,start,mid,.008f,floatArrayOf(.73f,.65f,.52f));link(head,mid,end,.005f,cream)
            }
        }
        ball(head,0f,-.09f,.702f,.09f,.065f,.055f,pink,55f)
        link(head,V(0f,-.14f,.71f),V(0f,-.22f,.70f),.010f,stripe)
        if(jaw>.12f) ball(head,0f,-.29f-jaw*.06f,.67f,.065f,.022f,.02f,pink,20f)
        if(jaw<.25f) for(side in listOf(-1f,1f)) {
            val y=-.24f+proud*.015f
            link(head,V(0f,-.22f,.70f),V(side*.09f,y-angry*.02f,.69f),.009f,stripe)
            link(head,V(side*.09f,y-angry*.02f,.69f),V(side*.16f,y+.035f-angry*.085f,.66f),.008f,stripe)
        }
        glDisableVertexAttribArray(position);glDisableVertexAttribArray(normal)
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
        glUniform1f(furUniform,if(gloss<=16f)1f else 0f)
        mesh.draw(position,normal)
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
            uniform mat4 uModel; uniform mat4 uNormal; uniform mat4 uVP;
            varying vec3 vPosition; varying vec3 vNormal;
            void main(){ vec4 world=uModel*vec4(aPosition,1.0); vPosition=world.xyz;
                vNormal=normalize(mat3(uNormal)*aNormal); gl_Position=uVP*world; }
        """
        private const val FRAGMENT="""
            precision mediump float;
            varying vec3 vPosition; varying vec3 vNormal;
            uniform vec3 uColor; uniform float uGloss; uniform float uFur;
            void main(){
                vec3 n=normalize(vNormal); vec3 eye=normalize(vec3(0.0,0.35,5.8)-vPosition);
                vec3 key=normalize(vec3(-0.5,0.75,0.9)); vec3 fill=normalize(vec3(0.8,0.3,0.7));
                float k=max(dot(n,key),0.0); float f=max(dot(n,fill),0.0);
                float rim=pow(1.0-max(dot(n,eye),0.0),3.0)*max(dot(n,normalize(vec3(0.1,0.7,-1.0))),0.0);
                float spec=pow(max(dot(n,normalize(key+eye)),0.0),uGloss);
                float grain=fract(sin(dot(floor(vPosition*170.0),vec3(12.9898,78.233,45.164)))*43758.5453);
                vec3 color=uColor*(0.34+0.66*k+0.20*f)*(1.0+uFur*(grain-0.5)*0.025);
                color+=vec3(0.25,0.37,0.37)*rim+vec3(1.0,0.94,0.84)*spec*(uGloss>40.0?0.44:0.06);
                gl_FragColor=vec4(color,1.0);
            }
        """
    }
}
