package org.lazywizard.omnifac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SubmarketPlugin.TransferAction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.campaign.CargoUtils;
import org.lazywizard.lazylib.campaign.MessageUtils;

public class OmniFac extends StoragePlugin
{
    private final Map<String, ShipData> shipData = new HashMap<>();
    private final Map<String, WeaponData> wepData = new HashMap<>();
    private SectorEntityToken station;
    private long lastHeartbeat;
    private int numHeartbeats = 0;
    private boolean warnedRequirements = true;

    @Override
    public void init(SubmarketAPI submarket)
    {
        super.init(submarket);
        super.setPlayerPaidToUnlock(true);

        // Will be properly set by initOmnifactory(), but here as a fallback
        this.station = submarket.getMarket().getPrimaryEntity();

        // Synchronize factory heartbeat to the start of the next day
        final CampaignClockAPI clock = Global.getSector().getClock();
        lastHeartbeat = new GregorianCalendar(clock.getCycle(),
                clock.getMonth() - 1, clock.getDay()).getTimeInMillis();
    }

    //<editor-fold desc="Static methods">
    public static void initOmnifactory(SectorEntityToken factory)
    {
        // Only one controller script per factory
        if (isFactory(factory))
        {
            throw new RuntimeException(factory.getFullName()
                    + " is already an Omnifactory!");
        }

        // Set up market data for the Omnifactory
        MarketAPI market = factory.getMarket();

        if (!market.hasSubmarket(Submarkets.SUBMARKET_STORAGE))
        {
            market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        }

        ((StoragePlugin) market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
                .getPlugin()).setPlayerPaidToUnlock(true);
        market.addSubmarket(Constants.SUBMARKET_ID);
        getFactory(factory).station = factory;
    }

    public static boolean isFactory(SectorEntityToken station)
    {
        return (station.getMarket() != null
                && station.getMarket().hasSubmarket(Constants.SUBMARKET_ID));
    }

    public static OmniFac getFactory(SectorEntityToken station)
    {
        return (OmniFac) station.getMarket().getSubmarket(Constants.SUBMARKET_ID).getPlugin();
    }

    public static SubmarketAPI getFactoryMarket(SectorEntityToken station)
    {
        return station.getMarket().getSubmarket(Constants.SUBMARKET_ID);
    }

    public static List<OmniFac> getAllFactories()
    {
        List<OmniFac> factories = new ArrayList<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            SectorEntityToken token = market.getPrimaryEntity();
            if (isFactory(token))
            {
                factories.add(getFactory(token));
            }
        }

