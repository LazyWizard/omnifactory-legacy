package org.lazywizard.omnifac.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.apache.log4j.Logger;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.omnifac.OmniFacSettings;

/**
 *
 * @author LazyWizard
 */
public class OmnifacBalance implements BaseCommand
{
    private static final Logger Log = Logger.getLogger(OmnifacBalance.class);

    // ===== NEW =====
    private static int getDaysToCreateNew(FleetMemberAPI member, float modifier)
    {
        int fp = member.getFleetPointCost();
        int size = member.getHullSpec().getHullSize().ordinal();

        return Math.round(Math.max(1f, Math.max((fp * size) / 2f, size * 2f) * modifier));
    }

    private static int getDaysToAnalyzeNew(FleetMemberAPI member, float modifier)
    {
        return Math.round(Math.max(1f, getDaysToCreateNew(member, 1f) * modifier));
    }

    private static int getDaysToCreateNew(WeaponSpecAPI weapon, float modifier)
    {
        return Math.round(Math.max(1f, getDaysToCreateOld(weapon, modifier) * getTierModifier(weapon)));
    }

    private static int getDaysToAnalyzeNew(WeaponSpecAPI weapon, float modifier)
    {
        return Math.round(Math.max(1f, getDaysToCreateNew(weapon, 1f) * modifier));
    }

    // ===== OLD =====
    //<editor-fold defaultstate="collapsed" desc="Old methods">
    private static int getDaysToCreateOld(FleetMemberAPI member, float modifier)
    {
        int fp = member.getFleetPointCost();
        int size = member.getHullSpec().getHullSize().ordinal();

        return (int) Math.max(size * 3f, Math.max((fp * size) / 2f, size * 3f) * modifier);
    }

    private static int getDaysToAnalyzeOld(FleetMemberAPI member, float modifier)
    {
        return (int) Math.max(1f, getDaysToCreateOld(member, 1f) * modifier);
    }

    private static int getDaysToCreateOld(WeaponSpecAPI weapon, float modifier)
    {
        return (int) Math.max(Math.max(getCargoSpace(weapon), 1f) * modifier, 1f);
    }

