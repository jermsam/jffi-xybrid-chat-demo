package com.yourcompany.testapp.ui.theme

import androidx.compose.ui.graphics.Color

// Simple Gray Palette
val PrimaryGray = Color(0xFF1F2937)          // Gray 800 (Primary for light mode / user bubble)
val PrimaryLightGray = Color(0xFFF3F4F6)     // Gray 100
val PrimaryDarkGray = Color(0xFF111827)      // Gray 900
val SecondaryGray = Color(0xFF4B5563)        // Gray 600

// Dark Mode Colors
val DarkBg = Color(0xFF121212)              // Standard dark mode background
val DarkSurface = Color(0xFF1E1E1E)         // Surface card background
val DarkOnBg = Color(0xFFF3F4F6)
val DarkOnSurface = Color(0xFFE5E7EB)

// Light Mode Colors
val LightBg = Color(0xFFF3F4F6)             // Cool gray light background
val LightSurface = Color(0xFFFFFFFF)        // Pure white for surface
val LightOnBg = Color(0xFF111827)
val LightOnSurface = Color(0xFF1F2937)

// Chat bubble colors — light mode
val LightUserBubble = Color(0xFF1F2937)          // Dark gray for user
val LightBotBubble = Color(0xFFE5E7EB)           // Light gray for bot
val LightUserBubbleText = Color(0xFFFFFFFF)
val LightBotBubbleText = Color(0xFF111827)

// Chat bubble colors — dark mode
val DarkUserBubble = Color(0xFF374151)           // Medium-dark gray for user
val DarkBotBubble = Color(0xFF1F2937)            // Darker gray for bot
val DarkUserBubbleText = Color(0xFFFFFFFF)
val DarkBotBubbleText = Color(0xFFF1F5F9)
