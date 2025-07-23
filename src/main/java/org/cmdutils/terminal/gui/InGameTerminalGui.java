package org.cmdutils.terminal.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.cmdutils.MainClient;
import org.cmdutils.command.CommandEnvironment;
import org.cmdutils.command.CommandParser;
import org.cmdutils.command.RunnableCommand;
import org.cmdutils.terminal.logger.InGameLogger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class InGameTerminalGui extends Screen {
    public EditBoxWidget log;
    public InGameLogger logger;

    public TextFieldWidget prompt;

    public final Screen previousScreen;
    public final ScreenHandler previousScreenHandler;

    private final List<RunnableCommand> queuedCommands = new ArrayList<>();

    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    // Removed the unused 'logText' and 'promptText' fields
    // as they are no longer needed for the new widget constructors/builders.

    public InGameTerminalGui(Screen previousScreen, ScreenHandler previousScreenHandler) {
        super(Text.of("CMD-Utils Terminal"));

        this.previousScreen = previousScreen;
        this.previousScreenHandler = previousScreenHandler;
    }

    @Override
    public void init() {
        // --- Updated EditBoxWidget (log) initialization for Minecraft 1.21.8 ---
        // The constructor signature changed. The anonymous inner class for charTyped
        // is replaced by setting setEditable(false).
        this.log = new EditBoxWidget(
                this.textRenderer,
                (this.width / 2) - this.width / 4,
                this.height / 4,
                this.width / 2,
                this.height / 3,
                Text.of(""), // Initial content text
                Text.of("Terminal Log") // Message/tooltip text for the widget
        );
        this.log.setEditable(false); // Make the log box read-only

        this.logger = new InGameLogger(this.log);

        // --- Updated TextFieldWidget (prompt) initialization for Minecraft 1.21.8 ---
        // The direct constructor with TextRenderer, dimensions, initial text, and message is valid.
        // The anonymous keyPressed override is removed from here and its logic moved to the Screen's keyPressed method.
        this.prompt = new TextFieldWidget(
                this.textRenderer,
                (this.width / 2) - this.width / 4,
                (this.height / 3) + this.height / 4 + 10,
                this.width / 2,
                20,
                Text.of(""), // Initial content text for the prompt
                Text.of("Enter Command") // Message/tooltip text for the prompt
        );
        this.prompt.setMaxLength(4096);

        // Add the widgets to the screen
        this.addDrawableChild(this.log);
        this.addDrawableChild(this.prompt);

        // Set initial focus to the prompt
        this.setInitialFocus(this.prompt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // First, allow the prompt TextFieldWidget to handle its default key presses
        // (e.g., typing, backspace, left/right arrow for cursor movement).
        // If the prompt consumes the key, we don't need to do further processing in this method.
        if (this.prompt.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Now, implement the custom key handling logic (Enter, Up, Down arrows)
        // specifically for the prompt, only if it's focused.
        if (this.prompt.isFocused()) {
            // Handle ENTER key for command submission
            if (keyCode == GLFW.GLFW_KEY_ENTER && (!this.prompt.getText().isEmpty() || !this.queuedCommands.isEmpty())) {
                RunnableCommand command = CommandParser.parseCommand(this.prompt.getText(), this.logger, CommandEnvironment.IN_GAME);
                
                // Add command to history if not empty and not a duplicate of the last command
                if (!this.prompt.getText().isEmpty() && (this.commandHistory.isEmpty() || !this.commandHistory.get(this.commandHistory.size() - 1).equals(this.prompt.getText()))) {
                    this.commandHistory.add(this.prompt.getText());
                }

                this.historyIndex = this.commandHistory.size(); // Reset history index to the end

                // Add the current command to the queue if not empty
                if (!this.prompt.getText().isEmpty()) {
                    this.queuedCommands.add(command);
                }
                this.prompt.setText(""); // Clear the prompt input field

                // If Control key is held down, prevent further processing of this key event.
                // This was part of the original logic.
                if (hasControlDown()) {
                    return false;
                }

                // Execute all queued commands
                for (RunnableCommand c : this.queuedCommands) {
                    if (c == null) {
                        this.logger.error("Invalid Command.");
                    } else {
                        try {
                            c.execute();
                        } catch (Exception ex) {
                            MainClient.LOGGER.error("Exception thrown while executing CMD-Utils command.", ex);
                        }
                    }
                }

                this.queuedCommands.clear(); // Clear the command queue
                return true; // Key event was handled
            }

            // Handle UP arrow for command history navigation (previous command)
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (this.historyIndex > 0) {
                    this.prompt.setText(this.commandHistory.get(--this.historyIndex));
                }
                return true; // Key event was handled
            }

            // Handle DOWN arrow for command history navigation (next command)
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (this.historyIndex < this.commandHistory.size() - 1) {
                    this.prompt.setText(this.commandHistory.get(++this.historyIndex));
                } else if (this.historyIndex == this.commandHistory.size() - 1) {
                    // If at the last command, pressing down clears the prompt
                    this.historyIndex++;
                    this.prompt.setText("");
                }
                return true; // Key event was handled
            }
        }

        // If none of the above conditions handled the key press,
        // defer to the superclass (Screen) for default behavior.
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render the background of the screen
        super.renderBackground(context, mouseX, mouseY, delta);
        // Render the widgets added to the screen (log and prompt)
        super.render(context, mouseX, mouseY, delta);

        // Draw the title text
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of("CMD-Utils Terminal"), this.width / 2, this.height / 4 - 20, 0xFFFFFF);
    }

    @Override
    public void close() {
        // When the terminal screen is closed, return to the previous screen.
        if (this.client != null) {
            this.client.setScreen(this.previousScreen);
            // If there's a player, ensure their current screen handler is restored.
            if (this.client.player != null) {
                this.client.player.currentScreenHandler = this.previousScreenHandler;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        // The terminal screen should not pause the game.
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Allow the screen to be closed by pressing the Escape key.
        return true;
    }
}