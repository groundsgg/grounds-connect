package gg.grounds.connect.ui.logs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public final class LogLineList extends ObjectSelectionList<LogLineList.Line> {

    public LogLineList(Minecraft mc, int width, int height, int top, int itemHeight) {
        super(mc, width, height, top, itemHeight);
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - 12;
    }

    public void addLine(String text) {
        addEntry(new Line(text));
    }

    public void clear() {
        clearEntries();
    }

    public String joined() {
        StringBuilder sb = new StringBuilder();
        for (Line line : this.children()) {
            sb.append(line.text).append('\n');
        }
        return sb.toString();
    }

    public static final class Line extends ObjectSelectionList.Entry<Line> {

        final String text;

        Line(String text) {
            this.text = text;
        }

        @Override
        public Component getNarration() {
            return Component.literal(text);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            extractor.text(font, text, getContentX() + 2, getContentY() + 1, 0xFFE0E0E0);
        }
    }
}
