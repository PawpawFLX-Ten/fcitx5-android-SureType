cmake_minimum_required(VERSION 3.18)

project(fcitx5-android-plugin-rime VERSION ${VERSION_NAME})

# For reproducible build
add_link_options("LINKER:--hash-style=gnu,--build-id=none")

# prefab dependency
find_package(fcitx5 REQUIRED CONFIG)
get_target_property(FCITX5_CMAKE_MODULES fcitx5::cmake INTERFACE_INCLUDE_DIRECTORIES)
set(CMAKE_MODULE_PATH ${FCITX5_CMAKE_MODULES} ${CMAKE_MODULE_PATH})

find_package(ECM MODULE)
find_package(Fcitx5Core MODULE)
find_package(Fcitx5Module MODULE)

set(Rime_VERSION "1.12.0")
set(Rime_dependencies
        log # glog links to android log
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        )

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
        INTERFACE_LINK_LIBRARIES "${Rime_dependencies}"
        )

# https://github.com/rime/librime/blob/1.9.0/src/rime_api.h#L663
# https://stackoverflow.com/questions/805555/ld-linker-question-the-whole-archive-option
# https://cmake.org/cmake/help/latest/manual/cmake-generator-expressions.7.html#link-features
set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)
set(RIME_DATA_DIR "${CMAKE_INSTALL_DATADIR}/rime-data")
add_subdirectory(fcitx5-rime)

# HandJump V3: full gaboolic/rime-frost tree + schema overrides under assets/rime-frost
set(HANDJUMP_RIME_FROST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../rime-frost")
set(HANDJUMP_RIME_OVERRIDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../assets/rime-frost")

if (NOT IS_DIRECTORY "${HANDJUMP_RIME_FROST_DIR}")
    message(FATAL_ERROR
            "HandJump rime-frost not found at: ${HANDJUMP_RIME_FROST_DIR}\n"
            "Expected monorepo layout: HandJump V3/rime-frost next to HandJump V3/fcitx5-android")
endif ()
install(DIRECTORY "${HANDJUMP_RIME_FROST_DIR}/"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets
        PATTERN ".git" EXCLUDE
        PATTERN "others" EXCLUDE
        PATTERN ".github" EXCLUDE)

if (IS_DIRECTORY "${HANDJUMP_RIME_OVERRIDE_DIR}")
    install(DIRECTORY "${HANDJUMP_RIME_OVERRIDE_DIR}/"
            DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
            COMPONENT prebuilt-assets
            FILES_MATCHING PATTERN "*.yaml")
endif ()

install(FILES
        "default.yaml"
        "rime-essay/essay.txt"
        "rime-prelude/key_bindings.yaml"
        "rime-prelude/punctuation.yaml"
        "rime-prelude/symbols.yaml"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets
        )
