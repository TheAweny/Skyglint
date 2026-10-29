package dev.skyglint.ui.settings

import dev.skyglint.ui.text.SettingsText
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.client.input.MouseButtonEvent

/** Keep native editing, focus and narration with the same face and material as the workspace. */
class SettingsEditBox(font: Font,x: Int,y: Int,width: Int,height: Int,title: String,private val placeholder: String="") :
    EditBox(font,x,y,width,height,Component.literal(title)) {
    private var viewStart=0
    private var selectionAnchor=0
    init { setBordered(false);setTextShadow(false);setMaxLength(128) }
    override fun extractWidgetRenderState(g:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,delta:Float) {
        SettingsPaint.rounded(g,x,y,width,height,6,if(isFocused)0x30415A6C else 0x1C8398A7)
        if(isFocused)SettingsPaint.rounded(g,x+8,y+height-2,width-16,1,0,SettingsPaint.ACCENT)
        SettingsIcons.draw(g,"search",x+8f,y+(height-12f)/2,12f,SettingsPaint.MUTED)
        g.enableScissor(x+23,y+2,x+width-6,y+height-2)
        viewStart=viewStart.coerceIn(0,value.length)
        if(cursorPosition<viewStart)viewStart=cursorPosition
        while(viewStart<cursorPosition&&SettingsText.width(value.substring(viewStart,cursorPosition))>width-37)
            viewStart=value.offsetByCodePoints(viewStart,1)
        val content=if(value.isEmpty())placeholder else value.substring(viewStart)
        val shown=SettingsText.fit(content,width-34)
        val ty=y+(height-8)/2
        val selection=highlighted
        if(isFocused&&selection.isNotEmpty()) {
            val start=if(value.startsWith(selection,cursorPosition))cursorPosition else (cursorPosition-selection.length).coerceAtLeast(0)
            val end=(start+selection.length).coerceAtMost(value.length)
            val left=x+26+SettingsText.width(value.substring(viewStart,start.coerceAtLeast(viewStart)))
            val right=x+26+SettingsText.width(value.substring(viewStart,end.coerceAtLeast(viewStart)))
            g.fill(left,ty-1,right,ty+11,0x705C859D)
        }
        SettingsText.draw(g,shown,x+26,ty,if(value.isEmpty())SettingsPaint.MUTED else SettingsPaint.INK)
        if(isFocused&&System.nanoTime()/500_000_000L%2==0L){
            val cursor=x+26+SettingsText.width(value.substring(viewStart,cursorPosition.coerceAtLeast(viewStart))).coerceAtMost(width-35)
            g.fill(cursor,ty-1,cursor+1,ty+10,SettingsPaint.INK)
        }
        g.disableScissor()
    }
    private fun indexAt(mouseX: Double):Int {
        val at=(mouseX-x-26).coerceAtLeast(0.0)
        var index=viewStart.coerceAtMost(value.length)
        while(index<value.length){val next=value.offsetByCodePoints(index,1)
            val left=SettingsText.width(value.substring(viewStart,index));val right=SettingsText.width(value.substring(viewStart,next))
            if(at<(left+right)/2.0)break
            index=next
        }
        return index
    }
    override fun onClick(event:MouseButtonEvent,doubleClick:Boolean){
        val index=indexAt(event.x())
        moveCursorTo(index,event.hasShiftDown())
        selectionAnchor=if(event.hasShiftDown())selectionAnchor else index
    }
    override fun onDrag(event:MouseButtonEvent,dx:Double,dy:Double){
        moveCursorTo(indexAt(event.x()),true);setHighlightPos(selectionAnchor)
    }
}
