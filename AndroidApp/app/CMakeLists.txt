cmake_minimum_required(VERSION 3.22)
project(nativesynth)

find_package(oboe REQUIRED CONFIG)

add_library(nativesynth SHARED src/main/cpp/native_synth.cpp)

target_link_libraries(nativesynth
    oboe::oboe
    android
    log
)
