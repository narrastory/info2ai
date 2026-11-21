package com.example.info2ai.director;

import net.minecraft.server.command.CommandOutput;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

final class CapturingOutput implements CommandOutput {
    private static final int MAX_MESSAGES = 32;
    private static final int MAX_CODE_POINTS = 400;
    private static final TextColor RED = TextColor.fromFormatting(Formatting.RED);
    private final List<String> output = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Override
    public void sendMessage(Text message) {
        if (output.size() + errors.size() >= MAX_MESSAGES) return;
        String value = message.getString();
        int length = Math.min(value.codePointCount(0, value.length()), MAX_CODE_POINTS);
        boolean error = RED.equals(message.getStyle().getColor());
        (error ? errors : output).add(value.substring(0, value.offsetByCodePoints(0, length)));
    }

    @Override public boolean shouldReceiveFeedback() { return true; }
    @Override public boolean shouldTrackOutput() { return true; }
    @Override public boolean shouldBroadcastConsoleToOps() { return false; }

    List<String> output() { return List.copyOf(output); }
    List<String> errors() { return List.copyOf(errors); }
}
