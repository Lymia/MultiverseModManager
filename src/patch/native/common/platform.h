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

#include <stddef.h>

#include "platform_defines.h"

const char* getExecutablePath();

__attribute__((noreturn)) void fatalError_fn(const char* message);
#define fatalError(format, arg...) { \
    char fatal_error_buffer[1024]; \
    snprintf(fatal_error_buffer, 1024, format, ##arg); \
    fatalError_fn(fatal_error_buffer); \
}

// Memory management functions
typedef unsigned long int memory_oldProtect; // used on Windows since we can easily get the memory protection status.
void unprotectMemoryRegion(void* start, size_t length, memory_oldProtect* old);
void protectMemoryRegion  (void* start, size_t length, memory_oldProtect* old);

typedef struct ExecutableMemory {
    int length;
    char data[];
} ExecutableMemory;
ExecutableMemory* executable_malloc(int length);
void executable_prepare(ExecutableMemory* memory);
void executable_free(ExecutableMemory* memory);

// std::list implementation
CppList* CppList_alloc();
void* CppList_newLink(CppList* list, int length);
CppListLink* CppList_begin(CppList* list);
CppListLink* CppList_end(CppList* list);
int CppList_size(CppList* list);
void CppList_clear(CppList* list);
void CppList_free(CppList* list);
