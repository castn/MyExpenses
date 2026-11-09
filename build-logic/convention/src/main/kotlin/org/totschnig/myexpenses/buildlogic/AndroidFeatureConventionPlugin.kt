package org.totschnig.myexpenses.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * The shell of the new UI on top of `:myExpenses`, i.e. `:ui_next`, a library built into `:app`.
 * Like the main module it uses java.time through desugaring.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            defaultConfig.missingDimensionStrategy("textRecognition", "extern")
            compileOptions.isCoreLibraryDesugaringEnabled = true
        }
        dependencies {
            add("implementation", project(":myExpenses"))
            add("coreLibraryDesugaring", libs.findLibrary("android-desugar").get())
        }
    }
}
