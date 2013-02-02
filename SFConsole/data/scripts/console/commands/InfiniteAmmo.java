package data.scripts.console.commands;

import data.scripts.console.BaseCombatHook;
import data.scripts.console.BaseCommand;

public class InfiniteAmmo extends BaseCommand
{
    @Override
    protected String getHelp()
    {
        return "Toggles infinite ammunition for all ships on your side.";
    }

    @Override
    protected String getSyntax()
    {
        return "infiniteammo (no arguments)";
    }

    @Override
    protected boolean isUseableInCombat()
    {
        return true;
    }

    @Override
    public boolean runCommand(String args)
    {
        BaseCombatHook.toggleInfiniteAmmo();
        return true;
    }
}