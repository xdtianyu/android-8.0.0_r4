#
# Clean Makefile
#

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/defs.mk

# Clean Target #################################################################

.PHONY: clean
clean:
	rm -rf $(OUT)
