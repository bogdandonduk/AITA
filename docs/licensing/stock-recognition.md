# Local stock recognition: third-party notices

AITA's desktop package includes [readable third-party notices](../../composeApp/src/jvmMain/resources/stock-capture/THIRD-PARTY-NOTICES.txt), [unchanged upstream license files](../../composeApp/src/jvmMain/resources/stock-capture/licenses), and a [source/digest manifest](../../composeApp/src/jvmMain/resources/stock-capture/licenses-manifest.json). These JVM resources ship inside the application; they do not require an internet connection.

The additions cover Tess4J 5.18.0, Lept4J 1.23.0, Tesseract 5.5.2, Leptonica 1.87.0 and the seven pinned Tesseract fast models. They also retain notices for the codecs present in the shipped Leptonica DLL: libpng, zlib, Independent JPEG Group JPEG, libtiff, OpenJPEG, libwebp and giflib. Native DLL strings identify libpng 1.6.50, zlib 1.3.1, JPEG 9f, libtiff 4.7.1 and OpenJPEG 2.5.4. Exact GIF and WebP binary versions are not asserted; the manifest identifies the official upstream revisions used for their unmodified license copies.

This software is based in part on the work of the Independent JPEG Group.

Tess4J/Lept4J jars themselves omit a top-level LICENSE/NOTICE resource, so AITA supplies the upstream copies. Other Java dependency jars retain their embedded license and notice resources; this document is scoped to the new local-recognition components, not a complete inventory of all AITA dependencies. Native binaries and model files are not modified by AITA.

For a future dependency/model update, verify the bundled DLL's dependency and codec versions, update these copies and the SHA-256 manifest, and rerun both the portable resource-integrity tests and the Windows packaging gate. A permanently visible product badge is not used; readable distribution notices are retained.
