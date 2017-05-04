package org.lazywizard.console;

import java.awt.Color;
import java.io.IOException;
import java.security.CodeSource;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import javax.swing.UIManager;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import org.lazywizard.console.CommandStore.StoredCommand;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.StringUtils;

/**
 * The main class of the console mod. Most of its methods aren't publicly
 * accessible, so this is mainly used to display messages to the player.
 * <p>
 * @author LazyWizard
 * @since 2.0
 */
public class Console
{
    private static final Logger Log = Global.getLogger(Console.class);
    private static ConsoleSettings settings;
    // Stores the output of the console until it can be displayed
    private static StringBuilder output = new StringBuilder();
    private static String lastCommand;

    private static Map<CommandResult, String> parseSoundOptions(JSONObject settings) throws JSONException
    {
        Map<CommandResult, String> sounds = new EnumMap<>(CommandResult.class);
        JSONObject json = settings.getJSONObject("playSoundOnResult");

        for (CommandResult result : CommandResult.values())
        {
            String resultId = result.name();
            if (json.has(resultId))
            {
                String soundId = json.getString(resultId);
                if (soundId != null && !soundId.isEmpty())
                {
                    sounds.put(result, soundId);
                }
            }
        }

        return sounds;
    }

    /**
     * Forces the console to reload its settings from the settings file.
     *
     * @throws IOException   if the JSON file at
     *                       {@link CommonStrings#PATH_SETTINGS} does not exist
     *                       or can't be opened.
     * @throws JSONException if the JSON is malformed or missing entries.
     * @since 2.0
     */
    public static void reloadSettings() throws IOException, JSONException
    {
        final JSONObject settingsFile = Global.getSettings().loadJSON(CommonStrings.PATH_SETTINGS);
        settings = new ConsoleSettings(settingsFile.getInt("consoleKey"),
                settingsFile.getBoolean("requireShift"),
                settingsFile.getBoolean("requireControl"),
                settingsFile.getBoolean("requireAlt"),
                Pattern.quote(settingsFile.getString("commandSeparator")),
                settingsFile.getBoolean("showEnteredCommands"),
                settingsFile.getBoolean("showCursorIndex"),
                settingsFile.getBoolean("showExceptionDetails"),
                settingsFile.getDouble("typoCorrectionThreshold"),
                JSONUtils.toColor(settingsFile.getJSONArray("outputColor")),
                settingsFile.getInt("maxOutputLineLength"),
                settingsFile.getString("consoleFont"),
                parseSoundOptions(settingsFile));

        // Set command persistence between battles
        //PersistentCommandManager.setCommandPersistence(
        //        settingsFile.getBoolean("persistentCombatCommands"));
        // What level to log console output at
        final Level logLevel = Level.toLevel(settingsFile.getString("consoleLogLevel"), Level.WARN);
        Global.getLogger(Console.class).setLevel(logLevel);
        Global.getLogger(CommandStore.class).setLevel(logLevel);

        // Console combat pop-up appearance settings (temporary)
        // TODO: Remove these settings once universal overlay is implemented
        Color color = JSONUtils.toColor(settingsFile.getJSONArray("backgroundColor"));
        UIManager.put("Panel.background", color);
        UIManager.put("OptionPane.background", color);
        UIManager.put("TextArea.background", color);
        UIManager.put("TextField.background", color);
        UIManager.put("Button.background", color);
        UIManager.put("SplitPane.background", color);

        color = JSONUtils.toColor(settingsFile.getJSONArray("foregroundColor"));
        UIManager.put("OptionPane.messageForeground", color);

        color = JSONUtils.toColor(settingsFile.getJSONArray("textColor"));
        UIManager.put("TextArea.foreground", color);
        UIManager.put("TextField.foreground", color);
        UIManager.put("TextField.caretForeground", color);

        color = JSONUtils.toColor(settingsFile.getJSONArray("buttonColor"));
        UIManager.put("Button.foreground", color);
        UIManager.put("SplitPane.foreground", color);
    }

    public static ConsoleSettings getSettings()
    {
        return settings;
    }

    static String getLastCommand()
    {
        return lastCommand;
    }

