package com.wakao.maindexkeep

import com.android.build.gradle.api.BaseVariant

class TaskUtils {

    static final String getTransformProguard(BaseVariant variant) {
        "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
    }

    static final String getTransformMultidexlist(BaseVariant variant) {
        "transformClassesWithMultidexlistFor${variant.name.capitalize()}"
    }

    static final String getTransformDex(BaseVariant variant) {
        "transformClassesWithDexFor${variant.name.capitalize()}"
    }

    static final String getAssemble(BaseVariant variant) {
        "assemble${variant.name.capitalize()}"
    }

}