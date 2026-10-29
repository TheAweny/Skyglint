package dev.skyglint.ui.settings

import dev.skyglint.ui.text.SettingsText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import kotlin.math.exp

/** Vanilla supplies activation/narration; native appearance keeps interruptible hover and switch state. */
class SettingsButton(
    x: Int, y: Int, width: Int, height: Int,
    private val caption: String,
    private val value: () -> String = { "" },
    private val toggled: (() -> Boolean)? = null,
    val hint: String,
    action: () -> Unit,
) : Button(x, y, width, height, Component.literal(caption), OnPress { action() }, DEFAULT_NARRATION) {
    enum class Kind { ACTION, NAVIGATION, CARD, ROW, PRIMARY }
    var kind = Kind.ACTION
    var selected: () -> Boolean = { false }
    var icon: String? = null
    private var hover = 0.0
    private var switchAmount: Double? = null
    private var lastFrame = 0L
    private var hoverSince = 0L
    private var hintFullyVisible = false

    init { refreshMessage() }
    fun refreshMessage() { message = Component.literal(listOf(caption, value()).filter(String::isNotBlank).joinToString(": ")) }
    override fun createNarrationMessage() = super.createNarrationMessage().append(". ").append(hint)

    /** Call once after content scissor is disabled, for the last hovered visible button only. */
    fun renderModernTooltip(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if(!visible || !active || hoverSince==0L || System.nanoTime()-hoverSince<600_000_000L ||
            mouseX<x || mouseY<y || mouseX>=x+width || mouseY>=y+height ||
            hint.isBlank() || (kind==Kind.CARD && hintFullyVisible)) return
        SettingsTooltip.draw(g,caption,hint,mouseX,mouseY)
    }

    override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.nanoTime()
        val pointerOver = active && mouseX>=x && mouseY>=y && mouseX<x+width && mouseY<y+height
        if(pointerOver){if(hoverSince==0L)hoverSince=now}else hoverSince=0L
        val dt = if (lastFrame == 0L) 0.0 else ((now-lastFrame).coerceAtLeast(0)/1e9).coerceAtMost(.2)
        lastFrame = now
        fun approach(current: Double, target: Double, rate: Double) = current + (target-current)*(1-exp(-rate*dt))
        hover = approach(hover, if(isHoveredOrFocused && active)1.0 else 0.0,18.0)
        val chosen = selected()
        when(kind) {
            Kind.NAVIGATION -> SettingsPaint.rounded(g,x,y,width,height,7,((hover*18).toInt() shl 24) or 0xB3AABD)
            Kind.CARD -> {
                SettingsPaint.rounded(g,x,y,width,height,9,SettingsPaint.mix(0x4B555360,0x8C9B8CB8.toInt(),hover))
                SettingsPaint.rounded(g,x+1,y+1,width-2,height-2,8,SettingsPaint.mix(0xE31C202B.toInt(),0xF0262935.toInt(),hover))
            }
            Kind.ROW -> {
                SettingsPaint.rounded(g,x,y,width,height,6,((hover*17).toInt() shl 24) or 0xCAC4D5)
                g.fill(x+12,y+height-1,x+width-12,y+height,SettingsPaint.color(0x1E777380))
            }
            Kind.PRIMARY -> SettingsPaint.rounded(g,x,y,width,height,7,SettingsPaint.mix(0xFFC8BAEF.toInt(),0xFFDED3FB.toInt(),hover))
            Kind.ACTION -> SettingsPaint.rounded(g,x,y,width,height,7,SettingsPaint.mix(0x324D4D5E,0x595D596F,hover))
        }
        if(isFocused)SettingsPaint.rounded(g,x+9,y+height-2,width-18,1,0,SettingsPaint.ACCENT)
        val isPrimary = kind==Kind.PRIMARY
        val ink = if(isPrimary)0xFF26212F.toInt() else if(kind==Kind.NAVIGATION && !chosen)SettingsPaint.MUTED
            else if(active)SettingsPaint.INK else SettingsPaint.MUTED
        val symbol = icon
        val iconSize = if(kind==Kind.CARD)23f else 13f
        val inset = if(kind==Kind.CARD)14 else 11
        val textX = x+inset+if(symbol!=null)(iconSize+10).toInt() else 0
        val rawValue = value()
        val chevron = toggled==null && (kind==Kind.CARD || rawValue=="›")
        val displayedValue = rawValue.takeUnless { it=="•" || chevron }.orEmpty()
        val shownValue = fit(displayedValue,(width/2-12).coerceAtLeast(0),9.5f)
        val tail = when { toggled!=null -> 32;chevron -> 25;shownValue.isNotBlank() -> SettingsText.width(shownValue)+16;else -> 0 }
        val textBudget = (x+width-inset-tail-textX).coerceAtLeast(0)
        val detailed = height>=40 && kind!=Kind.NAVIGATION
        hintFullyVisible = detailed && SettingsText.width(hint,8.1f)<=textBudget
        val titleY = if(detailed)y+11f else y+(height-8f)/2
        if(symbol!=null)SettingsIcons.draw(g,symbol,(x+inset).toFloat(),y+(height-iconSize)/2,iconSize,
            if(chosen)SettingsPaint.ACCENT else ink)
        SettingsText.draw(g,fit(caption,textBudget,9.5f),textX.toFloat(),titleY,ink)
        if(detailed)SettingsText.drawSized(g,fit(hint,textBudget,8.1f),textX.toFloat(),y+29f,8.1f,
            if(isPrimary)0xD22D2738.toInt() else SettingsPaint.MUTED)
        if(toggled!=null) {
            val enabled=toggled.invoke()
            switchAmount=approach(switchAmount ?: if(enabled)1.0 else 0.0,if(enabled)1.0 else 0.0,22.0)
            val amount=switchAmount!!
            val sx=x+width-35f;val sy=y+(height-13f)/2
            SettingsPaint.rounded(g,sx,sy,25f,13f,6.5f,SettingsPaint.mix(0xFF474653.toInt(),SettingsPaint.ACCENT,amount))
            SettingsPaint.rounded(g,sx+2+12f*amount.toFloat(),sy+2,9f,9f,4.5f,
                SettingsPaint.mix(0xFFD8D5DE.toInt(),0xFFFAF8FF.toInt(),amount))
        } else if(chevron)SettingsIcons.draw(g,"chevron",x+width-22f,y+(height-12f)/2,12f,SettingsPaint.MUTED)
        else if(shownValue.isNotBlank())SettingsText.draw(g,shownValue,(x+width-inset-SettingsText.width(shownValue)).toFloat(),titleY,
            if(isPrimary)ink else if(active)SettingsPaint.ACCENT else SettingsPaint.MUTED)
    }

    private fun fit(text: String, budget: Int, size: Float): String {
        if(budget<=0)return ""
        if(SettingsText.width(text,size)<=budget)return text
        val ellipsis="…"
        if(SettingsText.width(ellipsis,size)>budget)return ""
        val points=text.codePoints().toArray()
        var low=0;var high=points.size
        while(low<high){val mid=(low+high+1)/2
            if(SettingsText.width(String(points,0,mid)+ellipsis,size)<=budget)low=mid else high=mid-1}
        return String(points,0,low)+ellipsis
    }
}
