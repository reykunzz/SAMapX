package com.smpe.mapeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

// Ikon kecil (path Material) supaya tidak perlu dependensi material-icons-extended.
private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

object UiIcons {
    val Undo: ImageVector by lazy {
        icon("undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }
    val Redo: ImageVector by lazy {
        icon("redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    }
    val Pan: ImageVector by lazy {
        icon("pan", "M10,9h4V6h3l-5,-5 -5,5h3v3zM9,10H6V7l-5,5 5,5v-3h3v-4zM23,12l-5,-5v3h-3v4h3v3l5,-5zM14,15h-4v3H7l5,5 5,-5h-3v-3z")
    }
    val Focus: ImageVector by lazy {
        icon("focus", "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM5,15H3v4c0,1.1 0.9,2 2,2h4v-2H5v-4zM5,5h4V3H5c-1.1,0 -2,0.9 -2,2v4h2V5zM19,3h-4v2h4v4h2V5c0,-1.1 -0.9,-2 -2,-2zM19,19h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4z")
    }
    val FitAll: ImageVector by lazy {
        icon("fit_all", "M15,3l2.3,2.3 -2.89,2.87 1.42,1.42L18.7,6.7 21,9V3zM3,9l2.3,-2.3 2.87,2.89 1.42,-1.42L6.7,5.3 9,3H3zM9,21l-2.3,-2.3 2.89,-2.87 -1.42,-1.42L5.3,17.3 3,15v6zM21,15l-2.3,2.3 -2.87,-2.89 -1.42,1.42 2.89,2.87L15,21h6z")
    }
    val Copy: ImageVector by lazy {
        icon("copy", "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z")
    }
    val Rotate get() = Icons.Default.Refresh
    val Panel get() = Icons.Default.Menu
}

@Composable
fun TbBtn(
    icon: ImageVector, desc: String,
    selected: Boolean = false, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(if (selected) cs.primary else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, desc, Modifier.size(20.dp),
            tint = when {
                selected -> cs.onPrimary
                enabled -> tint ?: Color.White
                else -> Color.White.copy(alpha = 0.3f)
            }
        )
    }
}

@Composable
private fun TbDivider() {
    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.18f)))
}

/** Toolbar viewport 3D: satu pil kecil berisi ikon saja. */
@Composable
fun ViewToolbar(
    panMode: Boolean, setPan: (Boolean) -> Unit,
    canUndo: Boolean, canRedo: Boolean,
    onFocus: () -> Unit, onAll: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit,
    panelVisible: Boolean?, onTogglePanel: () -> Unit,
    modifier: Modifier = Modifier,
    snap: Float? = null, onSnap: () -> Unit = {}
) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = Color(0xE6121714)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TbBtn(UiIcons.Rotate, "Putar", selected = !panMode) { setPan(false) }
            TbBtn(UiIcons.Pan, "Geser", selected = panMode) { setPan(true) }
            TbDivider()
            TbBtn(UiIcons.Focus, "Fokus ke objek", onClick = onFocus)
            TbBtn(UiIcons.FitAll, "Tampilkan semua", onClick = onAll)
            TbDivider()
            TbBtn(UiIcons.Undo, "Urungkan", enabled = canUndo, onClick = onUndo)
            TbBtn(UiIcons.Redo, "Ulangi", enabled = canRedo, onClick = onRedo)
            if (snap != null) {
                TbDivider()
                val cs = MaterialTheme.colorScheme
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (snap > 0f) cs.primary else Color.Transparent)
                        .clickable(role = Role.Button, onClick = onSnap).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val v = if (snap == snap.toInt().toFloat()) snap.toInt().toString() else snap.toString()
                    Text(if (snap == 0f) "Snap off" else "Snap $v", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (snap > 0f) cs.onPrimary else Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            }
            if (panelVisible != null) {
                TbDivider()
                TbBtn(UiIcons.Panel, "Inspector", selected = panelVisible, onClick = onTogglePanel)
            }
        }
    }
}
