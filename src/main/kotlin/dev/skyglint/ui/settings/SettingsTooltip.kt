package dev.skyglint.ui.settings

import dev.skyglint.ui.text.SettingsText
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Paint explicitly after the content scissor; no vanilla pixel font/background is enqueued. */
object SettingsTooltip {
    fun draw(g: GuiGraphicsExtractor, title: String, hint: String, mouseX: Int, mouseY: Int) {
        val available=(g.guiWidth()-20).coerceAtLeast(16)
        val contentWidth=(available-24).coerceAtLeast(1).coerceAtMost(258)
        val lines=wrap(hint,contentWidth)
        val maximumLines=((g.guiHeight()-58)/12).coerceAtLeast(1)
        val visible=lines.take(maximumLines).toMutableList()
        if(lines.size>maximumLines)visible[visible.lastIndex]=fit(visible.last()+"…",contentWidth,8.5f)
        val titleText=fit(title,contentWidth,9.5f)
        val inkWidth=maxOf(SettingsText.width(titleText),visible.maxOfOrNull{SettingsText.width(it,8.5f)}?:0)
        val width=(inkWidth+24).coerceAtMost(available)
        val height=34+visible.size*12
        val left=(mouseX+13).coerceAtMost(g.guiWidth()-width-8).coerceAtLeast(8)
        val top=(if(mouseY+height+18>g.guiHeight())mouseY-height-10 else mouseY+18).coerceAtLeast(8)
        SettingsPaint.rounded(g,left,top,width,height,9,0xAE514B64.toInt())
        SettingsPaint.rounded(g,left+1,top+1,width-2,height-2,8,0xF91C1E29.toInt())
        SettingsText.draw(g,titleText,left+12,top+11,SettingsPaint.INK)
        visible.forEachIndexed { i,line ->
            SettingsText.drawSized(g,line,(left+12).toFloat(),(top+29+i*12).toFloat(),8.5f,0xFFB4B1C0.toInt())
        }
    }

    private fun wrap(raw: String, budget: Int): List<String> {
        val words=raw.replace('\n',' ').replace('\r',' ').split(Regex("\\s+")).filter(String::isNotEmpty)
        val result=mutableListOf<String>()
        var current=""
        for(word in words) {
            val proposed=if(current.isEmpty())word else "$current $word"
            if(SettingsText.width(proposed,8.5f)<=budget){current=proposed;continue}
            if(current.isNotEmpty()){result+=current;current=""}
            if(SettingsText.width(word,8.5f)<=budget){current=word;continue}
            // Split long identifiers by codepoint so every segment remains inside the tooltip.
            val points=word.codePoints().toArray()
            var segment=""
            for(point in points){val next=segment+String(Character.toChars(point))
                if(segment.isNotEmpty()&&SettingsText.width(next,8.5f)>budget){result+=segment;segment=String(Character.toChars(point))}
                else segment=next}
            current=segment
        }
        if(current.isNotEmpty())result+=current
        return result
    }

    private fun fit(text: String,budget: Int,size: Float):String {
        if(SettingsText.width(text,size)<=budget)return text
        if(SettingsText.width("…",size)>budget)return ""
        val points=text.codePoints().toArray();var low=0;var high=points.size
        while(low<high){val mid=(low+high+1)/2
            if(SettingsText.width(String(points,0,mid)+"…",size)<=budget)low=mid else high=mid-1}
        return String(points,0,low)+"…"
    }
}
