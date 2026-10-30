package com.vini.miniprinter.driver

/**
 * Reply to `1E 47 03`, e.g. "HV=H1.0,SV=V1.01,VOLT=7980mv,DPI=384,".
 * Found by probing the YHK-ECFA.
 */
data class PrinterInfo(
    val hardware: String?,
    val firmware: String?,
    val millivolts: Int?,
    val dots: Int?,
) {
    /** Rough charge for a 2-cell Li-ion pack (8.4 V full); null when no voltage was reported. */
    val batteryPercent: Int? get() = millivolts?.let { percentFor(it) }

    companion object {
        val QUERY = byteArrayOf(0x1E, 0x47, 0x03)

        fun parse(reply: String): PrinterInfo? {
            val fields = reply.trimEnd('\u0000').split(',')
                .mapNotNull { f -> f.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
                .toMap()
            if (fields.isEmpty()) return null
            return PrinterInfo(
                hardware = fields["HV"],
                firmware = fields["SV"],
                millivolts = fields["VOLT"]?.filter(Char::isDigit)?.toIntOrNull(),
                dots = fields["DPI"]?.toIntOrNull(),
            )
        }

        /** Per-cell Li-ion discharge curve (mV -> %), interpolated linearly. */
        private val CURVE = listOf(
            3300 to 0, 3500 to 6, 3600 to 15, 3700 to 30, 3800 to 48,
            3900 to 63, 4000 to 77, 4100 to 88, 4200 to 100,
        )

        fun percentFor(packMillivolts: Int, cells: Int = 2): Int {
            val cell = packMillivolts / cells
            if (cell <= CURVE.first().first) return 0
            if (cell >= CURVE.last().first) return 100
            val i = CURVE.indexOfFirst { it.first >= cell }
            val (v0, p0) = CURVE[i - 1]
            val (v1, p1) = CURVE[i]
            return p0 + (p1 - p0) * (cell - v0) / (v1 - v0)
        }
    }
}
