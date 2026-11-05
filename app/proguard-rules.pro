# ProGuard / R8 configuration for Continuum Explorer
# Uses R8 tree-shaking (code & resource shrinking) while disabling slow/brittle bytecode optimization
# and obfuscation so reflection-heavy server/document libraries work reliably and build fast.

-dontoptimize
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep application entrypoints and services
-keep class com.troikoss.continuum_explorer.** { *; }

# Tink / EncryptedSharedPreferences — R8 strips reflection targets without these
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# Ktor & Netty embedded server reflection entrypoints
-keep class io.ktor.server.netty.** { *; }
-keep class io.netty.channel.** { *; }
-keep class io.netty.util.internal.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn kotlinx.html.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.**
-dontwarn com.typesafe.config.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.log4j.**

# Apache FtpServer & MINA
-keep class org.apache.ftpserver.** { *; }
-keep class org.apache.mina.** { *; }
-dontwarn org.apache.ftpserver.**
-dontwarn org.apache.mina.**

# Network storage libraries (FTP, SFTP, WebDAV, SMB)
-keep class org.apache.commons.net.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep class at.bitfire.dav4jvm.** { *; }
-keep class com.hierynomus.** { *; }
-keep class com.rapid7.** { *; }
-keep class net.engio.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn com.jcraft.jsch.**
-dontwarn at.bitfire.dav4jvm.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.hierynomus.**
-dontwarn com.rapid7.**
-dontwarn net.engio.**

# Document, Archive, PDF, Log4j, OSGi & Editor engines
-dontwarn org.osgi.framework.**
-dontwarn org.osgi.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.batik.**
-dontwarn org.etsi.uri.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn schemasMicrosoftComOfficeExcel.**
-dontwarn schemasMicrosoftComOfficeOffice.**
-dontwarn schemasMicrosoftComVml.**
-dontwarn com.graphbuilder.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn de.rototor.pdfbox.**
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.**
-dontwarn javax.script.**
-dontwarn javax.management.**
-dontwarn javax.security.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn sun.misc.**

-dontwarn javax.el.**
-dontwarn javax.naming.**
-dontwarn java.beans.**
-dontwarn java.lang.instrument.**
-dontwarn java.rmi.**
-dontwarn java.sql.**
-dontwarn javax.sql.**
-dontwarn javax.transaction.**