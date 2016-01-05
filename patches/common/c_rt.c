/**
    Copyright (C) 2015-2016 Lymia Aluysia <lymiahugs@gmail.com>

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

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#include "c_rt.h"
#include "platform.h"

// Debug logging
#ifdef DEBUG
    FILE* debug_log_file;
    __attribute__((constructor(150))) static void initDebugLogging() {
        debug_log_file = fopen("mvmm_debug.log", "w");
    }
#else
    #define debug_print(format, ...)
#endif

extern __attribute__((stdcall)) void* asm_resolveAddress(int address) __asm__("cif_resolveAddress");
__attribute__((stdcall)) void* asm_resolveAddress(int address) {
    return resolveAddress(address);
}

// Actual patch code!
static UnpatchData* writeRelativeJmp(void* targetAddress, void* hookAddress, const char* reason) {
    // Register the patch for unpatching
    UnpatchData* unpatch = malloc(sizeof(UnpatchData));
    unpatch->offset = targetAddress;
    memcpy(unpatch->oldData, targetAddress, 5);

    // Actually generate the patch opcode.
    int offsetDiff = (int) hookAddress - (int) targetAddress - 5;
    debug_print("Writing JMP (%s) - 0x%08x => 0x%08x (diff: 0x%08x)",
        reason, targetAddress, hookAddress, offsetDiff);
    *((char*)(targetAddress    )) = 0xe9; // jmp opcode
    *((int *)(targetAddress + 1)) = offsetDiff;

    return unpatch;
}
UnpatchData* doPatch(int address, void* hookAddress, const char* reason) {
    void* targetAddress = resolveAddress(address);
    char reason_buf[256];
    snprintf(reason_buf, 256, "patch: %s", reason);

    memory_oldProtect protectFlags;
    unprotectMemoryRegion(targetAddress, 5, &protectFlags);
    UnpatchData* unpatch = writeRelativeJmp(targetAddress, hookAddress, reason_buf);
    protectMemoryRegion(targetAddress, 5, &protectFlags);
    return unpatch;
}
void unpatch(UnpatchData* data) {
    memory_oldProtect protectFlags;
    debug_print("Unpatching at 0x%08x", data->offset);
    unprotectMemoryRegion(data->offset, 5, &protectFlags);
    memcpy(data->offset, data->oldData, 5);
    protectMemoryRegion(data->offset, 5, &protectFlags);
    free(data);
}
