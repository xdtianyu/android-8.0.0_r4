#include "shared.rsh"

struct Simple {
    int I;
    long L;
};

struct Simple simple;

void checkSimple(int argI, long argL) {
    bool failed = false;

    rsDebug("argI       ", argI);
    rsDebug("simple.I   ", simple.I);
    rsDebug("argL.lo    ", (unsigned)argL & ~0U);
    rsDebug("simple.L.lo", (unsigned)simple.L & ~0U);
    rsDebug("argL.hi    ", (unsigned)((ulong)argL >> 32));
    rsDebug("simple.L.hi", (unsigned)((ulong)simple.L >> 32));

    _RS_ASSERT(simple.I == argI);
    _RS_ASSERT(simple.L == argL);

    if (failed) {
        rsDebug("struct_field_simple FAILED", 0);
        rsSendToClientBlocking(RS_MSG_TEST_FAILED);
    }
    else {
        rsDebug("struct_field_simple PASSED", 0);
        rsSendToClientBlocking(RS_MSG_TEST_PASSED);
    }
}
