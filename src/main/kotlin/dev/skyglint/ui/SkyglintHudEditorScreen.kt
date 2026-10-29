package dev.skyglint.ui

import dev.skyglint.fabric.SkyglintClient
import dev.skyglint.ui.layout.HudPlacement
import dev.skyglint.ui.settings.*
import dev.skyglint.ui.text.SettingsText as HudText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** One draft for the complete row; no config writes during drag or scale changes. */
class SkyglintHudEditorScreen(private val parent: Screen?, private val actions: SettingsActions) : Screen(Component.literal("Skyglint HUD layout")) {
    private var draft = actions.config.let { HudPlacement(it.hudOffsetX,it.hudPositionY,it.hudScale) }
    private val wasPreview = actions.preview
    private val samples=dev.skyglint.preview.HudPreviewController().apply { setEnabled(true,SkyglintClient.now()) }
    private var initialized = false
    private var draggingRow = false
    private var dragX = 0.0; private var dragY = 0.0
    private var snap = true
    private var resetLegacyOffset = false
    private var status = "Drag the row · scroll to scale · arrows to nudge"
    private lateinit var percent: EditBox
    private lateinit var slider: SettingsSlider
    private var inspectorX=0; private var inspectorY=0; private var inspectorW=0
    override fun init() {
        if(!initialized){ initialized=true; actions.setPreview(true) }
        val compact=height<300
        inspectorW=minOf(220,width-24).coerceAtLeast(150);inspectorX=width-inspectorW-12;inspectorY=(height-48-if(compact)144 else 200).coerceAtLeast(40)
        slider=SettingsSlider(inspectorX+14,inspectorY+if(compact)24 else 42,inspectorW-28,draft.scale) { value ->
            draft=draft.copy(scale=value).sanitized()
            SkyglintHud.editorPlacement=draft
            if(::percent.isInitialized)percent.setValue(String.format(java.util.Locale.ROOT,"%.2f",draft.scale*100))
        }
        addRenderableWidget(slider)
        val inputY=inspectorY+if(compact)65 else 92
        percent=SettingsEditBox(font,inspectorX+14,inputY,inspectorW-94,23,"Scale percentage","33.33–200%")
        percent.setMaxLength(6);percent.setValue(String.format(java.util.Locale.ROOT,"%.2f",draft.scale*100));percent.setHint(Component.literal("33.33–200"))
        percent.setResponder { value -> value.toDoubleOrNull()?.takeIf { it in 33.33..200.0 }?.let { draft=draft.copy(scale=it/100).sanitized();slider.synchronize(draft.scale);SkyglintHud.editorPlacement=draft } }
        addRenderableWidget(percent)
        addRenderableWidget(SettingsButton(inspectorX+inspectorW-74,inputY,60,23,"Set",hint="Use the exact percentage. 100% preserves the agreed HUD size.") {
            if(validPercent())rebuildWidgets()
        })
        addRenderableWidget(SettingsButton(inspectorX+14,inspectorY+if(compact)92 else 128,inspectorW-28,if(compact)20 else 25,"Snap to center",toggled={snap},hint="Snap the group to the horizontal center guide.") {snap=!snap})
        addRenderableWidget(SettingsButton(inspectorX+14,inspectorY+if(compact)117 else 162,inspectorW-28,if(compact)20 else 25,"Reset this group",hint="Restore top-center placement and 100% without changing other settings.") {
            draft=HudPlacement();resetLegacyOffset=true;rebuildWidgets()
        })
        val buttonY=height-40
        addRenderableWidget(SettingsButton(12,buttonY,100,26,"Cancel",{"Esc"},hint="Discard the layout draft."){onClose()})
        addRenderableWidget(SettingsButton(width-112,buttonY,100,26,"Apply",hint="Save only position and scale, preserving all other preferences.") {
            if(validPercent())try {
                val value=draft.sanitized()
                actions.changeConfig { it.copy(hudScale=value.scale,hudOffsetX=value.offsetX,hudPositionY=value.positionY,topOffset=if(resetLegacyOffset&&value.positionY==null)12 else it.topOffset) }
                onClose()
            }catch(failure:Exception){status="Could not save: ${failure.message ?: failure.javaClass.simpleName}"}
        })
        SkyglintHud.editorPlacement=draft
        SkyglintHud.editorSnapshot=samples.snapshot(SkyglintClient.now())
    }
    private fun validPercent():Boolean {
        val value=percent.value.toDoubleOrNull()
        if(value==null||value !in 33.33..200.0){status="Enter a scale from 33.33% to 200%.";return false}
        draft=draft.copy(scale=value/100).sanitized();return true
    }
    override fun extractRenderState(g:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,delta:Float) {
        g.fill(0,0,width,height,0x30050709)
        SkyglintHud.editorPlacement=draft
        SkyglintHud.editorSnapshot=samples.snapshot(SkyglintClient.now())
        SkyglintHud.render(g,true)
        g.fill(width/2,38,width/2+1,height-52,0x405F6B74)
        if(height>=300){
            SettingsPaint.rounded(g,width/2-146,14,292,28,14,0xDB18212A.toInt())
            HudText.draw(g,"HUD layout   ·   Sample data",width/2-125,24,SettingsPaint.INK)
        }
        SkyglintHud.editorBounds?.let { b ->
            val x=b.x.roundToInt()-3;val y=b.y.roundToInt()-3;val r=(b.x+b.width).roundToInt()+3;val bottom=(b.y+b.height).roundToInt()+3
            g.fill(x,y,r,y+1,0xFFCED5DA.toInt());g.fill(x,bottom-1,r,bottom,0xFFCED5DA.toInt())
            g.fill(x,y,x+1,bottom,0xFFCED5DA.toInt());g.fill(r-1,y,r,bottom,0xFFCED5DA.toInt())
        }
        SettingsPaint.rounded(g,inspectorX,inspectorY,inspectorW,if(height<300)144 else 200,10,0xDC191D22.toInt())
        HudText.draw(g,"Main HUD · linked row",inspectorX+14,inspectorY+if(height<300)8 else 15,SettingsPaint.INK)
        if(height>=300){g.pose().pushMatrix();try{g.pose().translate((inspectorX+14).toFloat(),(inspectorY+79).toFloat());g.pose().scale(.7f,.7f)
            HudText.draw(g,"100% = 45 physical pixels",0,0,SettingsPaint.MUTED)
        }finally{g.pose().popMatrix()}}
        g.pose().pushMatrix();try{g.pose().translate(124f,(height-31).toFloat());g.pose().scale(.7f,.7f)
            HudText.draw(g,HudText.fit(status,((width-250)/.7).toInt().coerceAtLeast(10)),0,0,SettingsPaint.MUTED)
        }finally{g.pose().popMatrix()}
        super.extractRenderState(g,mouseX,mouseY,delta)
    }
    override fun mouseClicked(event:MouseButtonEvent,doubleClick:Boolean):Boolean {
        if(super.mouseClicked(event,doubleClick))return true
        if(event.button()==0&&SkyglintHud.editorBounds?.contains(event.x(),event.y())==true){clearFocus();draggingRow=true;val b=SkyglintHud.editorBounds!!;dragX=event.x()-b.x;dragY=event.y()-b.y;return true}
        return false
    }
    override fun mouseDragged(event:MouseButtonEvent,dx:Double,dy:Double):Boolean {
        if(draggingRow){move(event.x()-dragX,event.y()-dragY);return true}
        return super.mouseDragged(event,dx,dy)
    }
    private fun move(x:Double,y:Double,snapToGuide:Boolean=snap){val bounds=SkyglintHud.editorBounds?:return
        val travelX=(width-bounds.width-12).coerceAtLeast(0.0);val travelY=(height-bounds.height-12).coerceAtLeast(0.0)
        var horizontal=if(travelX>0)((x-6)/travelX*2-1).coerceIn(-1.0,1.0) else 0.0
        if(snapToGuide&&kotlin.math.abs(horizontal*travelX/2)<5)horizontal=0.0
        draft=draft.copy(offsetX=horizontal,positionY=if(travelY>0)((y-6)/travelY).coerceIn(0.0,1.0) else 0.0)
        SkyglintHud.editorPlacement=draft
    }
    override fun mouseReleased(event:MouseButtonEvent):Boolean {if(draggingRow){draggingRow=false;return true};return super.mouseReleased(event)}
    override fun mouseScrolled(mouseX:Double,mouseY:Double,horizontal:Double,vertical:Double):Boolean {
        if(SkyglintHud.editorBounds?.contains(mouseX,mouseY)==true){draft=draft.copy(scale=(draft.scale+vertical*.05).coerceIn(1.0/3.0,2.0));rebuildWidgets();return true}
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical)
    }
    override fun keyPressed(event:KeyEvent):Boolean {
        if(event.isEscape){onClose();return true}
        if(focused===percent||focused===slider)return super.keyPressed(event)
        val step=if(event.hasShiftDown())10.0 else 1.0
        val b=SkyglintHud.editorBounds
        if(b!=null&&(event.isLeft||event.isRight||event.isUp||event.isDown)){
            move(b.x+when{event.isLeft->-step;event.isRight->step;else->0.0},b.y+when{event.isUp->-step;event.isDown->step;else->0.0},false);return true
        }
        return super.keyPressed(event)
    }
    override fun removed(){SkyglintHud.editorPlacement=null;SkyglintHud.editorBounds=null;SkyglintHud.editorSnapshot=null;actions.setPreview(wasPreview);super.removed()}
    override fun onClose(){minecraft.gui.setScreen(parent)}
    override fun isPauseScreen()=false
}
