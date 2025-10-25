
-target 1.8
-dontshrink
-keepattributes *Annotation*
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames
-flattenpackagehierarchy 'a'
-repackageclasses 'a'
-renamesourcefileattribute SourceFile
-adaptclassstrings
-adaptresourcefilenames **.properties,a/**.bin
-adaptresourcefilecontents **.properties,MANIFEST.MF
-verbose

#-keepclassmembers class com.jacob.** {
#   <fields>;
#}
#
#-keepclasseswithmembers class log4j2.* {
#    <methods>;
#}

#-keep public class me.lethinh.xacminhserver.XacMinhApplication

-keepclassmembers public class * {
    public static void main(java.lang.String[]);
}

# Also keep - Enumerations. Keep the special static methods that are required in
# enumeration classes.
-keepclassmembers enum  * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Also keep - Database drivers. Keep all implementations of java.sql.Driver.
-keep class * extends java.sql.Driver

# Also keep - Swing UI L&F. Keep all extensions of javax.swing.plaf.ComponentUI,
# along with the special 'createUI' method.
-keepclassmembers class * extends javax.swing.plaf.ComponentUI {
    public static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent);
}

# Keep - Native method names. Keep all native class/method names.
-keepclasseswithmembers,includedescriptorclasses,allowshrinking class * {
    native <methods>;
}

# Remove debugging - Throwable_printStackTrace calls. Remove all invocations of
# Throwable.printStackTrace().
-assumenosideeffects public class java.lang.Throwable {
    public void printStackTrace();
}

# Remove debugging - Thread_dumpStack calls. Remove all invocations of
# Thread.dumpStack().
-assumenosideeffects public class java.lang.Thread {
    public static void dumpStack();
}