    private static int getDaysToAnalyzeOld(WeaponSpecAPI weapon, float modifier)
    {
        return (int) Math.max(1f, getDaysToCreateOld(weapon, 1f) * modifier);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Misc methods">
    private static float getCargoSpace(WeaponSpecAPI weapon)
    {
        switch (weapon.getSize())
        {
            case SMALL:
                return 2f;
            case MEDIUM:
                return 4f;
            case LARGE:
                return 8f;
            default:
                return 0f;
        }
    }

    private static float getTierModifier(WeaponSpecAPI weapon)
    {
        switch (weapon.getTier())
        {
            case 0:
                return 0.8f;
            case 1:
                return 1f;
            case 2:
                return 1.2f;
            case 3:
                return 1.5f;
            default:
                return 1f;
        }
    }

    private static List<FleetMemberAPI> generateFleetMembers()
    {
        final List<FleetMemberAPI> members = new ArrayList<>();
        for (String hullId : Global.getSector().getAllEmptyVariantIds())
        {
            FleetMemberAPI member = Global.getFactory().createFleetMember(
                    FleetMemberType.SHIP, hullId);
            if (member.getHullSpec().getHullSize() != HullSize.FIGHTER)
            {
                members.add(member);
            }
        }
        for (String wingId : Global.getSector().getAllFighterWingIds())
        {
            members.add(Global.getFactory().createFleetMember(
                    FleetMemberType.FIGHTER_WING, wingId));
        }

        Collections.sort(members, new FleetMemberComparator());
        return members;
    }

    private static List<WeaponSpecAPI> generateWeapons()
    {
        final List<WeaponSpecAPI> weapons = new ArrayList<>();
        for (String weaponId : Global.getSector().getAllWeaponIds())
        {
            weapons.add(Global.getSettings().getWeaponSpec(weaponId));
        }

        Collections.sort(weapons, new WeaponComparator());
        return weapons;
    }
    //</editor-fold>

    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (!context.isInCampaign())
        {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        // Generate ship production balance report
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("                             /=============================\\\n");
        sb.append("             HULLS           |      OLD     |      NEW     |\n");
        sb.append("/============================|==============|==============|\n");
        sb.append("|             ID             | First | Next | First | Next |\n");
        sb.append("|============================|==============|==============|\n");
        HullSize lastHullSize = null;
        for (FleetMemberAPI member : generateFleetMembers())
        {
            final HullSize size = member.getHullSpec().getHullSize();
            if (lastHullSize == null)
            {
                lastHullSize = size;
            }
            if (lastHullSize != size)
            {
                sb.append("|----------------------------|-------|------|-------|------|\n");
                lastHullSize = size;
            }

            final int daysToAnalyzeOld = getDaysToAnalyzeOld(member, 0.5f),
                    daysToCreateOld = getDaysToCreateOld(member, 1f),
                    daysToAnalyzeNew = getDaysToAnalyzeNew(member,
                            OmniFacSettings.getShipAnalysisTimeMod()),
                    daysToCreateNew = getDaysToCreateNew(member,
                            OmniFacSettings.getShipProductionTimeMod());
            sb.append(String.format("| %-26.26s | %5d | %4d | %5d | %4d |\n",
                    member.getHullSpec().getHullName(),
                    daysToAnalyzeOld + daysToCreateOld, daysToCreateOld,
                    daysToAnalyzeNew + daysToCreateNew, daysToCreateNew));
        }
        sb.append("\\==========================================================/\n\n");

        // Generate weapon production balance report
        sb.append("                             /=============================\\\n");
        sb.append("            WEAPONS          |      OLD     |      NEW     |\n");
        sb.append("/============================|==============|==============|\n");
        sb.append("|             ID             | First | Next | First | Next |\n");
        sb.append("|============================|==============|==============|\n");
        WeaponSize lastWeaponSize = null;
        for (WeaponSpecAPI weapon : generateWeapons())
        {
            final WeaponSize size = weapon.getSize();
            if (lastWeaponSize == null)
            {
                lastWeaponSize = size;
            }
            if (lastWeaponSize != size)
            {
                sb.append("|----------------------------|-------|------|-------|------|\n");
                lastWeaponSize = size;
            }

            final int daysToAnalyzeOld = getDaysToAnalyzeOld(weapon, 0.5f),
                    daysToCreateOld = getDaysToCreateOld(weapon, 1f),
                    daysToAnalyzeNew = getDaysToAnalyzeNew(weapon,
                            OmniFacSettings.getWeaponAnalysisTimeMod()),
                    daysToCreateNew = getDaysToCreateNew(weapon,
                            OmniFacSettings.getWeaponProductionTimeMod());
            sb.append(String.format("| %-26.26s | %5d | %4d | %5d | %4d |\n",
                    weapon.getWeaponName(),
                    daysToAnalyzeOld + daysToCreateOld, daysToCreateOld,
                    daysToAnalyzeNew + daysToCreateNew, daysToCreateNew));
        }
        sb.append("\\==========================================================/\n\n");

        // Log report (game text isn't monospaced so we can't print formatted text)
        Log.info("Balance report:\n\n" + sb.toString());
        Console.showMessage("Saved balance report to starsector.log");
        return CommandResult.SUCCESS;
    }

    private static class WeaponComparator implements Comparator<WeaponSpecAPI>
    {
        @Override
        public int compare(WeaponSpecAPI o1, WeaponSpecAPI o2)
        {
            if (o1.getSize() == o2.getSize())
            {
                return o1.getWeaponName().compareTo(o2.getWeaponName());
            }

            return o1.getSize().compareTo(o2.getSize());
        }
    }

    private static class FleetMemberComparator implements Comparator<FleetMemberAPI>
    {
        @Override
        public int compare(FleetMemberAPI o1, FleetMemberAPI o2)
        {
            ShipHullSpecAPI h1 = o1.getHullSpec(), h2 = o2.getHullSpec();
            if (h1.getHullSize() == h2.getHullSize())
            {
                return h1.getHullName().compareTo(h2.getHullName());
            }

            return h1.getHullSize().compareTo(h2.getHullSize());
        }
    }
}
