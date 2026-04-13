if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/Users/thomasphillips/.gradle/caches/transforms-3/529c4bc72d583ca3a7109bb36ebb70f1/transformed/oboe-1.9.3/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/Users/thomasphillips/.gradle/caches/transforms-3/529c4bc72d583ca3a7109bb36ebb70f1/transformed/oboe-1.9.3/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

