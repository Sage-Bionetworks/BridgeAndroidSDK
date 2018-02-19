# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# RS
-keep public class * implements org.researchstack.backbone.ui.step.layout.StepLayout { *; }
-keep public class * implements org.researchstack.backbone.ui.step.body.StepBody { *; }

# RS / Squeaky
-dontwarn co.touchlab.squeaky.dao.SqueakyOpenHelper

# Bridge Data
-dontwarn org.sagebionetworks.bridge.data.StudyUploadEncryptor*

# SpongyCastle
-keep class org.spongycastle.crypto.* { *; }
-keep class org.spongycastle.crypto.agreement.** { *; }
-keep class org.spongycastle.crypto.digests.* { *; }
-keep class org.spongycastle.crypto.ec.* { *; }
-keep class org.spongycastle.crypto.encodings.* { *; }
-keep class org.spongycastle.crypto.engines.* { *; }
-keep class org.spongycastle.crypto.macs.* { *; }
-keep class org.spongycastle.crypto.modes.* { *; }
-keep class org.spongycastle.crypto.paddings.* { *; }
-keep class org.spongycastle.crypto.params.* { *; }
-keep class org.spongycastle.crypto.prng.* { *; }
-keep class org.spongycastle.crypto.signers.* { *; }

-keep class org.spongycastle.jcajce.provider.asymmetric.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.dh.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.dsa.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.ec.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.elgamal.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.rsa.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.util.* { *; }
-keep class org.spongycastle.jcajce.provider.asymmetric.x509.* { *; }

-keep class org.spongycastle.jcajce.provider.digest.** { *; }
-keep class org.spongycastle.jcajce.provider.keystore.** { *; }
-keep class org.spongycastle.jcajce.provider.symmetric.** { *; }
-keep class org.spongycastle.jcajce.spec.* { *; }
-keep class org.spongycastle.jce.** { *; }

-keep class org.spongycastle.x509.** { *; }
-keep class org.spongycastle.bcpg.** { *; }
-keep class org.spongycastle.openpgp.** { *; }


# Logback
-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*
-dontwarn ch.qos.logback.core.net.*

# Guava
-dontwarn com.google.common.base.**
-keep class com.google.common.base.** {*;}
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** {*;}
-dontwarn   com.google.j2objc.annotations.**
-keep class com.google.j2objc.annotations.** { *; }
-dontwarn   java.lang.ClassValue
-keep class java.lang.ClassValue { *; }
-dontwarn   org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-keep class org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement { *; }

# mikephil/charting
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class *
-dontwarn javax.**
-dontwarn io.realm.**

# Jackson
-keep class com.fasterxml.jackson.databind.ObjectMapper {
    public <methods>;
    protected <methods>;
}
-keep class com.fasterxml.jackson.databind.ObjectWriter {
    public ** writeValueAsString(**);
}
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# RxJava
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
-dontnote rx.internal.util.PlatformDependent

# OkHttp
-keep class okhttp3.Headers { *; }