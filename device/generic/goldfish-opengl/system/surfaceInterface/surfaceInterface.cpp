#include "surfaceInterface.h"

#include <cutils/log.h>
#include <gui/Surface.h>

class SurfaceInterface : public android::ANativeObjectBase<
                                    ANativeWindow,
                                    android::Surface,
                                    android::RefBase> {
public:
    static SurfaceInterface* get();
    void setAsyncMode(ANativeWindow* anw, bool async) {
        ALOGD("SurfaceInterface::%s: set async mode %d", __func__, async);
        window = anw;
        android::Surface* s = android::Surface::getSelf(window);
        s->setAsyncMode(async);
        window = NULL;
    }
    ANativeWindow* window;
};

static SurfaceInterface* sSurfaceInterface = NULL;

SurfaceInterface* SurfaceInterface::get() {
    if (!sSurfaceInterface)
        sSurfaceInterface = new SurfaceInterface;
    return sSurfaceInterface;
}

extern "C" void surfaceInterface_init() {
    SurfaceInterface::get();
}

extern "C" void surfaceInterface_setAsyncModeForWindow(void* window) {
    SurfaceInterface::get()->setAsyncMode((ANativeWindow*)window, true);
}
