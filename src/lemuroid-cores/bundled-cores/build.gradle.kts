plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid.cores"

    sourceSets {
        getByName("main") {
            // Configurar jniLibs para que tome los cores de los directorios reales
            // en lugar de symlinks incompatibles en algunos entornos
            jniLibs.srcDirs(
                "../lemuroid_core_snes9x/src/main/jniLibs",
                "../lemuroid_core_fceumm/src/main/jniLibs",
                "../lemuroid_core_gambatte/src/main/jniLibs",
                "../lemuroid_core_mgba/src/main/jniLibs",
                "../lemuroid_core_genesis_plus_gx/src/main/jniLibs",
                "../lemuroid_core_stella/src/main/jniLibs",
                "../lemuroid_core_handy/src/main/jniLibs",
                "../lemuroid_core_prosystem/src/main/jniLibs",
                "../lemuroid_core_fbneo/src/main/jniLibs",
                "../lemuroid_core_mame2003_plus/src/main/jniLibs",
                "../lemuroid_core_desmume/src/main/jniLibs",
                "../lemuroid_core_melonds/src/main/jniLibs",
                "../lemuroid_core_mupen64plus_next_gles3/src/main/jniLibs",
                "../lemuroid_core_pcsx_rearmed/src/main/jniLibs",
                "../lemuroid_core_ppsspp/src/main/jniLibs",
                "../lemuroid_core_mednafen_pce_fast/src/main/jniLibs",
                "../lemuroid_core_mednafen_ngp/src/main/jniLibs",
                "../lemuroid_core_mednafen_wswan/src/main/jniLibs",
                "../lemuroid_core_dosbox_pure/src/main/jniLibs",
                "../lemuroid_core_citra/src/main/jniLibs"
            )
        }
    }
}

dependencies {
    implementation(kotlin(deps.libs.kotlin.stdlib))
}
