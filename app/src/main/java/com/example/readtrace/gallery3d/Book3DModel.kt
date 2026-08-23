package com.example.readtrace.gallery3d

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Book3DModel(
    width: Float = 1.0f,
    height: Float = 1.45f,
    depth: Float = 0.18f,
) {
    val vertexBuffer: FloatBuffer
    val normalBuffer: FloatBuffer
    val texCoordBuffer: FloatBuffer
    val indexBuffer: ShortBuffer
    val indexCount: Int

    init {
        val w = width / 2.0f
        val h = height / 2.0f
        val d = depth / 2.0f

        // 24 个顶点（6 个面 * 4 个顶点）
        val vertices = floatArrayOf(
            // 前面 (封面)
            -w, -h,  d,   w, -h,  d,   w,  h,  d,  -w,  h,  d,
            // 后面 (封底)
             w, -h, -d,  -w, -h, -d,  -w,  h, -d,   w,  h, -d,
            // 左面 (书脊)
            -w, -h, -d,  -w, -h,  d,  -w,  h,  d,  -w,  h, -d,
            // 右面 (书页侧边)
             w, -h,  d,   w, -h, -d,   w,  h, -d,   w,  h,  d,
            // 顶面 (顶切面)
            -w,  h,  d,   w,  h,  d,   w,  h, -d,  -w,  h, -d,
            // 底面 (底切面)
            -w, -h, -d,   w, -h, -d,   w, -h,  d,  -w, -h,  d,
        )

        // 法线向量
        val normals = floatArrayOf(
            // 前面
             0f,  0f,  1f,   0f,  0f,  1f,   0f,  0f,  1f,   0f,  0f,  1f,
            // 后面
             0f,  0f, -1f,   0f,  0f, -1f,   0f,  0f, -1f,   0f,  0f, -1f,
            // 左面
            -1f,  0f,  0f,  -1f,  0f,  0f,  -1f,  0f,  0f,  -1f,  0f,  0f,
            // 右面
             1f,  0f,  0f,   1f,  0f,  0f,   1f,  0f,  0f,   1f,  0f,  0f,
            // 顶面
             0f,  1f,  0f,   0f,  1f,  0f,   0f,  1f,  0f,   0f,  1f,  0f,
            // 底面
             0f, -1f,  0f,   0f, -1f,  0f,   0f, -1f,  0f,   0f, -1f,  0f,
        )

        // UV 纹理坐标 (正面映射完整封面，其余侧面映射材质边缘)
        val texCoords = floatArrayOf(
            // 前面 (完整正向封面)
            0f, 1f,   1f, 1f,   1f, 0f,   0f, 0f,
            // 后面 (封底反向)
            1f, 1f,   0f, 1f,   0f, 0f,   1f, 0f,
            // 左面 (书脊)
            0f, 1f,   0.08f, 1f, 0.08f, 0f, 0f, 0f,
            // 右面 (书页)
            0.92f, 1f, 1f, 1f,  1f, 0f,   0.92f, 0f,
            // 顶面
            0f, 0.08f, 1f, 0.08f, 1f, 0f,  0f, 0f,
            // 底面
            0f, 1f,   1f, 1f,   1f, 0.92f, 0f, 0.92f,
        )

        // 索引 (6 个面 * 2 个三角形 * 3 个索引 = 36 个索引)
        val indices = shortArrayOf(
             0,  1,  2,   0,  2,  3, // 前面
             4,  5,  6,   4,  6,  7, // 后面
             8,  9, 10,   8, 10, 11, // 左面
            12, 13, 14,  12, 14, 15, // 右面
            16, 17, 18,  16, 18, 19, // 顶面
            20, 21, 22,  20, 22, 23  // 底面
        )

        indexCount = indices.size

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(normals)
                position(0)
            }

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }
    }
}
