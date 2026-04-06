cmake_minimum_required(VERSION 3.22.1)

project("mem_scanner")

# Add the executable target
add_executable(mem_scanner scanner.cpp)

# Optimization flags for max performance
target_compile_options(mem_scanner PRIVATE -O3 -flto -ffast-math)
target_link_options(mem_scanner PRIVATE -O3 -flto)

# AGP automatically packages any target ending in .so to the APK
# We rename the executable output to libscanner.so so AGP picks it up.
set_target_properties(mem_scanner PROPERTIES OUTPUT_NAME "scanner")
set_target_properties(mem_scanner PROPERTIES SUFFIX ".so")
set_target_properties(mem_scanner PROPERTIES PREFIX "lib")
