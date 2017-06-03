package org.lazywizard.omnifac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import org.apache.log4j.Level;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollectionUtils.CollectionFilter;
import org.lazywizard.lazylib.MathUtils;

public class OmniFacModPlugin extends BaseModPlugin
{
    private static SectorEntityToken createOmnifactory()
    {
        SectorAPI sector = Global.getSector();

        if (!OmniFacSettings.shouldHaveRandomStartingLocation())
        {
            StarSystemAPI system = sector.getStarSystem(
                    OmniFacSettings.getFixedStartingLocationSystem());
            if (system != null)
            {
                SectorEntityToken anchor = system.getEntityById(
                        OmniFacSettings.getFixedStartingLocationAnchorEntity());
                if (anchor != null)
                {
                    Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                            "Omnifactory starting location: orbiting "
                            + anchor.getName() + " in " + system.getBaseName());

                    return system.addOrbitalStation(Constants.STATION_ID,
                            anchor, 315f, (anchor.getRadius() * 1.1f) + 100f, 50f,
                            Constants.STATION_NAME, Constants.STATION_FACTION);
                }
            }

            Global.getLogger(OmniFacModPlugin.class).log(Level.ERROR,
                    "Anchor not found, using random Omnifactory location");
        }

        // Find a random planet or star that doesn't already have a station
        final List<StarSystemAPI> systems = new ArrayList<>(sector.getStarSystems());
        Collections.shuffle(systems);
        for (StarSystemAPI system : systems)
        {
            final CollectionFilter planetFilter = new ValidOrbitFilter(system);
            final List<PlanetAPI> planets = CollectionUtils.filter(
                    system.getPlanets(), planetFilter);
            if (!planets.isEmpty())
            {
                final PlanetAPI toOrbit = planets.get(
                        MathUtils.getRandom().nextInt(planets.size()));

                Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                        "Omnifactory starting location: orbiting "
                        + toOrbit.getName() + " in " + system.getBaseName());

                return system.addOrbitalStation(Constants.STATION_ID, toOrbit,
                        (float) (Math.random() * 360f), toOrbit.getRadius() + 150f,
                        50f, Constants.STATION_NAME, Constants.STATION_FACTION);
            }
        }

        // No empty planets found? Orbit a random star
        Collections.shuffle(systems);
        for (StarSystemAPI system : systems)
        {
            if (system.getStar() != null)
            {
                final PlanetAPI star = system.getStar();
                Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                        "Omnifactory starting location: orbiting "
                        + system.getBaseName() + "'s star (" + star.getName() + ")");
                return system.addOrbitalStation(
                        Constants.STATION_ID, star, (float) (Math.random() * 360f),
                        (star.getRadius() * 1.5f) + 50f + star.getSpec().getCoronaSize(),
                        50f, Constants.STATION_NAME, Constants.STATION_FACTION);
            }
        }

        // In the unlikely situation where every planet's orbit is occupied
        // and all stars in the sector have somehow vanished...
        throw new RuntimeException("Could not find a valid Omnifactory location!");
    }

    @Override
    public void onApplicationLoad() throws Exception
    {
        OmniFacSettings.reloadSettings();
    }

    @Override
    public void onEnabled(boolean wasEnabledBefore)
    {
        if (!wasEnabledBefore)
        {
            // Support for multiple factories
            for (int x = 1; x <= OmniFacSettings.getNumberOfFactories(); x++)
            {
                // Set up the station and its market
                SectorEntityToken factory = createOmnifactory();
                String id = Constants.STATION_ID + "-" + x;
                MarketAPI market = Global.getFactory().createMarket(id, Constants.STATION_NAME, 0);
                SharedData.getData().getMarketsWithoutPatrolSpawn().add(id);
                SharedData.getData().getMarketsWithoutTradeFleetSpawn().add(id);
                market.setPrimaryEntity(factory);
                market.setFactionId(Constants.STATION_FACTION);
                market.addCondition(Conditions.ABANDONED_STATION);
                factory.setMarket(market);
                Global.getSector().getEconomy().addMarket(market);

                // Add Omnifactory submarket to station's market
                OmniFac.initOmnifactory(factory);
            }
        }
    }

    private static class ValidOrbitFilter implements CollectionFilter<PlanetAPI>
    {
        final Set<PlanetAPI> blocked;

        private ValidOrbitFilter(StarSystemAPI system)
        {
            blocked = new HashSet<>();
            for (SectorEntityToken station : system.getEntitiesWithTag(Tags.STATION))
            {
                final OrbitAPI orbit = station.getOrbit();
                if (orbit != null && orbit.getFocus() instanceof PlanetAPI)
                {
                    blocked.add((PlanetAPI) station.getOrbit().getFocus());
                }
            }
        }

        @Override
        public boolean accept(PlanetAPI planet)
        {
            return !planet.isStar() && !blocked.contains(planet);
        }
    }
}
