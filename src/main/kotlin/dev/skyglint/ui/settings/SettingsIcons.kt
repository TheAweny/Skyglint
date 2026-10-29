package dev.skyglint.ui.settings

import dev.skyglint.ui.HudItemRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.*

/** Native category items and a common-stroke, analytically antialiased navigation icon set. */
object SettingsIcons {
    /** No synthetic player heads; the item components require an initialized world. */
    fun item(id: String): ItemStack? {
        if (Minecraft.getInstance().level == null) return null
        val item = when (id) {
            "fishing" -> Items.FISHING_ROD
            "mining" -> Items.DIAMOND_PICKAXE
            "garden", "jacob" -> Items.WHEAT
            "dungeon", "dungeons", "slayer" -> Items.IRON_SWORD
            "pets", "pet" -> Items.BONE
            "rewards", "reward" -> Items.CHEST
            "skills" -> Items.ENCHANTED_BOOK
            "activity" -> Items.COMPASS
            "buffs" -> Items.POTION
            "winter", "jerry" -> Items.SNOWBALL
            else -> return null
        }
        return ItemStack(item)
    }

    fun draw(g: GuiGraphicsExtractor, id: String, x: Float, y: Float, size: Float, color: Int) {
        if (!size.isFinite() || size <= 0 || !x.isFinite() || !y.isFinite()) return
        item(id)?.let { native ->
            val visibleSize = size * sqrt(SettingsPaint.opacity.coerceIn(0f,1f))
            if (visibleSize <= .05f) return
            g.pose().pushMatrix()
            try {
                g.pose().translate(x+(size-visibleSize)/2,y+(size-visibleSize)/2)
                g.pose().scale(visibleSize/16f,visibleSize/16f)
                HudItemRenderer.item(g,native,0,0)
            } finally { g.pose().popMatrix() }
            return
        }
        g.pose().pushMatrix()
        try {
            g.pose().translate(x,y);g.pose().scale(size/24f,size/24f)
            fun segment(ax:Float,ay:Float,bx:Float,by:Float,stroke:Float=1.7f) {
                val dx=bx-ax;val dy=by-ay
                g.pose().pushMatrix()
                try {
                    g.pose().translate(ax,ay);g.pose().rotate(atan2(dy,dx))
                    SettingsPaint.rounded(g,-stroke/2,-stroke/2,hypot(dx,dy)+stroke,stroke,stroke/2,color)
                } finally { g.pose().popMatrix() }
            }
            fun path(vararg v:Float) { for(i in 0 until v.size-2 step 2)segment(v[i],v[i+1],v[i+2],v[i+3]) }
            fun ring(cx:Float,cy:Float,r:Float,start:Int=0,count:Int=24) {
                for(i in start until start+count){val a=i*PI/12;val b=(i+1)*PI/12
                    segment(cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,cx+cos(b).toFloat()*r,cy+sin(b).toFloat()*r,1.6f)}
            }
            fun box(l:Float,t:Float,r:Float,b:Float)=path(l,t,r,t,r,b,l,b,l,t)
            when(id) {
                "hud","layout" -> {box(3f,5f,21f,19f);segment(7f,9f,17f,9f);path(9f,15f,12f,12f,15f,15f);segment(12f,12f,12f,17f)}
                "modules" -> {box(3f,3f,9f,9f);box(15f,3f,21f,9f);box(3f,15f,9f,21f);box(15f,15f,21f,21f)}
                "events" -> {path(5f,17f,7f,14f,7f,9f,9f,5f,15f,5f,17f,9f,17f,14f,19f,17f,5f,17f);segment(10f,20f,14f,20f)}
                "appearance" -> {ring(12f,12f,9f);segment(12f,4f,12f,20f);segment(8f,7f,8f,17f);segment(5f,10f,5f,14f)}
                "updates" -> {ring(12f,12f,8.5f,1,18);path(17f,3f,21f,4f,20f,8f);path(12f,7f,12f,16f,8f,12f);segment(12f,16f,16f,12f)}
                "diagnostics" -> {box(3f,4f,21f,20f);path(6f,14f,9f,10f,12f,15f,17f,8f)}
                "back","arrowleft" -> {path(14f,5f,7f,12f,14f,19f);segment(7f,12f,21f,12f)}
                "arrowright" -> {path(10f,5f,17f,12f,10f,19f);segment(3f,12f,17f,12f)}
                "search" -> {ring(10f,10f,6f);segment(15f,15f,21f,21f)}
                "chevron" -> path(9f,5f,16f,12f,9f,19f)
                "spark" -> {path(12f,2f,14.5f,9.5f,22f,12f,14.5f,14.5f,12f,22f,9.5f,14.5f,2f,12f,9.5f,9.5f,12f,2f)}
                "fishing" -> {path(4f,21f,16f,3f,21f,17f);path(21f,17f,19f,21f,16f,21f,14f,18f)}
                "mining" -> {path(3f,6f,8f,3f,15f,4f,21f,10f);segment(13f,5f,5f,21f)}
                "garden","jacob" -> {segment(12f,3f,12f,22f);for(i in 0..2)path(5f,5f+i*5f,12f,10f+i*5f,19f,5f+i*5f)}
                "dungeon","dungeons","slayer" -> {path(4f,20f,18f,6f,20f,3f,17f,4f,3f,18f);segment(8f,13f,12f,17f)}
                "pets","pet" -> {ring(12f,16f,4f);for(i in 0..2)ring(6f+i*6f,6f+abs(i-1)*2,2f)}
                "rewards","reward" -> {box(4f,8f,20f,20f);segment(3f,12f,21f,12f);segment(12f,8f,12f,20f);path(12f,8f,7f,3f,5f,5f,12f,8f,17f,3f,19f,5f,12f,8f)}
                "skills" -> {path(3f,5f,12f,7f,21f,5f,21f,19f,12f,21f,3f,19f,3f,5f);segment(12f,7f,12f,21f)}
                "buffs" -> {path(9f,3f,15f,3f,15f,8f,20f,17f,18f,21f,6f,21f,4f,17f,9f,8f,9f,3f);segment(7f,15f,17f,15f)}
                "winter","jerry" -> {segment(12f,2f,12f,22f);segment(3f,7f,21f,17f);segment(3f,17f,21f,7f);path(8f,4f,12f,8f,16f,4f);path(8f,20f,12f,16f,16f,20f)}
                "activity" -> {ring(12f,12f,9f);path(15f,8f,13f,13f,8f,16f,10f,11f,15f,8f)}
                else -> {ring(12f,12f,8f);segment(12f,8f,12f,13f);SettingsPaint.rounded(g,11f,16f,2f,2f,1f,color)}
            }
        } finally { g.pose().popMatrix() }
    }
}
