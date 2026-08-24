package com.zakir.vestra

import android.app.Application

/**
 * Lightweight [Application] for instrumentation tests. Avoids [VestraApp]'s
 * ~68 MB lite-pack seed + manifest refresh so JUnit can start within the
 * instrumentation timeout.
 */
class TestApplication : Application()
