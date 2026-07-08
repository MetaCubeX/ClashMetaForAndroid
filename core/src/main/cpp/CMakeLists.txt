cmake_minimum_required(VERSION 3.0)

# 获取git hash

message(STATUS "CMAKE_CURRENT_SOURCE_DIR= ${CMAKE_CURRENT_SOURCE_DIR}")
execute_process(
  COMMAND git submodule foreach git log -1 --format=%H   
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} 
  OUTPUT_VARIABLE COMMIT_HASH
  OUTPUT_STRIP_TRAILING_WHITESPACE
  ERROR_QUIET
)
string(REPLACE "\n" ";" COMMIT_HASH "${COMMIT_HASH}")
list(LENGTH COMMIT_HASH COMMIT_HASH_LEN)
if (COMMIT_HASH_LEN GREATER 1)
  list(GET COMMIT_HASH 1 COMMIT_HASH)
else ()
  set(COMMIT_HASH "unknown")
endif ()
string(REGEX REPLACE "[\n\t\r]" "" COMMIT_HASH "${COMMIT_HASH}")
if (NOT "${COMMIT_HASH}" STREQUAL "unknown")
  string(SUBSTRING "${COMMIT_HASH}" 0 7 COMMIT_HASH)
endif ()
message(STATUS "git hash= ${COMMIT_HASH}")

# 获取分支名称
execute_process(
  COMMAND git submodule foreach git branch -r --contains ${COMMIT_HASH}
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} 
  OUTPUT_VARIABLE CURRENT_BRANCH
  OUTPUT_STRIP_TRAILING_WHITESPACE
  ERROR_QUIET
)
string(REPLACE "\n" ";" CURRENT_BRANCH "${CURRENT_BRANCH}")
list(LENGTH CURRENT_BRANCH CURRENT_BRANCH_LEN)
if (CURRENT_BRANCH_LEN GREATER 1)
  list(GET CURRENT_BRANCH 1 CURRENT_BRANCH)
else ()
  set(CURRENT_BRANCH "unknown")
endif ()
string(REGEX REPLACE "origin/" "" CURRENT_BRANCH "${CURRENT_BRANCH}")
string(REGEX REPLACE "[\n\t\r]" "" CURRENT_BRANCH "${CURRENT_BRANCH}")
#string(SUBSTRING ${CURRENT_BRANCH} 0 8 CURRENT_BRANCH)
message(STATUS "git current branch = ${CURRENT_BRANCH}")

# 获取核心语义化版本标签（例如 v1.19.23）
execute_process(
  COMMAND git -C ${CMAKE_CURRENT_SOURCE_DIR}/../../foss/golang/clash describe --tags --abbrev=0 --match v[0-9]*
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
  OUTPUT_VARIABLE CORE_TAG
  RESULT_VARIABLE CORE_TAG_RESULT
  OUTPUT_STRIP_TRAILING_WHITESPACE
  ERROR_QUIET
)

# Reconfigure whenever the mihomo submodule moves. CORE_TAG above is read at
# configure time and cached in .cxx/, which survives `gradlew clean` — without
# this a core bump keeps shipping the old version string (and constant.Version,
# which the Go side derives from it) until .cxx/ is deleted by hand.
execute_process(
  COMMAND git -C ${CMAKE_CURRENT_SOURCE_DIR}/../../foss/golang/clash rev-parse --absolute-git-dir
  OUTPUT_VARIABLE MIHOMO_GIT_DIR
  OUTPUT_STRIP_TRAILING_WHITESPACE
  ERROR_QUIET
)
if (EXISTS "${MIHOMO_GIT_DIR}/HEAD")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MIHOMO_GIT_DIR}/HEAD")
endif ()
string(REGEX REPLACE "[\n\t\r]" "" CORE_TAG "${CORE_TAG}")
message(STATUS "core tag = ${CORE_TAG}")

# 获取生成时间
string(TIMESTAMP COMPILE_TIME "%y%m%d")
string (REGEX REPLACE "[\n\t\r]" "" COMPILE_TIME ${COMPILE_TIME})
string(REGEX REPLACE "\"" "" COMPILE_TIME ${COMPILE_TIME})

# 生成版本信息
# Prefer release tag for stable core version display in app UI.
if (CORE_TAG_RESULT EQUAL 0 AND NOT "${CORE_TAG}" STREQUAL "")
  set(GIT_VERSION "${CORE_TAG}_${COMMIT_HASH}_${COMPILE_TIME}")
else ()
  set(GIT_VERSION "${CURRENT_BRANCH}_${COMMIT_HASH}_${COMPILE_TIME}")
endif ()
message(STATUS "version info = ${GIT_VERSION}")

# 去除空格
string(REGEX REPLACE "[ ]+" "" GIT_VERSION "${GIT_VERSION}")

# 保存变量到文件
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/version.h.in ${CMAKE_CURRENT_SOURCE_DIR}/version.h @ONLY)

project(clash-bridge C)


set(CMAKE_POSITION_INDEPENDENT_CODE on)
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3")

set(GO_OUTPUT_BASE ${GO_OUTPUT}/${FLAVOR_NAME})

if ("${CMAKE_BUILD_TYPE}" STREQUAL "Debug")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Debug")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "Release")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Release")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "RelWithDebInfo")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Release")
else ()
    message(FATAL_ERROR "Unknown build type ${CMAKE_BUILD_TYPE}")
endif ()

include_directories("${GO_OUTPUT_BASE}/${CMAKE_ANDROID_ARCH_ABI}")
include_directories("${GO_SOURCE}")

link_directories("${GO_OUTPUT_BASE}/${CMAKE_ANDROID_ARCH_ABI}")

add_library(bridge SHARED main.c jni_helper.c bridge_helper.c)
target_link_libraries(bridge log clash)
