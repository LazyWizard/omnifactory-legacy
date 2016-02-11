package org.lazywizard.omnifac;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OmniFacSettings
{
    private static Set<String> restrictedWeapons;
    private static Set<String> restrictedShips;
    private static boolean randomLocation;
    private static boolean showAddedCargo;
    private static boolean showAnalysisComplete;
    private static boolean showLimitReached;
    private static boolean allowRestrictedGoods;
    private static boolean removeBrokenGoods;
    private static float shipAnalysisTimeMod;
    private static float weaponAnalysisTimeMod;
    private static float shipProductionTimeMod;
    private static float weaponProductionTimeMod;
    private static int requiredCrew;
    private static float requiredSuppliesPerDay;
    private static float requiredFuelPerDay;
    private static int maxHullsPerFighter;
    private static int maxHullsPerFrigate;
    private static int maxHullsPerDestroyer;
    private static int maxHullsPerCruiser;
    private static int maxHullsPerCapital;
    private static float maxStacksPerWeapon;
    private static int numberOfFactories;
    private static float omnifactoryTariff;

    public static void reloadSettings() throws JSONException, IOException
    {
        // Base Omnifactory settings
        JSONObject settings = Global.getSettings().loadJSON(Constants.SETTINGS_FILE);
        randomLocation = settings.getBoolean("randomStartingLocation");
        showAddedCargo = settings.getBoolean("showAddedCargo");
        showAnalysisComplete = settings.getBoolean("showAnalysisComplete");
        showLimitReached = settings.getBoolean("showLimitReached");
        allowRestrictedGoods = settings.getBoolean("ignoreGoodRestrictions");
        removeBrokenGoods = settings.getBoolean("removeBrokenGoods");
        shipAnalysisTimeMod = (float) settings.getDouble("shipAnalysisTimeMod");
        weaponAnalysisTimeMod = (float) settings.getDouble("weaponAnalysisTimeMod");
        shipProductionTimeMod = (float) settings.getDouble("shipProductionTimeMod");
        weaponProductionTimeMod = (float) settings.getDouble("weaponProductionTimeMod");
        requiredCrew = settings.getInt("requiredCrewToFunction");
        requiredSuppliesPerDay = (float) settings.getDouble("requiredSuppliesPerDay");
        requiredFuelPerDay = (float) settings.getDouble("requiredFuelPerDay");
        maxHullsPerFighter = settings.getInt("maxHullsPerFighter");
        maxHullsPerFrigate = settings.getInt("maxHullsPerFrigate");
        maxHullsPerDestroyer = settings.getInt("maxHullsPerDestroyer");
        maxHullsPerCruiser = settings.getInt("maxHullsPerCruiser");
        maxHullsPerCapital = settings.getInt("maxHullsPerCapital");
        maxStacksPerWeapon = (float) settings.getDouble("maxStacksPerWeapon");
        numberOfFactories = settings.getInt("numberOfFactories");
        omnifactoryTariff = (float) settings.getDouble("omnifactoryTariff");

        // Restricted goods
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("weapon id",
                Constants.RESTRICTED_WEAPONS_CSV, Constants.MOD_ID);
        restrictedWeapons = new HashSet<>();
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedWeapons.add(row.getString("weapon id"));
        }

        // Restricted ships
        csv = Global.getSettings().getMergedSpreadsheetDataForMod("hull id",
                Constants.RESTRICTED_SHIPS_CSV, Constants.MOD_ID);
        restrictedShips = new HashSet<>();
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedShips.add(row.getString("hull id"));
        }
    }

    public static Set<String> getRestrictedWeapons()
    {
        if (allowRestrictedGoods)
        {
            return Collections.<String>emptySet();
        }

        return restrictedWeapons;
    }

    public static Set<String> getRestrictedShips()
    {
        if (allowRestrictedGoods)
        {
            return Collections.<String>emptySet();
        }

        return restrictedShips;
    }

    static boolean shouldHaveRandomStartingLocation()
    {
        return randomLocation;
    }

    public static boolean shouldShowAddedCargo()
    {
        return showAddedCargo;
    }

    public static boolean shouldShowAnalysisComplete()
    {
        return showAnalysisComplete;
    }

    public static boolean shouldShowLimitReached()
    {
        return showLimitReached;
    }

    public static boolean shouldRemoveBrokenGoods()
    {
        return removeBrokenGoods;
    }

    public static float getShipAnalysisTimeMod()
    {
        return shipAnalysisTimeMod;
    }

    public static float getWeaponAnalysisTimeMod()
    {
        return weaponAnalysisTimeMod;
    }

    public static float getShipProductionTimeMod()
    {
        return shipProductionTimeMod;
    }

    public static float getWeaponProductionTimeMod()
    {
        return weaponProductionTimeMod;
    }

    public static int getRequiredCrew()
    {
        return requiredCrew;
    }

    public static float getRequiredSuppliesPerDay()
    {
        return requiredSuppliesPerDay;
    }

    public static float getRequiredFuelPerDay()
    {
        return requiredFuelPerDay;
    }

    public static int getMaxHullsPerFighter()
    {
        return maxHullsPerFighter;
    }

    public static int getMaxHullsPerFrigate()
    {
        return maxHullsPerFrigate;
    }

    public static int getMaxHullsPerDestroyer()
    {
        return maxHullsPerDestroyer;
    }

    public static int getMaxHullsPerCruiser()
    {
        return maxHullsPerCruiser;
    }

    public static int getMaxHullsPerCapital()
    {
        return maxHullsPerCapital;
    }

    public static float getMaxStacksPerWeapon()
    {
        return maxStacksPerWeapon;
    }

    public static int getNumberOfFactories()
    {
        return (randomLocation ? numberOfFactories : 1);
    }

    public static float getOmnifactoryTariff()
    {
        return omnifactoryTariff;
    }

    private OmniFacSettings()
    {
    }
}
