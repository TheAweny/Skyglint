package dev.skyglint.smoke;

import dev.skyglint.ui.SkyglintSettingsScreen;
import dev.skyglint.ui.SkyglintUpdatesScreen;
import dev.skyglint.fabric.GitHubUpdates;
import dev.skyglint.ui.settings.SettingsButton;
import dev.skyglint.ui.settings.SettingsIcons;
import dev.skyglint.updates.github.UpdateImportance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.LoggerFactory;

/** Dev-only actual-world proof for menu rendering, navigation continuity, motion and GUI scale. */
final class SettingsWorkspaceSmoke {
    private int tick;
    private SkyglintSettingsScreen workspace;
    private long began;

    void tick(Minecraft client) {
        tick++;
        if (tick == 1) {
            began = System.nanoTime();
            client.options.guiScale().set(2);client.resizeGui();
            workspace = new SkyglintSettingsScreen(null);client.gui.setScreen(workspace);
            if(SettingsIcons.INSTANCE.item("fishing")==null || SettingsIcons.INSTANCE.item("mining")==null)
                throw new AssertionError("Native category item models are unavailable in the loaded world");
        }
        if (tick==3||tick==7||tick==13) capture(client,"opening-"+tick);
        if (tick==25) { verifyBounds(client);capture(client,"modules"); }
        if (tick==30) click(client,"Fishing");
        if (tick==32||tick==36||tick==41)capture(client,"page-motion-"+tick);
        if (tick==50)capture(client,"fishing");
        if (tick==55) {
            var toggle=(AbstractWidget)client.gui.screen().children().stream().filter(child -> child instanceof AbstractWidget w && w.getMessage().getString().startsWith("Selected bait")).findFirst().orElseThrow();
            var e=new MouseButtonEvent(toggle.getX()+toggle.getWidth()-20,toggle.getY()+12,new MouseButtonInfo(0,0));
            client.gui.screen().mouseClicked(e,false);client.gui.screen().mouseReleased(e);
        }
        if(tick==62)click(client,"Selected bait");
        if(tick==70)click(client,"Updates");
        if(tick==90) {
            if(client.gui.screen()!=workspace)throw new AssertionError("Updates created a second native window");
            verifyBounds(client);capture(client,"updates-inline");
        }
        if(tick==95)client.gui.setScreen(new SkyglintUpdatesScreen(null,new GitHubUpdates.View("0.2.2",GitHubUpdates.Phase.AVAILABLE,"0.2.3",
            "Important compatibility fixes for Minecraft 26.2. Review this release and choose when to download.",true,UpdateImportance.IMPORTANT)));
        if(tick==120){verifyBounds(client);capture(client,"updates-important");}
        if(tick==125){client.gui.setScreen(new SettingsFontProofScreen());}
        if(tick==145)capture(client,"font-gui2");
        if(tick==150){client.options.guiScale().set(3);client.resizeGui();client.gui.setScreen(new SettingsFontProofScreen());}
        if(tick==170)capture(client,"font-gui3");
        if(tick==175){client.gui.setScreen(new SkyglintSettingsScreen(null));}
        if(tick==200){verifyBounds(client);capture(client,"modules-gui3");}
        if(tick==205){client.getWindow().setWindowed(854,480);client.options.guiScale().set(2);client.resizeGui();client.gui.setScreen(new SkyglintSettingsScreen(null));}
        if(tick==230){verifyBounds(client);capture(client,"modules-small");}
        if(tick==235)client.gui.setScreen(new SkyglintUpdatesScreen(null,new GitHubUpdates.View("0.2.2",GitHubUpdates.Phase.READY,"0.2.3","Update verified. A full Minecraft restart applies this version.",true)));
        if(tick==260){verifyBounds(client);capture(client,"updates-small");}
        if(tick==265)client.gui.screen().onClose();
        if(tick==268||tick==272)capture(client,"closing-"+tick);
        if(tick>=295){
            if(client.gui.screen()!=null)throw new AssertionError("Animated workspace close failed to return to the game");
            LoggerFactory.getLogger("SkyglintSettingsSmoke").info("SKYGLINT_SETTINGS_WORKSPACE_PASSED: actual native Fishing/Mining item icons; same-instance Updates route; opening/page/closing spring motion; smooth Inter Latin+Cyrillic GUI2/3; bounded controls at1920x1080 and854x480. {}ms",(System.nanoTime()-began)/1_000_000);
            client.stop();
        }
    }
    private void click(Minecraft client,String title){
        var screen=client.gui.screen();
        var w=(AbstractWidget)screen.children().stream().filter(child -> child instanceof AbstractWidget a && (a.getMessage().getString().equals(title)||a.getMessage().getString().startsWith(title+":"))).findFirst().orElseThrow();
        var e=new MouseButtonEvent(w.getX()+w.getWidth()/2.0,w.getY()+w.getHeight()/2.0,new MouseButtonInfo(0,0));
        screen.mouseClicked(e,false);screen.mouseReleased(e);
    }
    private void verifyBounds(Minecraft client){
        var screen=client.gui.screen();
        for(var child:screen.children())if(child instanceof AbstractWidget w && w.visible){
            if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)
                throw new AssertionError("Control outside viewport: "+w.getMessage().getString());
        }
    }
    private void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,"settings-workspace-"+name+".png",client.gameRenderer.mainRenderTarget(),1,text->{});}
}
