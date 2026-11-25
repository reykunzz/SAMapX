# Liquid Glass Inspector update

The 3D Inspector now follows the active app theme across its panel shell, section cards, tab selector, coordinate tiles, and selected-object quick card.

The visual treatment uses translucent theme-tinted surfaces, subtle vertical highlights, theme-colored outlines, and low-elevation depth. It does not use real-time backdrop blur; Android 10 does not provide the same native blur pipeline as newer Android versions, so this keeps the effect compatible with the project's current minimum platform.

## Follow-up theme consistency fixes

- Inspector panel and section surfaces now use the active palette's tinted glass surfaces rather than fixed navy fills.
- The 3D toolbar and its overflow menu now follow the active palette and use translucent, outlined surfaces.
- The app overflow menu uses the same palette-aware glass treatment.
- Shared editor text fields explicitly use the theme's readable foreground, label, cursor, and outline colors.
- Surface/container colors are tinted from the selected accent palette so Emerald, Amethyst, Amber, Rose, Teal, and Blue themes affect nested bars as well as the main navigation.
