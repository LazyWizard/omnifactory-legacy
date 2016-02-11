package org.lazywizard.omnifac.commands;

import java.util.Collections;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.StringUtils;
import org.lazywizard.omnifac.OmniFac;

public class OmnifacStatus implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        if (context != CommandContext.CAMPAIGN_MAP)
        {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        final List<OmniFac> factories = OmniFac.getAllFactories();
        if (factories.isEmpty())
        {
            Console.showMessage("There are no active Omnifactories in this save!");
            return CommandResult.SUCCESS;
        }

        final boolean showDetailed = "detailed".equals(args);
        final int lineLength = Console.getSettings().getMaxOutputLineLength() - 3;
        final StringBuilder output = new StringBuilder("Active Omnifactories ("
                + factories.size() + "):\n");
        for (OmniFac fac : factories)
        {
            // TODO: Format this to look good
            if (showDetailed)
            {
                output.append(" - " + fac);
                List<String> tmp = fac.getKnownShips();
                Collections.sort(tmp);
                output.append(StringUtils.indent(StringUtils.wrapString("\nKnown ships: "
                        + CollectionUtils.implode(tmp), lineLength), "  "));
                tmp = fac.getKnownWings();
                Collections.sort(tmp);
                output.append(StringUtils.indent(StringUtils.wrapString("\nKnown wings: "
                        + CollectionUtils.implode(tmp), lineLength), "  "));
                tmp = fac.getKnownWeapons();
                Collections.sort(tmp);
                output.append(StringUtils.indent(StringUtils.wrapString("\nKnown weapons: "
                        + CollectionUtils.implode(tmp), lineLength), "  "));
            }
            else
            {
                output.append(" - " + fac + "\n");
            }
        }

        if (!showDetailed)
        {
            output.append("\nUse \"" + OmnifacStatus.class.getSimpleName()
                    + " detailed\" to show more details.");
        }

        Console.showMessage(output.toString());
        return CommandResult.SUCCESS;
    }
}
