// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    // KSP: lo necesita el compilador de Room (annotation processing). 2.3.6 es la que recomienda Android para AGP 9.
    id("com.google.devtools.ksp") version "2.3.6" apply false
}
