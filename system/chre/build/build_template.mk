#
# Build Template
#
# Invoke this template with a set of variables in order to make build targets
# for a build variant that targets a specific CPU architecture.
#

include $(CHRE_PREFIX)/build/defs.mk

################################################################################
#
# Build Template
#
# Invoke this to instantiate a set of build targets. Two build targets are
# produced by this template that can be either used directly or depended on to
# perform post processing (ie: during a nanoapp build).
#
# TARGET_NAME_ar - An archive of the code compiled by this template.
# TARGET_NAME_so - A shared object of the code compiled by this template.
# TARGET_NAME    - A convenience target that depends on the above archive and
#                  shared object targets.
#
# Argument List:
#     $1  - TARGET_NAME          - The name of the target being built.
#     $2  - TARGET_CFLAGS        - The compiler flags to use for this target.
#     $3  - TARGET_CC            - The C/C++ compiler for the target variant.
#     $4  - TARGET_SO_LDFLAGS    - The linker flags to use for this target.
#     $5  - TARGET_LD            - The linker for the target variant.
#     $6  - TARGET_ARFLAGS       - The archival flags to use for this target.
#     $7  - TARGET_AR            - The archival tool for the targer variant.
#     $8  - TARGET_VARIANT_SRCS  - Source files specific to this variant.
#     $9  - TARGET_BUILD_BIN     - Build a binary. Typically this means that the
#                                  source files provided include an entry point.
#     $10 - TARGET_BIN_LDFLAGS   - Linker flags that are passed to the linker
#                                  when building an executable binary.
#     $11 - TARGET_SO_EARLY_LIBS - Link against a set of libraries when building
#                                  a shared object. These are placed before the
#                                  objects produced by this build.
#     $12 - TARGET_SO_LATE_LIBS  - Link against a set of libraries when building
#                                  a shared object. These are placed after the
#                                  objects produced by this build.
#
################################################################################

ifndef BUILD_TEMPLATE
define BUILD_TEMPLATE

# Target Objects ###############################################################

# Source files.
$$(1)_CXX_SRCS = $$(filter %.cc, $(COMMON_SRCS) $(8))
$$(1)_C_SRCS = $$(filter %.c, $(COMMON_SRCS) $(8))

# Object files.
$$(1)_OBJS_DIR = $(1)_objs
$$(1)_CXX_OBJS = $$(patsubst %.cc, $(OUT)/$$($$(1)_OBJS_DIR)/%.o, \
                             $$($$(1)_CXX_SRCS))
$$(1)_C_OBJS = $$(patsubst %.c, $(OUT)/$$($$(1)_OBJS_DIR)/%.o, \
                           $$($$(1)_C_SRCS))

# Add object file directories.
$$$(1)_DIRS = $$(sort $$(dir $$($$(1)_CXX_OBJS) $$($$(1)_C_OBJS)))

# Outputs ######################################################################

# Shared Object
$$(1)_SO = $(OUT)/$$$(1)/$(OUTPUT_NAME).so

# Static Archive
$$(1)_AR = $(OUT)/$$$(1)/$(OUTPUT_NAME).a

# Optional Binary
$$(1)_BIN = $$(if $(9), $(OUT)/$$$(1)/$(OUTPUT_NAME), )

# Top-level Build Rule #########################################################

# Define the phony target.
.PHONY: $(1)_ar
$(1)_ar: $$($$(1)_AR)

.PHONY: $(1)_so
$(1)_so: $$($$(1)_SO)

.PHONY: $(1)_bin
$(1)_bin: $$($$(1)_BIN)

.PHONY: $(1)
$(1): $(1)_ar $(1)_so $(1)_bin

# If building the runtime, simply add the archive and shared object to the all
# target. When building CHRE, it is expected that this runtime just be linked
# into another build system (or the entire runtime is built using another build
# system).
ifeq ($(IS_NANOAPP_BUILD),)
all: $(1)
endif

# Compile ######################################################################

# Add common and target-specific compiler flags.
$$$(1)_CFLAGS = $(COMMON_CFLAGS) \
    $(2)

$$($$(1)_CXX_OBJS): $(OUT)/$$($$(1)_OBJS_DIR)/%.o: %.cc
	$(3) $(COMMON_CXX_CFLAGS) $$($$$(1)_CFLAGS) -c $$< -o $$@

$$($$(1)_C_OBJS): $(OUT)/$$($$(1)_OBJS_DIR)/%.o: %.c
	$(3) $(COMMON_C_CFLAGS) $$($$$(1)_CFLAGS) -c $$< -o $$@

# Archive ######################################################################

# Add common and target-specific archive flags.
$$$(1)_ARFLAGS = $(COMMON_ARFLAGS) \
    $(6)

$$($$(1)_AR): $$(OUT)/$$$(1) $$($$$(1)_DIRS) $$($$(1)_CXX_OBJS) $$($$(1)_C_OBJS)
	$(7) $$($$$(1)_ARFLAGS) $$@ $$(filter %.o, $$^)

# Link #########################################################################

$$($$(1)_SO): $$(OUT)/$$$(1) $$($$$(1)_DIRS) $$($$(1)_CXX_OBJS) $$($$(1)_C_OBJS)
	$(5) $(4) -o $$@ $(11) $$(filter %.o, $$^) $(12)

$$($$(1)_BIN): $$(OUT)/$$$(1) $$($$$(1)_DIRS) $$($$(1)_CXX_OBJS) \
               $$($$(1)_C_OBJS)
	$(3) -o $$@ $$(filter %.o, $$^) $(10)

# Output Directories ###########################################################

$$($$$(1)_DIRS):
	mkdir -p $$@

$$(OUT)/$$$(1):
	mkdir -p $$@

endef
endif

# Template Invocation ##########################################################

$(eval $(call BUILD_TEMPLATE, $(TARGET_NAME), \
                              $(TARGET_CFLAGS), \
                              $(TARGET_CC), \
                              $(TARGET_SO_LDFLAGS), \
                              $(TARGET_LD), \
                              $(TARGET_ARFLAGS), \
                              $(TARGET_AR), \
                              $(TARGET_VARIANT_SRCS), \
                              $(TARGET_BUILD_BIN), \
                              $(TARGET_BIN_LDFLAGS), \
                              $(TARGET_SO_EARLY_LIBS), \
                              $(TARGET_SO_LATE_LIBS)))