    //<editor-fold defaultstate="collapsed" desc="showMessage variants">
    /**
     * Displays a message to the user. The message will be formatted and shown
     * to the player when they reach a section of the game where it can be
     * displayed properly (combat/campaign map).
     * <p>
     * @param message  The message to show.
     * @param logLevel If this is equal to/higher than the "consoleLogLevel"
     *                 setting, this message will be logged in Starsector.log.
     * <p>
     * @since 2.0
     */
    public static void showMessage(String message, Level logLevel)
    {
        // Also add to Starsector's log
        Log.log(logLevel, message);

        // Word-wrap message and add it to the output queue
        message = StringUtils.wrapString(message, settings.getMaxOutputLineLength());
        output.append(message);
        if (!message.endsWith("\n"))
        {
            output.append('\n');
        }
    }

    /**
     * Displays a message to the user. The message will be formatted and shown
     * to the player when they reach a section of the game where it can be
     * displayed properly (combat/campaign map).
     * <p>
     * @param message The message to show.
     * <p>
     * @since 2.0
     */
    public static void showMessage(String message)
    {
        showMessage(message, Level.INFO);
    }

    /**
     * Displays the stack trace of a {@link Throwable}.
     * <p>
     * @param message An optional message to show before the stack trace. Can be
     *                {@code null}.
     * @param ex      The {@link Throwable} whose stack trace will be shown.
     * <p>
     * @since 2.0
     */
    public static void showException(String message, Throwable ex)
    {
        final StringBuilder stackTrace = new StringBuilder(256);

        // Add message if one was entered
        if (message != null)
        {
            stackTrace.append(message);
            if (!message.endsWith("\n"))
            {
                stackTrace.append("\n");
            }
        }

        // Add stack trace of Throwable, with a few extra details
        stackTrace.append(ex.toString()).append("\n");
        if (settings.getShouldShowExceptionStackTraces())
        {
            final ClassLoader cl = Global.getSettings().getScriptClassLoader();
            for (StackTraceElement ste : ex.getStackTrace())
            {
                String classSource;
                try
                {
                    final Class srcClass = Class.forName(ste.getClassName(), false, cl);
                    final CodeSource cs = srcClass.getProtectionDomain().getCodeSource();
                    if (cs == null || cs.getLocation() == null)
                    {
                        // TODO: Determine whether class is core or Janino-compiled
                        classSource = "core java class or loose script";
                    }
                    else
                    {
                        classSource = cs.getLocation().getFile().replace("\\", "/");
                        if (classSource.endsWith(".jar"))
                        {
                            classSource = classSource.substring(classSource.lastIndexOf('/') + 1);
                        }
                    }
                }
                catch (ClassNotFoundException ex1)
                {
                    classSource = "unknown class";
                }

                stackTrace.append("   at ").append(ste.toString())
                        .append("\n      [").append(classSource).append("]\n");
            }
        }
        else
        {
            Log.error("Console ran into exception: ", ex);
        }

        showMessage(stackTrace.toString(), Level.ERROR);
    }

    public static void showDialogOnClose(InteractionDialogPlugin dialog,
            SectorEntityToken token)
    {
        Global.getSector().addTransientScript(new ShowDialogOnCloseScript(dialog, token));
    }

    public static void showDialogOnClose(SectorEntityToken token)
    {
        Global.getSector().addTransientScript(new ShowDialogOnCloseScript(null, token));
    }
    //</editor-fold>

