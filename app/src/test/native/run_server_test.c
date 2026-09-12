// SPDX-License-Identifier: GPL-3.0-or-later
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int interrupt_read;
static int interrupt_write;
static int fail_read;
static int short_write;

static ssize_t test_read(int fd, void *buf, size_t size) {
    if (interrupt_read) { interrupt_read = 0; errno = EINTR; return -1; }
    if (fail_read) { errno = EIO; return -1; }
    return read(fd, buf, size);
}

static ssize_t test_write(int fd, const void *buf, size_t size) {
    if (interrupt_write) { interrupt_write = 0; errno = EINTR; return -1; }
    return write(fd, buf, short_write && size > 2 ? 2 : size);
}

#define read test_read
#define write test_write
#define main server_launcher_main
#include "../../main/cpp/run_server.c"
#undef main
#undef write
#undef read

int main(int argc, char **argv) {
    assert(argc == 2);
    char dir[1024], src[1100], dst[1100];
    assert(snprintf(dir, sizeof(dir), "%s/launcher-test-XXXXXX", argv[1]) < (int) sizeof(dir));
    assert(mkdtemp(dir));
    snprintf(src, sizeof(src), "%s/source", dir);
    snprintf(dst, sizeof(dst), "%s/destination", dir);
    FILE *input = fopen(src, "w");
    assert(input);
    const char content[] = "complete jar contents";
    assert(fwrite(content, 1, sizeof(content), input) == sizeof(content));
    assert(fclose(input) == 0);

    interrupt_read = interrupt_write = short_write = 1;
    assert(copy_file(src, dst) == 0);
    FILE *output = fopen(dst, "r");
    assert(output);
    char actual[sizeof(content)];
    assert(fread(actual, 1, sizeof(actual), output) == sizeof(actual));
    assert(memcmp(content, actual, sizeof(content)) == 0);
    assert(fclose(output) == 0);

    fail_read = 1;
    assert(copy_file(src, dst) == -1);
    assert(access(dst, F_OK) != 0);
    unlink(src);
    rmdir(dir);
    puts("Native launcher regression checks passed");
    return 0;
}
