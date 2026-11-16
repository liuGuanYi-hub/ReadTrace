# 阅痕 ReadTrace 混淆规则（P40 安装包瘦身）
#
# 总体策略：
# - Manifest 声明的组件（Activity / Service / AppWidgetProvider / 自定义 View）由 AGP 自动保留；
# - androidx / Material 依赖自带 consumer rules，Fragment 与 View 的构造反射已被覆盖；
# - JSON 全部通过平台 API org.json 以字段直读方式手动序列化，无反射依赖，无需 keep 数据模型。

# 崩溃堆栈保留行号，便于线上排查（体积代价可忽略）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager 的 Room 生成类在 AGP 9 full-mode R8 下会丢失无参构造函数，
# 启动即崩：NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
# 崩溃点在 androidx.startup.InitializationProvider → WorkManagerInitializer，
# 属于「应用还没进入任何 Activity 就挂掉」，必须显式保留。
# room-runtime 自带的 consumer rules 在此裁剪模式下未能覆盖 work 的生成类，故此处补一条。
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# 微信 OpenSDK 为运行时反射软接入（WeChatAuthManager 通过 Class.forName 查找，
# 当前未编入 classpath，找不到时静默降级游客模式）。R8 对缺失类告警需要压制；
# 若未来以 compileOnly / libs jar 形式引入，请同时解开下方 keep 规则：
-dontwarn com.tencent.mm.opensdk.**
#-keep class com.tencent.mm.opensdk.** { *; }
