package dev.fanworld.halolite

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class HaloTileService : TileService() {

    override fun onStartListening() = render()

    override fun onClick() {
        val target = !StripLight.isOn(this)
        if (!StripLight.set(this, target)) {
            Toast.makeText(
                this,
                "Нет доступа: запусти Shizuku и открой HaloLite один раз",
                Toast.LENGTH_LONG
            ).show()
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = StripLight.isOn(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "HaloLite"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (on) "Вкл" else "Выкл"
        }
        tile.updateTile()
    }
}
