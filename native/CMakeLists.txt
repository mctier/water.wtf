cmake_minimum_required(VERSION 3.20)
project(utilitymod-native)
set(CMAKE_CXX_STANDARD 17)
find_package(JNI REQUIRED)
add_library(utilitymod-native SHARED bridge.cpp)
target_include_directories(utilitymod-native PRIVATE ${JNI_INCLUDE_DIRS})
# Output to utility-mod/run or build
set_target_properties(utilitymod-native PROPERTIES OUTPUT_NAME "utilitymod-native")