    private static CommandResult runCommand(String input, CommandContext context)
    {
        String[] tmp = input.split(" ", 2);
        String com = tmp[0].toLowerCase();
        String args = (tmp.length > 1 ? tmp[1] : "");
        CommandResult result;

        // Alias with arguments support
        if (CommandStore.getAliases().containsKey(com))
        {
            String rawAlias = CommandStore.getAliases().get(com);
            tmp = rawAlias.split(" ", 2);
            com = tmp[0];
            if (tmp.length > 1)
            {
                args = tmp[1] + " " + args;
            }
        }

        try
        {
            StoredCommand stored = CommandStore.retrieveCommand(com);
            if (stored == null)
            {
                String bestMatch = CommandUtils.findBestStringMatch(com,
                        CommandStore.getLoadedCommands());
                if (bestMatch != null)
                {
                    showMessage("No such command \"" + com + "\" registered,"
                            + " did you mean \"" + bestMatch + "\"?");
                    return CommandResult.ERROR;
                }

                showMessage("No such command \"" + com + "\" registered!", Level.ERROR);
                return CommandResult.ERROR;
            }

            if (settings.getShouldShowEnteredCommands())
            {
                showMessage("Running command \"" + input + "\"");
            }

            BaseCommand command = stored.getCommandClass().newInstance();
            result = command.runCommand(args, context);

            if (result == CommandResult.BAD_SYNTAX
                    && !stored.getSyntax().isEmpty())
            {
                showMessage("Syntax: " + stored.getSyntax());
            }

        }
        catch (Exception ex)
        {
            showException("Failed to execute command \"" + input
                    + "\" in context " + context, ex);
            return CommandResult.ERROR;
        }

        return result;
    }

    static void parseInput(String rawInput, CommandContext context)
    {
        if (rawInput == null)
        {
            return;
        }

        lastCommand = rawInput;

        // Runcode ignores separators
        // Hopefully the ONLY hardcoded command support I'll add to this mod...
        CommandResult worstResult;
        if (rawInput.length() >= 7 && rawInput.substring(0, 7).equalsIgnoreCase("runcode"))
        {
            worstResult = runCommand(rawInput, context);
        }
        else
        {
            // Split the raw input up into the individual commands
            // The command separator is used to separate multiple commands
            Set<CommandResult> results = new HashSet<>();
            worstResult = CommandResult.SUCCESS;
            Map<String, String> aliases = CommandStore.getAliases();
            for (String input : rawInput.split(settings.getCommandSeparator()))
            {
                input = input.trim();
                if (!input.isEmpty())
                {
                    // Whole-line alias support
                    if (aliases.containsKey(input.toLowerCase()))
                    {
                        for (String input2 : aliases.get(input.toLowerCase())
                                .split(settings.getCommandSeparator()))
                        {
                            input2 = input2.trim();
                            if (!input2.isEmpty())
                            {
                                results.add(runCommand(input2, context));
                            }
                        }
                    }
                    // Regular commands
                    else
                    {
                        results.add(runCommand(input, context));
                    }
                }
            }

            // Find 'worst' result of executed commands
            for (CommandResult tmp : results)
            {
                if (tmp.ordinal() > worstResult.ordinal())
                {
                    worstResult = tmp;
                }
            }
        }

        // Play a sound based on worst error type
        String sound = settings.getSoundForResult(worstResult);
        if (sound != null)
        {
            Global.getSoundPlayer().playUISound(sound, 1f, 1f);
        }
    }

    private static void showOutput(ConsoleListener listener)
    {
        if (output.length() > 0 && listener.showOutput(output.toString()))
        {
            output = new StringBuilder();
        }
    }

    static void advance(float amount, ConsoleListener listener)
    {
        // Just check the output queue for now
        //PersistentCommandManager.advance(amount, listener);
        showOutput(listener);
    }

    private static class ShowDialogOnCloseScript implements EveryFrameScript
    {
        private final SectorEntityToken token;
        private final InteractionDialogPlugin dialog;
        private boolean isDone = false;

        private ShowDialogOnCloseScript(InteractionDialogPlugin dialog,
                SectorEntityToken token)
        {
            this.dialog = dialog;
            this.token = token;
        }

        @Override
        public boolean isDone()
        {
            return isDone;
        }

        @Override
        public boolean runWhilePaused()
        {
            return false;
        }

        @Override
        public void advance(float amount)
        {
            final CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (!isDone && !ui.isShowingDialog())
            {
                isDone = true;

                try
                {
                    if (dialog == null)
                    {
                        ui.showInteractionDialog(token);
                    }
                    else
                    {
                        ui.showInteractionDialog(dialog, token);
                    }
                }
                // Catching the exception won't actually help
                // The game is screwed at this point, honestly
                catch (Exception ex)
                {
                    Console.showException("Failed to open dialog "
                            + dialog.getClass().getCanonicalName(), ex);
                    Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
                }
            }
        }
    }

    private Console()
    {
    }
}
