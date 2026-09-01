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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}