package org.lazywizard.console.commands;

import java.lang.ref.WeakReference;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class Reveal implements BaseCommand
{
    private static WeakReference<RevealPlugin> plugin;

    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (context.isInCampaign())
        {
            final String bonusId = CommonStrings.MOD_ID + "_reveal";
            final MutableFleetStatsAPI stats = Global.getSector().getPlayerFleet().getStats();
            final StatBonus rangeMod = stats.getSensorRangeMod();
            final StatBonus strengthMod = stats.getSensorStrengthMod();
            if (rangeMod.getFlatBonus(bonusId) != null)
            {
                rangeMod.unmodify(bonusId);
                strengthMod.unmodify(bonusId);
                Console.showMessage("Sensors returned to normal.");
                return CommandResult.SUCCESS;
            }

            rangeMod.modifyFlat(bonusId, 999_999f, "Console");
            strengthMod.modifyFlat(bonusId, 999_999f, "Console");
            Console.showMessage("Sensor strength maximized.");
            return CommandResult.SUCCESS;
        }

        RevealPlugin tmp;
        if (plugin == null || plugin.get() == null
                || plugin.get().engine != Global.getCombatEngine())
        {
            tmp = new RevealPlugin();
            plugin = new WeakReference<>(tmp);
            Global.getCombatEngine().addPlugin(tmp);
            Console.showMessage("Fog of war disabled.");
        }
        else
        {
            tmp = plugin.get();
            plugin.clear();
            tmp.active = false;
            Console.showMessage("Fog of war enabled.");
        }

        return CommandResult.SUCCESS;
    }

    private static class RevealPlugin extends BaseEveryFrameCombatPlugin
    {
        private boolean active = true;
        private CombatEngineAPI engine;

        @Override
        public void advance(float amount, List<InputEventAPI> events)
        {
            if (!active)
            {
                engine.removePlugin(this);
                return;
            }

            FogOfWarAPI fow = engine.getFogOfWar(FleetSide.PLAYER.ordinal());
            float radius = Math.max(engine.getMapWidth(), engine.getMapHeight());
            fow.revealAroundPoint(this, 0f, 0f, radius);
        }

        @Override
        public void init(CombatEngineAPI engine)
        {
            this.engine = engine;
        }
    }
}