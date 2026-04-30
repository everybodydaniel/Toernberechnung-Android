package com.example.trnberechnung.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════
// Maritime Farbpalette: „Nordsee bei Nacht"
// ══════════════════════════════════════════════════════

// ── Backgrounds & Surfaces ──
val NauticalBackground = Color(0xFF0D1B2A)       // Tiefes Marinedunkelblau
val NauticalSurface = Color(0xFF1B2838)           // Dunkleres Card-Blaugrau
val NauticalSurfaceVariant = Color(0xFF223347)    // Leicht hellere Flächen
val NauticalBottomBar = Color(0xFF111D2E)         // Noch dunkler als BG

// ── Accent Colors ──
val NauticalPrimary = Color(0xFF00BFA6)           // Teal – Seekarten-Fluoreszenz
val NauticalPrimaryDark = Color(0xFF009688)       // Gedämpftes Teal
val NauticalSecondary = Color(0xFF4FC3F7)         // Helles Cyan
val NauticalAccentWarm = Color(0xFFFFB74D)        // Warmes Amber (Warnungen)

// ── Text Colors ──
val NauticalTextPrimary = Color(0xFFE0E6ED)       // Helles Grauweiß
val NauticalTextSecondary = Color(0xFF7A8A9E)     // Gedämpftes Blaugrau
val NauticalTextOnPrimary = Color(0xFF0D1B2A)     // Dunkler Text auf hellen Akzenten

// ── Signal Colors (Steuerbord/Backbord) ──
val NauticalGo = Color(0xFF00E676)                // Steuerbord Grün
val NauticalGoBg = Color(0xFF0A2E1A)              // Dunkler Grün-Hintergrund
val NauticalNoGo = Color(0xFFFF5252)              // Backbord Rot
val NauticalNoGoBg = Color(0xFF2E0A0A)            // Dunkler Rot-Hintergrund

// ── Tidal / Chart Colors ──
val NauticalTideBlue = Color(0xFF26C6DA)          // Tidenkurve
val NauticalNowLine = Color(0xFFFF5252)           // Jetzt-Linie
val NauticalGridLine = Color(0xFF2A3A4E)          // Achsen/Grid

// ── Status / Info ──
val NauticalInfoBg = Color(0xFF0D2B3E)            // Info-Box dunkler Teal-BG
val NauticalInfoText = Color(0xFF4FC3F7)          // Info-Box Text (Cyan)

// ── Misc ──
val NauticalDivider = Color(0xFF2A3A4E)           // Dezente Trennlinien
val NauticalSunshine = Color(0xFFF9A825)          // Sonnenschein-Gelb

// ── Route Line Gradient ──
val RouteLineStart = Color(0xFF0066FF)            // Tiefes Seeblau
val RouteLineEnd = Color(0xFF00CCFF)              // Helles Cyan
val RouteLineGlow = Color(0xFF0088FF)             // Mittleres Leuchten

// Legacy (für Compose-Theme Scheme Kompatibilität)
val Purple80 = NauticalPrimary
val PurpleGrey80 = NauticalSecondary
val Pink80 = NauticalAccentWarm
val Purple40 = NauticalPrimaryDark
val PurpleGrey40 = NauticalTextSecondary
val Pink40 = NauticalNoGo