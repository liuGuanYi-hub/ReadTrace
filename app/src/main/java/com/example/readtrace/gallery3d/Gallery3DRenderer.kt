package com.example.readtrace.gallery3d

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.readtrace.model.Book
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class Gallery3DRenderer(
    private val context: Context,
    private var books: List<Book> = emptyList(),
) : GLSurfaceView.Renderer {

    enum class GalleryTheme(
        val displayName: String,
        val bgRed: Float, val bgGreen: Float, val bgBlue: Float,
        val ambientR: Float, val ambientG: Float, val ambientB: Float,
        val lightR: Float, val lightG: Float, val lightB: Float,
    ) {
        MIDNIGHT("🌌 星空漫想", 0.05f, 0.07f, 0.12f, 0.45f, 0.45f, 0.55f, 0.9f, 0.95f, 1.0f),
        WARM_LIBRARY("🕯️ 暖木书房", 0.12f, 0.08f, 0.05f, 0.55f, 0.45f, 0.35f, 1.0f, 0.9f, 0.75f),
        ZEN_OASIS("🌿 禅意绿洲", 0.06f, 0.10f, 0.08f, 0.45f, 0.55f, 0.45f, 0.85f, 1.0f, 0.9f),
    }

    var currentTheme: GalleryTheme = GalleryTheme.MIDNIGHT

    // 摄像机与视界控制参数
    var rotationYaw: Float = 0f
    var pitchAngle: Float = 22f
    var cameraDistance: Float = 5.2f

    private var targetYaw: Float? = null
    private val bookModel = Book3DModel(width = 1.0f, height = 1.45f, depth = 0.18f)
    private val textureIds = mutableListOf<Int>()

    // OpenGL 矩阵
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var programId: Int = 0
    private var uMVPMatrixHandle: Int = -1
    private var uModelMatrixHandle: Int = -1
    private var uLightPosHandle: Int = -1
    private var uCameraPosHandle: Int = -1
    private var uAmbientColorHandle: Int = -1
    private var uLightColorHandle: Int = -1
    private var uTextureHandle: Int = -1
    private var aPositionHandle: Int = -1
    private var aNormalHandle: Int = -1
    private var aTexCoordinateHandle: Int = -1

    private var animationTime: Float = 0f
    private var surfaceWidth: Int = 1
    private var surfaceHeight: Int = 1

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoordinate;

        varying vec3 vPosition;
        varying vec3 vNormal;
        varying vec2 vTexCoordinate;

        void main() {
            vPosition = vec3(uModelMatrix * aPosition);
            vNormal = normalize(vec3(uModelMatrix * vec4(aNormal, 0.0)));
            vTexCoordinate = aTexCoordinate;
            gl_Position = uMVPMatrix * aPosition;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec3 uLightPos;
        uniform vec3 uCameraPos;
        uniform vec3 uAmbientColor;
        uniform vec3 uLightColor;

        varying vec3 vPosition;
        varying vec3 vNormal;
        varying vec2 vTexCoordinate;

        void main() {
            vec4 texColor = texture2D(uTexture, vTexCoordinate);
            
            vec3 lightDir = normalize(uLightPos - vPosition);
            float diff = max(dot(vNormal, lightDir), 0.0);
            
            vec3 viewDir = normalize(uCameraPos - vPosition);
            float rim = 1.0 - max(dot(viewDir, vNormal), 0.0);
            rim = smoothstep(0.65, 1.0, rim) * 0.3;
            
            vec3 diffuse = diff * uLightColor;
            vec3 finalColor = texColor.rgb * (uAmbientColor + diffuse) + vec3(rim);
            gl_FragColor = vec4(finalColor, texColor.a);
        }
    """.trimIndent()

    fun updateBooks(newBooks: List<Book>) {
        books = newBooks
    }

    fun smoothFocusTo(index: Int) {
        if (books.isEmpty()) return
        val count = books.size
        val anglePerBook = 360f / count
        // 目标是将 index 的书转到 180 度（正对摄像机前方）
        val desiredYaw = -(index * anglePerBook)
        // 寻找与当前 rotationYaw 最近的周期角度
        var diff = (desiredYaw - rotationYaw) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        targetYaw = rotationYaw + diff
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uLightPosHandle = GLES20.glGetUniformLocation(programId, "uLightPos")
        uCameraPosHandle = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uAmbientColorHandle = GLES20.glGetUniformLocation(programId, "uAmbientColor")
        uLightColorHandle = GLES20.glGetUniformLocation(programId, "uLightColor")
        uTextureHandle = GLES20.glGetUniformLocation(programId, "uTexture")

        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoordinateHandle = GLES20.glGetAttribLocation(programId, "aTexCoordinate")

        loadAllTextures()
    }

    private fun loadAllTextures() {
        textureIds.forEach { GalleryTextureHelper.deleteTexture(it) }
        textureIds.clear()
        books.forEach { book ->
            val texId = GalleryTextureHelper.loadOrCreateTexture(context, book)
            textureIds.add(texId)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 48.0f, ratio, 0.5f, 25.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 平滑对焦插值
        targetYaw?.let { target ->
            rotationYaw += (target - rotationYaw) * 0.12f
            if (kotlin.math.abs(target - rotationYaw) < 0.1f) {
                rotationYaw = target
                targetYaw = null
            }
        }

        animationTime += 0.016f

        val theme = currentTheme
        GLES20.glClearColor(theme.bgRed, theme.bgGreen, theme.bgBlue, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        // 相机位置计算（根据俯仰角与距离）
        val pitchRad = Math.toRadians(pitchAngle.toDouble()).toFloat()
        val camY = sin(pitchRad) * cameraDistance
        val camZ = cos(pitchRad) * cameraDistance
        Matrix.setLookAtM(viewMatrix, 0, 0f, camY, camZ, 0f, 0f, 0f, 0f, 1f, 0f)

        // 设置光照与摄像机参数
        GLES20.glUniform3f(uLightPosHandle, 0f, 4f, 4.5f)
        GLES20.glUniform3f(uCameraPosHandle, 0f, camY, camZ)
        GLES20.glUniform3f(uAmbientColorHandle, theme.ambientR, theme.ambientG, theme.ambientB)
        GLES20.glUniform3f(uLightColorHandle, theme.lightR, theme.lightG, theme.lightB)

        // 顶点属性绑定
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, bookModel.vertexBuffer)

        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, bookModel.normalBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordinateHandle)
        GLES20.glVertexAttribPointer(aTexCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, bookModel.texCoordBuffer)

        val count = books.size
        if (count == 0) return

        // 环形展台半径随作品数量自适应
        val radius = kotlin.math.max(1.8f, (count * 0.38f))
        val angleStep = 360f / count

        books.forEachIndexed { index, _ ->
            val angleDeg = index * angleStep + rotationYaw
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            val posX = sin(angleRad) * radius
            val posZ = cos(angleRad) * radius

            // 浮动呼吸动画
            val bobbing = sin(animationTime * 2.2f + index * 0.8f) * 0.06f

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, posX, bobbing, posZ)
            // 让书籍模型正面朝向外圈（朝向摄像机环视方向）
            Matrix.rotateM(modelMatrix, 0, angleDeg, 0f, 1f, 0f)

            // 计算 MVP 矩阵
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)

            // 绑定纹理
            val texId = if (index < textureIds.size) textureIds[index] else 0
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(uTextureHandle, 0)

            // 绘制 3D 书盒
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                bookModel.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                bookModel.indexBuffer,
            )
        }

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordinateHandle)
    }

    /**
     * 获取当前正对前方的聚焦书籍索引
     */
    fun getFrontFocusedIndex(): Int {
        if (books.isEmpty()) return -1
        val count = books.size
        val angleStep = 360f / count
        // 找出哪一个 index 的角度最贴近 0 度 (cos 接近 1)
        var bestIndex = 0
        var maxCos = -2.0

        for (i in 0 until count) {
            val angleDeg = (i * angleStep + rotationYaw) % 360f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val c = cos(angleRad)
            if (c > maxCos) {
                maxCos = c
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
