This directory contains the original kernel headers that are used to generate
Bionic's "cleaned-up" user-land headers.

They are mostly covered by the GPLv2 + exception, and thus cannot be
distributed as part of the platform itself.
(NOTE: The cleaned up headers do not contain copyrightable information and are
distributed with Bionic)

Importing modified kernel headers files is done using scripts found in bionic.

For more details, please read bionic/libc/kernel/README.TXT.

Generated Headers
-----------------
The uapi directory contains uapi version of original kernel headers that are
used to generate Bionic's "cleaned-up" user-land headers. The script
bionic/libc/kernel/tools/generate_uapi_headers.sh automatically imports the
headers from an android kernel repository.

Running the script:

generate_uapi_headers.sh --download-kernel

In order to run the script, you must have properly initialized the build
environment using the lunch command. The script will automatically retrieve
an android kernel, generate all include files, and then copy the headers to
this directory.

The headers are mostly covered by the GPLv2 + exception, and thus cannot be
distributed as part of the platform itself.
(NOTE: The cleaned up headers do not contain copyrightable information and
are distributed with Bionic)

For more details, please read bionic/libc/kernel/README.TXT

Manually Modified Headers
-------------------------
The scsi directory contains a set of manually updated headers. The scsi kernel
header were never properly made to into uapi versions, so this directory
contains the unmodified scsi headers that are imported into bionic. The
generation script will indicate if these files have changed and require
another manual update.

The files from the scsi directory will be copied into bionic after being
processed as is, unless there exists a file of the same name in
../modified/scsi. Any files found in the modified directory completely
replace the ones in the scsi directory.
