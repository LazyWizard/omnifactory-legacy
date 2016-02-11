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

public class OmniFacModPlugin extends BaseModPlugin
{
    private static SectorEntityToken createOmnifactory()
    {
        SectorAPI sector = Global.getSector();

        if (!OmniFacSettings.shouldHaveRandomStartingLocation())
        {
            StarSystemAPI corvus = sector.getStarSystem("corvus");
            if (corvus != null)
            {
                Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                        "Omnifactory starting location: orbiting Somnus in Corvus");

                // By default the Omnifactory orbits the planet Somnus in the Corvus system
                return corvus.addOrbitalStation(Constants.STATION_ID,
                        corvus.getEntityById("corvus_IV"), 315f,
                        300f, 50f, Constants.STATION_NAME, Constants.STATION_FACTION);
            }

            Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                    "Corvus not found, using random Omnifactory location");
        }

        // Find a random planet or star that doesn't already have a station
        List<StarSystemAPI> systems = new ArrayList<>(sector.getStarSystems());
        Collections.shuffle(systems);
        for (StarSystemAPI system : systems)
        {
            CollectionFilter planetFilter = new EmptyOrbitFilter(system);
            List<PlanetAPI> planets = CollectionUtils.filter(
                    system.getPlanets(), planetFilter);
            if (!planets.isEmpty())
            {
                Collections.shuffle(planets);
                PlanetAPI toOrbit = planets.get(0);

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
                Global.getLogger(OmniFacModPlugin.class).log(Level.INFO,
                        "Omnifactory starting location: orbiting "
                        + system.getBaseName() + "'s star");
                return system.addOrbitalStation(Constants.STATION_ID,
                        system.getStar(), (float) (Math.random() * 360f),
                        (system.getStar().getRadius() * 1.5f) + 50f, 50f,
                        Constants.STATION_NAME, Constants.STATION_FACTION);
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

    private static class EmptyOrbitFilter implements CollectionFilter<SectorEntityToken>
    {
        final Set<SectorEntityToken> blocked;

        private EmptyOrbitFilter(StarSystemAPI system)
        {
            blocked = new HashSet<>();
            for (SectorEntityToken station : system.getEntitiesWithTag(Tags.STATION))
            {
                OrbitAPI orbit = station.getOrbit();
                if (orbit != null && orbit.getFocus() != null)
                {
                    blocked.add(station.getOrbit().getFocus());
                }
            }
        }

        @Override
        public boolean accept(SectorEntityToken token)
        {
            return !blocked.contains(token);
        }
    }
}
