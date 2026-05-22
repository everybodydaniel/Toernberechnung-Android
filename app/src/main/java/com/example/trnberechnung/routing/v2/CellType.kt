package com.example.trnberechnung.routing.v2

enum class CellType(val cost: Double, val isBlocked: Boolean) {

    OPEN_SEA(1.0, false),

    FAIRWAY(0.85, false),

    HARBOUR(1.0, false),

    WATTFAHRWASSER(0.95, false),

    LAND(Double.MAX_VALUE, true),

    RESTRICTED(Double.MAX_VALUE, true),

    RUHEZONE(2.0, false);

    companion object {

        private val VALUES = values()

        fun fromByte(b: Byte): CellType {
            val idx = b.toInt() and 0xFF
            return if (idx < VALUES.size) VALUES[idx] else OPEN_SEA
        }
    }
}
