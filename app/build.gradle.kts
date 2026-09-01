import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 微信开放平台 AppID。
// 未配置（留空）时，微信授权自动降级为「沙盒模拟模式」，
// 走完整的本地授权链路但不拉起真实微信客户端，保证本地开发不被 AppID 阻塞。
val wechatAppId: String = (project.findProperty("WECHAT_APP_ID") as String?).orEmpty()

// 阿里云短信服务（v1.0.3 认证正式化）。
// 未配置（留空）时，手机号验证码自动降级为「沙盒模式」（本地生成并明文回显）。
// 全部配置后，PhoneAuthManager 将调用 Dysmsapi SendSms 真实下发验证码。
val aliyunSmsAccessKeyId: String = (project.findProperty("ALIYUN_SMS_ACCESS_KEY_ID") as String?).orEmpty()
val aliyunSmsAccessKeySecret: String = (project.findProperty("ALIYUN_SMS_ACCESS_KEY_SECRET") as String?).orEmpty()
val aliyunSmsSignName: String = (project.findProperty("ALIYUN_SMS_SIGN_NAME") as String?).orEmpty()
val aliyunSmsTemplateCode: String = (project.findProperty("ALIYUN_SMS_TEMPLATE_CODE") as String?).orEmpty()

android {
    namespace = "com.example.readtrace"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.readtrace"
        minSdk = 31
        targetSdk = 37
        versionCode = 40
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "WECHAT_APP_ID", "\"$wechatAppId\"")
        buildConfigField("String", "ALIYUN_SMS_ACCESS_KEY_ID", "\"$aliyunSmsAccessKeyId\"")
        buildConfigField("String", "ALIYUN_SMS_ACCESS_KEY_SECRET", "\"$aliyunSmsAccessKeySecret\"")
        buildConfigField("String", "ALIYUN_SMS_SIGN_NAME", "\"$aliyunSmsSignName\"")
        buildConfigField("String", "ALIYUN_SMS_TEMPLATE_CODE", "\"$aliyunSmsTemplateCode\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file("readtrace-release.jks")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug {
            // 与 release 共用正式签名，避免调试装机与正式包互相覆盖时签名冲突；
            // 本地无 keystore.properties（如 CI）时回退默认调试签名。
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all { test ->
            // AGP 9 内置 Kotlin (built_in_kotlinc) 的单测运行时 classpath 缺失测试类输出，
            // 导致 ClassNotFoundException；将测试类目录打成 jar 显式挂载（本地 JVM 单测专用，不影响 APK 构建）
            test.dependsOn(unitTestKotlinClassesJar)
            test.classpath += files(unitTestKotlinClassesJar.flatMap { it.archiveFile })
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    // 单元测试使用 JVM 版 org.json 实现（Android SDK 中的 org.json 在本地单测中被 stub）
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 将 AGP 9 内置 Kotlin 编译的主代码与单元测试类打包为 jar，供 testDebugUnitTest 运行时 classpath 使用。
// 项目路径含中文与空格，AGP 测试 worker 对此类 classpath 条目解码异常导致 ClassNotFoundException；
// 故将 jar 输出到纯 ASCII 的系统临时目录，绕开非 ASCII 条目（jar 仅数 MB，远低于 C 盘 50MB 保护线）。
val unitTestKotlinClassesJar = tasks.register<Jar>("packageDebugUnitTestKotlinClasses") {
    dependsOn(tasks.matching { it.name == "compileDebugKotlin" || it.name == "compileDebugUnitTestKotlin" })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"))
    from(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"))
    archiveFileName.set("readtrace-unit-test-classes.jar")
    destinationDirectory.set(file(System.getProperty("java.io.tmpdir") + "/readtrace-unit-tests"))
}
