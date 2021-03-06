/**
    Copyright (C) 2015-2017 Lymia Aluysia <lymiahugs@gmail.com>

    Permission is hereby granted, free of charge, to any person obtaining a copy of
    this software and associated documentation files (the "Software"), to deal in
    the Software without restriction, including without limitation the rights to
    use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
    of the Software, and to permit persons to whom the Software is furnished to do
    so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

#pragma once

#include <stdio.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <limits.h>

#include "platform.h"
#include "config.h"

#define CONSTRUCTOR_GET_EXE_PATH      200
#define CONSTRUCTOR_READ_CONFIG       210
#define CONSTRUCTOR_LOGGING           220
#define CONSTRUCTOR_EARLY_INIT        230
#define CONSTRUCTOR_BINARY_INIT_EARLY 300
#define CONSTRUCTOR_BINARY_INIT       310
#define CONSTRUCTOR_PROXY_INIT        320
#define CONSTRUCTOR_HOOK_INIT         400

extern FILE* debug_log_file;
#define debug_print_raw(format, arg...) { \
    time_t time_val = time(NULL); \
    char* time_str_tmp = asctime(localtime(&time_val)); \
    time_str_tmp[strlen(time_str_tmp) - 1] = 0; \
    char debug_print_buffer[2048]; \
    snprintf(debug_print_buffer, 2048, "[%s] " format "\n", time_str_tmp, ##arg); \
    debug_print_buffer[2047] = '\0'; \
    fprintf(stderr, "[MPPatch] %s", debug_print_buffer); \
    if(debug_log_file != NULL && enableLogging) { \
        fprintf(debug_log_file, "%s", debug_print_buffer); \
        fflush(debug_log_file); \
    } \
}
#define debug_print(format, arg...) \
    debug_print_raw("%s at %s:%u - " format, __PRETTY_FUNCTION__, strrchr(__FILE__, '/') + 1, __LINE__, ##arg)

#define ENTRY __attribute__((force_align_arg_pointer))

const char* executable_directory_path;
#define getSupportFilePath(buffer, file) \
    snprintf(buffer, sizeof(buffer), "%s" PATH_SEPARATOR file, executable_directory_path);

bool endsWith(const char* str, const char* ending);
bool fileExists(const char* file);

CppListLink* CppListLink_alloc(int length);
void* CppListLink_newLink(CppListLink* list, int length);
void CppListLink_clear(CppListLink* list);
void CppListLink_free(CppListLink* list);

typedef struct PatchInformation {
    void* offset;
    char oldData[5];
    ExecutableMemory* functionFragment;
} PatchInformation;

void patchJmpInstruction(void* fromAddress, void* toAddress, const char* logReason);
PatchInformation* proxyFunction(void* fromAddress, void* toAddress, int patchBytes, const char* logReason);
void unpatchCode(PatchInformation* data);
void unpatch(PatchInformation* data);
