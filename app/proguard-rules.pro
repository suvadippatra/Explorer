# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Tink / EncryptedSharedPreferences — R8 strips reflection targets without these
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# Ktor & Netty embedded server
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class kotlinx.html.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn kotlinx.html.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.**
-dontwarn com.typesafe.config.**

# Apache FtpServer & MINA
-keep class org.apache.ftpserver.** { *; }
-keep class org.apache.mina.** { *; }
-dontwarn org.apache.ftpserver.**
-dontwarn org.apache.mina.**

# Network storage libraries (FTP, SFTP, WebDAV, SMB)
-keep class org.apache.commons.net.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep class at.bitfire.dav4jvm.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn com.jcraft.jsch.**
-dontwarn at.bitfire.dav4jvm.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# smbj — SMB2/3 client
-keep class com.hierynomus.** { *; }
-keep class com.rapid7.** { *; }
-keep class net.engio.** { *; }
-dontwarn com.hierynomus.**
-dontwarn com.rapid7.**
-dontwarn net.engio.**

# Document, Archive, PDF & Editor engines
-keep class org.apache.poi.** { *; }
-keep class com.tom_roush.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**

-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn javax.naming.**
-dontwarn java.beans.**