        return factories;
    }
    //</editor-fold>

    //<editor-fold desc="Omnifactory local data">
    private static String parseHullName(FleetMemberAPI ship)
    {
        return (ship.isFighterWing() ? ship.getSpecId() : ship.getHullId());
    }

    public boolean isUnknownShip(FleetMemberAPI ship)
    {
        return !shipData.containsKey(parseHullName(ship));
    }

    public boolean isUnknownWeapon(CargoStackAPI stack)
    {
        // We only deal with weapons, not resources
        return (stack.isWeaponStack() && !wepData.containsKey(stack.getData()));
    }

    public boolean isRestrictedShip(FleetMemberAPI ship)
    {
        return OmniFacSettings.getRestrictedShips().contains(parseHullName(ship));
    }

    public boolean isRestrictedWeapon(CargoStackAPI stack)
    {
        return OmniFacSettings.getRestrictedWeapons().contains(stack.getData());
    }

    public List<String> getKnownShips()
    {
        List<String> knownShips = new ArrayList<>(shipData.size());

        for (ShipData data : shipData.values())
        {
            if (data.size > 1)
            {
                knownShips.add(data.id);
            }
        }

        return knownShips;
    }

    public List<String> getKnownWings()
    {
        List<String> knownWings = new ArrayList<>(shipData.size());

        for (ShipData data : shipData.values())
        {
            if (data.size == 1)
            {
                knownWings.add(data.id);
            }
        }

        return knownWings;
    }

    public List<String> getKnownWeapons()
    {
        return new ArrayList<>(wepData.keySet());
    }

    public BlueprintData getShipBlueprint(String hullOrWingId)
    {
        return shipData.get(hullOrWingId);
    }

    public BlueprintData getWeaponBlueprint(String weaponId)
    {
        return wepData.get(weaponId);
    }

    public SubmarketAPI getStorageMarket()
    {
        if (!market.hasSubmarket(Submarkets.SUBMARKET_STORAGE))
        {
            market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
            ((StoragePlugin) market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
                    .getPlugin()).setPlayerPaidToUnlock(true);
            market.addSubmarket(Constants.SUBMARKET_ID);
        }

        return market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
    }

    public CargoAPI getFactoryCargo()
    {
        return getCargo();
    }

    public CargoAPI getStorageCargo()
    {
        return getStorageMarket().getCargo();
    }

    public SectorEntityToken getStation()
    {
        return station;
    }

    public String getLocationString()
    {
        OrbitAPI orbit = station.getOrbit();
        LocationAPI loc = station.getContainingLocation();
        if (orbit == null || orbit.getFocus() == null)
        {
            return "orbiting nothing in " + (loc == null ? " nowhere" : loc.getName());
        }

        return "orbiting " + orbit.getFocus().getName() + " in " + loc.getName();
    }

    @Override
    public String toString()
    {
        return station.getName() + " " + getLocationString()
                + " (" + shipData.size() + " ships, " + wepData.size()
                + " weapons known)";
    }
    //</editor-fold>

    //<editor-fold desc="Heartbeat">
    private void heartbeat()
    {
        boolean metRequirements = true;
        CargoAPI cargo = getCargo();

        if (cargo.getTotalCrew() < OmniFacSettings.getRequiredCrew())
        {
            if (!warnedRequirements)
            {
                Global.getSector().getCampaignUI().addMessage("The " + station.getName()
                        + " needs " + (OmniFacSettings.getRequiredCrew() - cargo.getTotalCrew())
                        + " more crew to function.");
            }

            metRequirements = false;
        }

        if (cargo.getFuel() < OmniFacSettings.getRequiredFuelPerDay())
        {
            if (!warnedRequirements)
            {
                Global.getSector().getCampaignUI().addMessage("The " + station.getName()
                        + " is out of fuel. It requires " + OmniFacSettings.getRequiredFuelPerDay()
                        + " per day to function.");
            }

            metRequirements = false;
        }

        if (cargo.getSupplies() < OmniFacSettings.getRequiredSuppliesPerDay())
        {
            if (!warnedRequirements)
            {
                Global.getSector().getCampaignUI().addMessage("The " + station.getName()
                        + " is out of supplies. It requires " + OmniFacSettings.getRequiredSuppliesPerDay()
                        + " per day to function.");
            }

            metRequirements = false;
        }

        if (!metRequirements)
        {
            warnedRequirements = true;
            return;
        }

        warnedRequirements = false;
        cargo.removeSupplies(OmniFacSettings.getRequiredSuppliesPerDay());
        cargo.removeFuel(OmniFacSettings.getRequiredFuelPerDay());
        numHeartbeats++;

        List<String> addedShips = new ArrayList<>();
        List<String> addedWeps = new ArrayList<>();
        List<String> analyzedShips = new ArrayList<>();
        List<String> analyzedWeps = new ArrayList<>();
        List<String> hitLimit = new ArrayList<>();

        for (BlueprintData tmp : shipData.values())
        {
            if (!tmp.isAnalyzed())
            {
                if (numHeartbeats - tmp.getDaysToAnalyze() >= tmp.getLastUpdate())
                {
                    tmp.setAnalyzed(true);

                    if (OmniFacSettings.shouldShowAnalysisComplete())
                    {
                        analyzedShips.add(tmp.getDisplayName() + " ("
                                + tmp.getDaysToCreate() + "d)");
                    }
                }
            }
            else
            {
                if (numHeartbeats - tmp.getDaysToCreate() >= tmp.getLastUpdate())
                {
                    try
                    {
                        if (tmp.create())
                        {
                            if (OmniFacSettings.shouldShowAddedCargo())
                            {
                                addedShips.add(tmp.getDisplayName() + " (" + tmp.getTotal()
                                        + "/" + tmp.getLimit() + ")");
                            }
                        }
                        else if (OmniFacSettings.shouldShowLimitReached() && !tmp.hasWarnedLimit())
                        {
                            hitLimit.add(tmp.getDisplayName());
                            tmp.setWarnedLimit(true);
                        }
                    }
                    catch (RuntimeException ex)
                    {
                        Global.getSector().getCampaignUI().addMessage(
                                "Failed to create ship '" + tmp.getDisplayName() + "' ("
                                + tmp.getId() + ")! Was a required mod disabled?");

                        if (OmniFacSettings.shouldRemoveBrokenGoods())
                        {
                            Global.getSector().getCampaignUI().addMessage(
                                    "Removed ship '" + tmp.getDisplayName() + "' from "
                                    + station.getName() + "'s memory banks.");
                            shipData.remove(tmp.getId());
                        }
                    }
                }
            }
        }

        for (BlueprintData tmp : wepData.values())
        {
            if (!tmp.isAnalyzed())
            {
                if (numHeartbeats - tmp.getDaysToAnalyze() >= tmp.getLastUpdate())
                {
                    tmp.setAnalyzed(true);

                    if (OmniFacSettings.shouldShowAnalysisComplete())
                    {
                        analyzedWeps.add(tmp.getDisplayName() + " ("
                                + tmp.getDaysToCreate() + "d)");
                    }
                }
            }
            else
            {
                if (numHeartbeats - tmp.getDaysToCreate() >= tmp.getLastUpdate())
                {
                    try
                    {
                        if (tmp.create())
                        {
                            if (OmniFacSettings.shouldShowAddedCargo())
                            {
                                addedWeps.add(tmp.getDisplayName() + " (" + tmp.getTotal()
                                        + "/" + tmp.getLimit() + ")");
                            }
                        }
                        else if (OmniFacSettings.shouldShowLimitReached() && !tmp.hasWarnedLimit())
                        {
                            hitLimit.add(tmp.getDisplayName());
                            tmp.setWarnedLimit(true);
                        }
                    }
                    catch (RuntimeException ex)
                    {
                        Global.getSector().getCampaignUI().addMessage(
                                "Failed to create weapon '" + tmp.getDisplayName() + "' ("
                                + tmp.getId() + ")! Was a required mod disabled?");

                        if (OmniFacSettings.shouldRemoveBrokenGoods())
                        {
                            Global.getSector().getCampaignUI().addMessage(
                                    "Removed weapon '" + tmp.getDisplayName() + "' from "
                                    + station.getName() + "'s memory banks.");
                            wepData.remove(tmp.getId());
                        }
                    }
                }
            }
        }

        if (OmniFacSettings.shouldShowAddedCargo())
        {
            if (!addedShips.isEmpty())
            {
                Collections.sort(addedShips);
                MessageUtils.showMessage("The " + station.getName()
                        + " has produced the following ships:",
                        CollectionUtils.implode(addedShips) + ".", true);
            }
            if (!addedWeps.isEmpty())
            {
                Collections.sort(addedWeps);
                MessageUtils.showMessage("The " + station.getName()
                        + " has produced the following weapons:",
                        CollectionUtils.implode(addedWeps) + ".", true);
            }
        }

        if (OmniFacSettings.shouldShowLimitReached() && !hitLimit.isEmpty())
        {
            Collections.sort(hitLimit);
            MessageUtils.showMessage("The " + station.getName()
                    + " has reached its limit for the following goods:",
                    CollectionUtils.implode(hitLimit) + ".", true);
        }

        if (OmniFacSettings.shouldShowAnalysisComplete())
        {
            if (!analyzedShips.isEmpty())
            {
                Collections.sort(analyzedShips);
                MessageUtils.showMessage("The " + station.getName()
                        + " has started production for the following ships:",
                        CollectionUtils.implode(analyzedShips) + ".", true);
            }
            if (!analyzedWeps.isEmpty())
            {
                Collections.sort(analyzedWeps);
                MessageUtils.showMessage("The " + station.getName()
                        + " has started production for the following weapons:",
                        CollectionUtils.implode(analyzedWeps) + ".", true);
            }
        }
    }

    public boolean checkCargo()
    {
        boolean newItem = false;
        final CargoAPI cargo = getCargo(),
                storage = getStorageCargo();
        final List<String> newShips = new ArrayList<>(), blockedShips = new ArrayList<>(),
                newWeps = new ArrayList<>(), blockedWeps = new ArrayList<>();

        for (FleetMemberAPI ship : cargo.getMothballedShips().getMembersListCopy())
        {
            if (isRestrictedShip(ship))
            {
                blockedShips.add(ship.getHullSpec().getHullName());
                cargo.getMothballedShips().removeFleetMember(ship);
                storage.getMothballedShips().addFleetMember(ship);
            }
            else if (isUnknownShip(ship))
            {
                newItem = true;
                String id = parseHullName(ship);
                ShipData tmp = new ShipData(ship);

                if (OmniFacSettings.getShipAnalysisTimeMod() == 0f)
                {
                    tmp.setAnalyzed(true);
                    newShips.add(tmp.getDisplayName() + " ("
                            + tmp.getDaysToCreate() + "d)");
                }
                else
                {
                    newShips.add(tmp.getDisplayName() + " ("
                            + tmp.getDaysToAnalyze() + "d)");
                }

                shipData.put(id, tmp);

                // Add all weapons on this ship to the station's cargo
                if (!ship.isFighterWing())
                {
                    for (String slot : ship.getVariant().getNonBuiltInWeaponSlots())
                    {
                        cargo.addWeapons(ship.getVariant().getWeaponId(slot), 1);
                    }
                }

                cargo.getMothballedShips().removeFleetMember(ship);
            }
        }

        for (CargoStackAPI stack : cargo.getStacksCopy())
        {
            if (isRestrictedWeapon(stack))
            {
                blockedWeps.add(stack.getDisplayName());
                CargoUtils.moveStack(stack, storage);
            }
            else if (isUnknownWeapon(stack))
            {
                newItem = true;
                WeaponData tmp = new WeaponData(stack);

                if (OmniFacSettings.getWeaponAnalysisTimeMod() == 0f)
                {
                    tmp.setAnalyzed(true);
                    newWeps.add(tmp.getDisplayName() + " ("
                            + tmp.getDaysToCreate() + "d)");
                }
                else
                {
                    newWeps.add(tmp.getDisplayName() + " ("
                            + tmp.getDaysToAnalyze() + "d)");
                }

                wepData.put((String) stack.getData(), tmp);
                cargo.removeWeapons((String) stack.getData(), 1);
            }
        }

        if (!newShips.isEmpty())
        {
            Collections.sort(newShips);
            if (OmniFacSettings.getShipAnalysisTimeMod() == 0f)
            {
                MessageUtils.showMessage("New ship blueprints added to the "
                        + station.getName() + ":",
                        CollectionUtils.implode(newShips) + ".", true);
            }
            else
            {
                MessageUtils.showMessage("The following ships are being"
                        + " disassembled and analyzed by the "
                        + station.getName() + ":",
                        CollectionUtils.implode(newShips) + ".", true);
            }
        }

        if (!newWeps.isEmpty())
        {
            Collections.sort(newWeps);
            if (OmniFacSettings.getWeaponAnalysisTimeMod() == 0f)
            {
                MessageUtils.showMessage("New weapon blueprints added to the "
                        + station.getName() + ":",
                        CollectionUtils.implode(newWeps) + ".", true);
            }
            else
            {
                MessageUtils.showMessage("The following weapons are being"
                        + " disassembled and analyzed by the "
                        + station.getName() + ":",
                        CollectionUtils.implode(newWeps) + ".", true);
            }
        }

        boolean movedItems = false;
        if (!blockedShips.isEmpty())
        {
            movedItems = true;
            Collections.sort(blockedShips);
            MessageUtils.showMessage("The " + station.getName()
                    + " is unable to replicate the following ships:",
                    CollectionUtils.implode(blockedShips) + ".", true);
        }

        if (!blockedWeps.isEmpty())
        {
            movedItems = true;
            Collections.sort(blockedWeps);
            MessageUtils.showMessage("The " + station.getName()
                    + " is unable to replicate the following weapons:",
                    CollectionUtils.implode(blockedWeps) + ".", true);
        }

        if (movedItems)
        {
            Global.getSector().getCampaignUI().addMessage("All non-replicable"
                    + " items have been moved to " + station.getName() + "'s storage.");
        }

        return newItem;
    }

    @Override
    public void advance(float amount)
    {
        CampaignClockAPI clock = Global.getSector().getClock();

        if (clock.getElapsedDaysSince(lastHeartbeat) >= 1f)
        {
            lastHeartbeat = clock.getTimestamp();
            heartbeat();

            if (checkCargo())
            {
                warnedRequirements = false;
            }
        }
    }
    //</editor-fold>

    //<editor-fold desc="Submarket details">
    @Override
    public String getName()
    {
        return Constants.STATION_NAME;
    }

    @Override
    public String getBuyVerb()
    {
        return (getTariff() > -1f ? "Buy" : "Take");
    }

    @Override
    public String getSellVerb()
    {
        return (getTariff() < 1f ? "Sell" : "Leave");
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action)
    {
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action)
    {
        // Can't sell restricted or known weapons to the Omnifactory
        if (action == TransferAction.PLAYER_SELL)
        {
            if (stack.isCrewStack())
            {
                return !(OmniFacSettings.getRequiredCrew() > 0);
            }
            else if (stack.isFuelStack())
            {
                return !(OmniFacSettings.getRequiredFuelPerDay() > 0f);
            }
            else if (stack.isSupplyStack())
            {
                return !(OmniFacSettings.getRequiredSuppliesPerDay() > 0f);
            }
            else
            {
                return (isRestrictedWeapon(stack) || !isUnknownWeapon(stack));
            }
        }

        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action)
    {
        // Can't sell restricted or known ships to the Omnifactory
        if (action == TransferAction.PLAYER_SELL)
        {
            return (isRestrictedShip(member) || !isUnknownShip(member));
        }

        return false;
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action)
    {
        if (!stack.isWeaponStack() || isRestrictedWeapon(stack))
        {
            return "Unable to replicate";
        }

        return "Blueprint already known";
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action)
    {
        return (isRestrictedShip(member) ? "Unable to replicate" : "Blueprint already known");
    }

    @Override
    public float getTariff()
    {
        return OmniFacSettings.getOmnifactoryTariff();
    }

    @Override
    public boolean isFreeTransfer()
    {
        return false;
    }

    @Override
    public boolean isParticipatesInEconomy()
    {
        return false;
    }

    @Override
    public void updateCargoPrePlayerInteraction()
    {
        // TODO: Modify demand
    }
    //</editor-fold>

    //<editor-fold desc="Internal data types">
    public static interface BlueprintData
    {
        public int getDaysToAnalyze();

        public int getDaysToCreate();

        int getLastUpdate();

        public String getDisplayName();

        public String getId();

        public int getTotal();

        public int getLimit();

        boolean hasWarnedLimit();

        void setWarnedLimit(boolean hasWarned);

        public boolean isAnalyzed();

        public void setAnalyzed(boolean isAnalyzed);

        boolean create();
    }

    private class ShipData implements BlueprintData
    {
        String id, displayName;
        FleetMemberType type;
        int fp, size, lastUpdate;
        boolean warnedLimit = false, isAnalyzed = false;

        ShipData(FleetMemberAPI ship)
        {
            id = parseHullName(ship);
            displayName = ship.getHullSpec().getHullName();
            type = ship.getType();
            fp = ship.getFleetPointCost();
            size = ship.getHullSpec().getHullSize().ordinal();
            lastUpdate = numHeartbeats;
        }

        @Override
        public int getDaysToAnalyze()
        {
            return (int) Math.max(1f,
                    getDaysToCreate() * OmniFacSettings.getShipAnalysisTimeMod());
        }

        @Override
        public int getDaysToCreate()
        {
            return (int) Math.max(((fp * size) / 2f) * OmniFacSettings.getShipProductionTimeMod(),
                    size * 3f);
        }

        @Override
        public int getLastUpdate()
        {
            return lastUpdate;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public int getTotal()
        {
            int total = 0;

            for (FleetMemberAPI tmp : getCargo().getMothballedShips().getMembersListCopy())
            {
                if (id.equals(parseHullName(tmp)))
                {
                    total++;
                }
            }

            return total;
        }

        @Override
        public int getLimit()
        {
            switch (size)
            {
                case 1:
                    return OmniFacSettings.getMaxHullsPerFighter();
                case 2:
                    return OmniFacSettings.getMaxHullsPerFrigate();
                case 3:
                    return OmniFacSettings.getMaxHullsPerDestroyer();
                case 4:
                    return OmniFacSettings.getMaxHullsPerCruiser();
                case 5:
                    return OmniFacSettings.getMaxHullsPerCapital();
                default:
                    return 0;
            }
        }

        @Override
        public boolean hasWarnedLimit()
        {
            return warnedLimit;
        }

        @Override
        public void setWarnedLimit(boolean hasWarned)
        {
            warnedLimit = hasWarned;
        }

        @Override
        public boolean isAnalyzed()
        {
            return isAnalyzed;
        }

        @Override
        public void setAnalyzed(boolean isAnalyzed)
        {
            this.isAnalyzed = isAnalyzed;
            lastUpdate = numHeartbeats;
        }

        @Override
        public boolean create()
        {
            lastUpdate = numHeartbeats;

            if (getTotal() >= getLimit())
            {
                return false;
            }

            warnedLimit = false;
            getCargo().addMothballedShip(type, id
                    + (type.equals(FleetMemberType.FIGHTER_WING) ? "" : "_Hull"), null);
            return true;
        }
    }

    private class WeaponData implements BlueprintData
    {
        String id, displayName;
        float size;
        int lastUpdate, stackSize;
        boolean warnedLimit = false, isAnalyzed = false;

        WeaponData(CargoStackAPI stack)
        {
            id = (String) stack.getData();
            displayName = stack.getDisplayName();
            size = stack.getCargoSpacePerUnit();
            //2 40
            //4 20
            //8 10
            stackSize = (int) (80 / stack.getCargoSpacePerUnit());
            lastUpdate = numHeartbeats;
        }

        @Override
        public int getDaysToAnalyze()
        {
            return (int) Math.max(1f,
                    getDaysToCreate() * OmniFacSettings.getWeaponAnalysisTimeMod());
        }

        @Override
        public int getDaysToCreate()
        {
            return (int) Math.max(size * OmniFacSettings.getWeaponProductionTimeMod(), 1f);
        }

        @Override
        public int getLastUpdate()
        {
            return lastUpdate;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public String getId()
        {
            return id;
        }

        @Override
        public int getTotal()
        {
            return getCargo().getNumWeapons(id);
        }

        @Override
        public int getLimit()
        {
            return (int) (stackSize * OmniFacSettings.getMaxStacksPerWeapon());
        }

        @Override
        public boolean hasWarnedLimit()
        {
            return warnedLimit;
        }

        @Override
        public void setWarnedLimit(boolean hasWarned)
        {
            warnedLimit = hasWarned;
        }

        @Override
        public boolean isAnalyzed()
        {
            return isAnalyzed;
        }

        @Override
        public void setAnalyzed(boolean isAnalyzed)
        {
            this.isAnalyzed = isAnalyzed;
            lastUpdate = numHeartbeats;
        }

        @Override
        public boolean create()
        {
            lastUpdate = numHeartbeats;

            if (getTotal() >= getLimit())
            {
                return false;
            }

            warnedLimit = false;
            getCargo().addWeapons(id, 1);
            return true;
        }
    }
    //</editor-fold>
}
